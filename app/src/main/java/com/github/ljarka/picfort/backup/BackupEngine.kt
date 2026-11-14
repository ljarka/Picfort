package com.github.ljarka.picfort.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.ljarka.picfort.core.database.BackupDao
import com.github.ljarka.picfort.core.database.LocalBackupEntity
import com.github.ljarka.picfort.core.network.AccessTokenStore
import com.github.ljarka.picfort.core.network.ServerUrlStore
import com.github.ljarka.picfort.local.hasMediaAccess
import com.github.ljarka.picfort.local.localImageUri
import com.github.ljarka.picfort.local.localVideoUri
import com.github.ljarka.picfort.ui.timeline.TimelineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Provider
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import retrofit2.HttpException

private const val TAG = "BackupEngine"
private const val BATCH = 50

data class BackupProgress(val done: Int, val total: Int)

sealed interface BackupOutcome {
    data class Done(
        val uploaded: Int,
        val alreadyOnServer: Int,
        val rejected: Int,
        /** Too large for the public address; they go through the home one (T-064). */
        val waitingForHome: Int = 0,
        val viaHomeNetwork: Boolean = false,
    ) : BackupOutcome
    data object NoMediaAccess : BackupOutcome
    data object NotLoggedIn : BackupOutcome

    /** The account's storage quota on the server is full; nothing more can be uploaded. */
    data class QuotaExceeded(val uploaded: Int) : BackupOutcome
}

/** Immich answers HTTP 400 "Quota has been exceeded!" when the account's quota is full. */
private class QuotaExceededException : Exception()

