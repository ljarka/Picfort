package com.github.ljarka.picfort.assets

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.network.ServerUrlStore
import com.github.ljarka.picfort.local.localImageUri
import com.github.ljarka.picfort.local.localVideoUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** An asset picked in the timeline, with the month bucket it belongs to. */
data class SelectedAsset(val id: String, val type: AssetType, val bucket: Long, val isVideo: Boolean = false)

/**
 * Share, download and delete for both Immich (REMOTE) and on-phone (LOCAL) assets.
 * Long-running calls are main-safe.
 */
@Singleton
class AssetActions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val assetsService: Provider<AssetsService>,
    private val serverUrlStore: ServerUrlStore,
) {
    private val shareDir get() = File(context.cacheDir, "shared")

    // ------------------------------------------------------------------ share

    /** Builds a share intent; Immich originals are downloaded to the cache first. */
    suspend fun shareIntent(assets: List<SelectedAsset>): Intent = withContext(Dispatchers.IO) {
        shareDir.deleteRecursively() // previous shares are no longer needed
        shareDir.mkdirs()

        val items = assets.map { asset ->
            when (asset.type) {
                AssetType.LOCAL -> {
                    val uri = localUri(asset)
                    uri to (context.contentResolver.getType(uri) ?: if (asset.isVideo) "video/*" else "image/*")
                }

                AssetType.REMOTE -> {
                    val info = assetsService.get().getAssetInfo(asset.id)
                    val file = File(shareDir, safeFileName(info))
                    file.outputStream().use { download(asset.id, it) }
                    FileProvider.getUriForFile(context, "${context.packageName}.files", file) to
                        (info.originalMimeType ?: guessMime(info))
                }
            }
        }

        val uris = items.map { it.first }
        val mimes = items.map { it.second }.distinct()
        val mime = when {
            mimes.size == 1 -> mimes.single()
            mimes.all { it.startsWith("image/") } -> "image/*"
            mimes.all { it.startsWith("video/") } -> "video/*"
            else -> "*/*"
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.setType(mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply {
                clipData = ClipData.newRawUri(null, uris.first()).apply {
                    uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
            }
        Intent.createChooser(intent, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // --------------------------------------------------------------- download

    /** Saves Immich originals to Pictures/Picfort (or Movies/Picfort). Returns how many were saved. */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun downloadToPhone(assets: List<SelectedAsset>): Int = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        assets.filter { it.type == AssetType.REMOTE }.count { asset ->
            val info = assetsService.get().getAssetInfo(asset.id)
            val mime = info.originalMimeType ?: guessMime(info)
            val isVideo = info.type == "VIDEO" || mime.startsWith("video/")
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName(info))
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Picfort",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create file")
            try {
                resolver.openOutputStream(uri)?.use { download(asset.id, it) }
                    ?: throw IOException("Couldn't open file for writing")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                true
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
    }

    // ----------------------------------------------------------------- delete

    /** Moves Immich assets to Immich's trash. */
    suspend fun deleteRemote(ids: List<String>) {
        if (ids.isNotEmpty()) assetsService.get().deleteAssets(AssetBulkDeleteRequest(ids))
    }

    /** System dialog asking the user to move phone photos to the phone's trash. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun localTrashRequest(assets: List<SelectedAsset>): IntentSender =
        MediaStore.createTrashRequest(context.contentResolver, assets.map(::localUri), true).intentSender

    // ---------------------------------------------------------------- helpers

    private fun localUri(asset: SelectedAsset): Uri =
        if (asset.isVideo) localVideoUri(asset.id.toLong()) else localImageUri(asset.id.toLong())

    private fun download(assetId: String, out: OutputStream) {
        val request = Request.Builder()
            .url("${serverUrlStore.serverUrl}/api/assets/$assetId/original")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download failed (HTTP ${response.code})")
            response.body.byteStream().use { it.copyTo(out) }
        }
    }

    private fun safeFileName(info: AssetInfo): String =
        info.originalFileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { info.id }

    private fun guessMime(info: AssetInfo): String = when (info.type) {
        "VIDEO" -> "video/*"
        else -> "image/*"
    }
}
