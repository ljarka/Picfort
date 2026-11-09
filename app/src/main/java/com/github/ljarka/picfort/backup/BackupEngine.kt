package com.github.ljarka.picfort.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.ljarka.picfort.db.BackupDao
import com.github.ljarka.picfort.db.LocalBackupEntity
import com.github.ljarka.picfort.local.hasMediaAccess
import com.github.ljarka.picfort.local.localImageUri
import com.github.ljarka.picfort.local.localVideoUri
import com.github.ljarka.picfort.ui.login.AccessTokenStore
import com.github.ljarka.picfort.ui.server.ServerUrlStore
import com.github.ljarka.picfort.ui.timeline.TimelineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import retrofit2.HttpException
import java.io.FileNotFoundException
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.coroutineContext

private const val TAG = "BackupEngine"
private const val BATCH = 50

data class BackupProgress(val done: Int, val total: Int)

sealed interface BackupOutcome {
    data class Done(val uploaded: Int, val alreadyOnServer: Int, val rejected: Int) : BackupOutcome
    data object NoMediaAccess : BackupOutcome
    data object NotLoggedIn : BackupOutcome

    /** The account's storage quota on the server is full; nothing more can be uploaded. */
    data class QuotaExceeded(val uploaded: Int) : BackupOutcome
}

/** Immich answers HTTP 400 "Quota has been exceeded!" when the account's quota is full. */
private class QuotaExceededException : Exception()

/** True when an Immich error response says the account's storage quota is full. */
fun isQuotaExceeded(errorBody: String?): Boolean =
    errorBody?.contains("quota", ignoreCase = true) == true

/**
 * Backs up the phone's photos and videos to Immich:
 * scan → hash new/changed files (cached) → ask Immich which it already has → upload the rest.
 * Work happens in batches, newest first, so progress is saved as it goes.
 */
class BackupEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: LocalMediaScanner,
    private val dao: BackupDao,
    private val service: Provider<BackupService>,
    private val settings: BackupSettingsStore,
    private val serverUrlStore: ServerUrlStore,
    private val accessTokenStore: AccessTokenStore,
    private val timelineRepository: Provider<TimelineRepository>,
) {
    suspend fun run(onProgress: suspend (BackupProgress) -> Unit = {}): BackupOutcome {
        if (!accessTokenStore.hasData() || !serverUrlStore.hasData()) return BackupOutcome.NotLoggedIn
        if (!hasMediaAccess(context)) return BackupOutcome.NoMediaAccess

        // Links to Immich assets only hold for the server and account they came from.
        val owner = "${serverUrlStore.serverUrl}|${accessTokenStore.userId}"
        if (settings.mappingsOwner != owner) {
            dao.resetRemote()
            settings.mappingsOwner = owner
        }

        val media = scanner.scanAll()
        val known = dao.getAll().associateBy { it.localId }
        val present = media.mapTo(HashSet()) { it.id }
        known.keys.filter { it !in present }.chunked(500).forEach { dao.delete(it) }

        fun isUnchanged(m: LocalMedia, row: LocalBackupEntity?) =
            row != null && row.dateModified == m.dateModified && row.size == m.size
        val todo = media.filter { m ->
            val row = known[m.id]
            !isUnchanged(m, row) || (row!!.remoteId == null && row.rejected == null)
        }
        val alreadyBackedUp = media.size - todo.size
        onProgress(BackupProgress(alreadyBackedUp, media.size))
        if (todo.isEmpty()) return BackupOutcome.Done(0, 0, 0)

        val api = service.get()
        val serverMajor = api.version().major
        var uploaded = 0
        var duplicates = 0
        var rejected = 0
        val touchedMonths = mutableSetOf<Long>()
        var quotaExceeded = false

        try {
            for (batch in todo.chunked(BATCH)) {
                coroutineContext.ensureActive()

                // 1. Checksums for new or changed files.
                val rows = withContext(Dispatchers.IO) {
                    batch.mapNotNull { m ->
                        val row = known[m.id]
                        if (isUnchanged(m, row)) return@mapNotNull row
                        val checksum = try {
                            context.contentResolver.openInputStream(uriOf(m))?.use(::sha1Hex)
                        } catch (e: FileNotFoundException) {
                            null // deleted since the scan
                        } ?: return@mapNotNull null
                        LocalBackupEntity(m.id, m.dateModified, m.size, m.dateTaken, checksum)
                    }
                }
                dao.upsert(rows)
                val mediaById = batch.associateBy { it.id }

                // 2. Which ones does Immich already have?
                val results = api.bulkUploadCheck(
                    BulkUploadCheckRequest(rows.map { BulkUploadCheckItem(it.localId.toString(), it.checksum) })
                ).results

                // 3. Upload the rest.
                for (result in results) {
                    coroutineContext.ensureActive()
                    val localId = result.id.toLongOrNull() ?: continue
                    val m = mediaById[localId] ?: continue
                    val row = rows.first { it.localId == localId }
                    when {
                        result.action == "reject" && result.assetId != null -> {
                            dao.setRemoteId(localId, result.assetId)
                            duplicates++
                            touchedMonths += monthKey(m.dateTaken)
                        }
                        result.action == "reject" -> {
                            dao.setRejected(localId, result.reason ?: "rejected")
                            rejected++
                        }
                        else -> when (val remoteId = upload(api, m, row.checksum, serverMajor)) {
                            null -> rejected++
                            else -> {
                                dao.setRemoteId(localId, remoteId)
                                uploaded++
                                touchedMonths += monthKey(m.dateTaken)
                            }
                        }
                    }
                }
                onProgress(BackupProgress(alreadyBackedUp + uploaded + duplicates + rejected, media.size))
            }
        } catch (e: QuotaExceededException) {
            // Stop: every further upload would fail too. Files stay pending, not rejected,
            // so they go up once the quota is raised.
            quotaExceeded = true
        }

        // Hide the phone copies that are now in Immich.
        try {
            timelineRepository.get().reloadBuckets(touchedMonths)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Timeline reload after backup failed", e)
        }
        if (quotaExceeded) return BackupOutcome.QuotaExceeded(uploaded)
        return BackupOutcome.Done(uploaded, duplicates, rejected)
    }

    /** Returns the Immich asset id, or null if the server refused this file. Network errors propagate. */
    private suspend fun upload(api: BackupService, m: LocalMedia, checksum: String, serverMajor: Int): String? {
        val fields = uploadFields(m, serverMajor, settings.deviceId)
            .mapValues { (_, v) -> v.toRequestBody(MultipartBody.FORM) }
        val body = ContentUriRequestBody(context.contentResolver, uriOf(m), m.mimeType.toMediaTypeOrNull(), m.size)
        val part = MultipartBody.Part.createFormData("assetData", m.fileName, body)
        return try {
            api.upload(checksum, fields, part).id
        } catch (e: HttpException) {
            if (isQuotaExceeded(e.response()?.errorBody()?.string())) throw QuotaExceededException()
            if (e.code() in listOf(400, 413, 415, 422)) {
                Log.w(TAG, "Immich refused ${m.fileName}: HTTP ${e.code()}")
                dao.setRejected(m.id, "HTTP ${e.code()}")
                null
            } else {
                throw e
            }
        } catch (e: FileNotFoundException) {
            null // deleted since the scan
        }
    }

    private fun uriOf(m: LocalMedia): Uri = if (m.isVideo) localVideoUri(m.id) else localImageUri(m.id)

    /** Timeline bucket key: midnight UTC on the 1st of the month the photo was taken (device zone). */
    private fun monthKey(dateTaken: Long): Long =
        YearMonth.from(Instant.ofEpochMilli(dateTaken).atZone(ZoneId.systemDefault()))
            .atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

/** Streams a content:// file into the request without loading it into memory. */
private class ContentUriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val type: MediaType?,
    private val size: Long,
) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = if (size > 0) size else -1
    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        input.source().use { sink.writeAll(it) }
    }
}