/** True when an Immich error response says the account's storage quota is full. */
fun isQuotaExceeded(errorBody: String?): Boolean = errorBody?.contains("quota", ignoreCase = true) == true

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
    private val queue: BackupQueue,
    private val route: BackupRoute,
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

        val todo = media.filter { m ->
            val row = known[m.id]
            !isUnchanged(m, row) ||
                (row?.remoteId == null && (row?.rejected == null || row.rejected == NEEDS_HOME_NETWORK))
        }
        val alreadyBackedUp = media.size - todo.size
        onProgress(BackupProgress(alreadyBackedUp, media.size))
        if (todo.isEmpty()) return BackupOutcome.Done(0, 0, 0)

        queue.start(todo.map { QueuedMedia(it.id, it.isVideo) })
        try {
            return uploadAll(todo, known, alreadyBackedUp, media.size, onProgress)
        } finally {
            queue.clear()
        }
    }

    /** Hash → ask Immich which it has → upload the rest, batch by batch. */
    private suspend fun uploadAll(
        todo: List<LocalMedia>,
        known: Map<Long, LocalBackupEntity>,
        alreadyBackedUp: Int,
        total: Int,
        onProgress: suspend (BackupProgress) -> Unit,
    ): BackupOutcome {
        val home = route.reachableHome()
        val api = home?.let(route::service) ?: service.get()
        val onHome = home != null
        val homeSet = settings.homeUrl.isNotBlank()
        val serverMajor = api.version().major
        var uploaded = 0
        var duplicates = 0
        var rejected = 0
        var waitingForHome = 0
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
                    BulkUploadCheckRequest(rows.map { BulkUploadCheckItem(it.localId.toString(), it.checksum) }),
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
                        }

                        result.action == "reject" -> {
                            dao.setRejected(localId, result.reason ?: "rejected")
                            rejected++
                        }

                        holdForHomeNetwork(m.size, row.rejected, onHome, homeSet) -> {
                            if (row.rejected != NEEDS_HOME_NETWORK) dao.setRejected(localId, NEEDS_HOME_NETWORK)
                            waitingForHome++
                        }

                        else -> when (val r = upload(api, m, row.checksum, serverMajor, onHome)) {
                            is UploadResult.Uploaded -> {
                                dao.setRemoteId(localId, r.remoteId)
                                uploaded++
                            }

                            UploadResult.NeedsHomeNetwork -> waitingForHome++

                            UploadResult.Refused -> rejected++
                        }
                    }
                    queue.finished(listOf(localId))
                }
                // Found in Immich, refused, or deleted since the scan: out of the queue too.
                queue.finished(batch.map { it.id })
                onProgress(BackupProgress(alreadyBackedUp + uploaded + duplicates + rejected + waitingForHome, total))
            }
        } catch (e: QuotaExceededException) {
            // Stop: every further upload would fail too. Files stay pending, not rejected,
            // so they go up once the quota is raised.
            quotaExceeded = true
        }

        // Hide the phone copies that are now in Immich.
        try {
            timelineRepository.get().refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Timeline reload after backup failed", e)
        }
        if (quotaExceeded) return BackupOutcome.QuotaExceeded(uploaded)
        return BackupOutcome.Done(uploaded, duplicates, rejected, waitingForHome, onHome)
    }

    private sealed interface UploadResult {
        data class Uploaded(val remoteId: String) : UploadResult
        data object NeedsHomeNetwork : UploadResult

        /** Refused for good (format, size on the server itself), or deleted since the scan. */
        data object Refused : UploadResult
    }

    /** Network errors propagate. */
    private suspend fun upload(
        api: BackupService,
        m: LocalMedia,
        checksum: String,
        serverMajor: Int,
        onHome: Boolean,
    ): UploadResult {
        val fields = uploadFields(m, serverMajor, settings.deviceId)
            .mapValues { (_, v) -> v.toRequestBody(MultipartBody.FORM) }
        queue.uploading(m.id)
        val body = ContentUriRequestBody(context.contentResolver, uriOf(m), m.mimeType.toMediaTypeOrNull(), m.size) {
            queue.progress(m.id, it)
        }
        val part = MultipartBody.Part.createFormData("assetData", m.fileName, body)
        return try {
            UploadResult.Uploaded(api.upload(checksum, fields, part).id)
        } catch (e: HttpException) {
            if (isQuotaExceeded(e.response()?.errorBody()?.string())) throw QuotaExceededException()
            when {
                // A proxy in front of Immich (Cloudflare: 100 MB) refused the size: retry at home.
                e.code() == 413 && !onHome -> {
                    Log.w(TAG, "${m.fileName} too large for the public address, waits for the home network")
                    dao.setRejected(m.id, NEEDS_HOME_NETWORK)
                    UploadResult.NeedsHomeNetwork
                }

                e.code() in listOf(400, 413, 415, 422) -> {
                    Log.w(TAG, "Immich refused ${m.fileName}: HTTP ${e.code()}")
                    dao.setRejected(m.id, if (e.code() == 413) TOO_LARGE_FOR_SERVER else "HTTP ${e.code()}")
                    UploadResult.Refused
                }

                else -> throw e
            }
        } catch (e: FileNotFoundException) {
            UploadResult.Refused // deleted since the scan
        }
    }

    private fun uriOf(m: LocalMedia): Uri = if (m.isVideo) localVideoUri(m.id) else localImageUri(m.id)
}

private fun isUnchanged(m: LocalMedia, row: LocalBackupEntity?) =
    row != null && row.dateModified == m.dateModified && row.size == m.size

/**
 * Streams a content:// file into the request without loading it into memory, reporting the share
 * sent (0..1, in whole percents) when the size is known.
 */
private class ContentUriRequestBody(
    private val resolver: ContentResolver,
    private val uri: Uri,
    private val type: MediaType?,
    private val size: Long,
    private val onProgress: (Float) -> Unit = {},
) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = if (size > 0) size else -1
    override fun writeTo(sink: BufferedSink) {
        val input = resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
        input.source().use { source ->
            val buffer = Buffer()
            var sent = 0L
            var percent = -1
            while (true) {
                val read = source.read(buffer, 64 * 1024L)
                if (read < 0) break
                sink.write(buffer, read)
                sent += read
                if (size > 0) {
                    val now = (sent * 100 / size).toInt()
                    if (now != percent) {
                        percent = now
                        onProgress(now / 100f)
                    }
                }
            }
        }
    }
}
