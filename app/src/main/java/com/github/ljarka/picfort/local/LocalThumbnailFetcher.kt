package com.github.ljarka.picfort.local

import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.request.bitmapConfig
import coil3.size.Dimension

private const val SCHEME = "picfort-thumb"

/**
 * Largest size asked of MediaStore. Up to about this size Android serves its cached
 * thumbnail; above it, it decodes the original again.
 */
private const val MAX_THUMBNAIL_PX = 512

/** Whether [localThumbnailUrl] can be used on this device (MediaStore thumbnails need Android 10). */
val hasLocalThumbnails: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

/** Model for a phone photo's or video's system thumbnail, loaded by [LocalThumbnailFetcher]. */
fun localThumbnailUrl(id: Long, isVideo: Boolean): String =
    "$SCHEME://${if (isVideo) "video" else "image"}/$id"

/**
 * Loads phone media through MediaStore's thumbnail cache instead of decoding the
 * original (a 12 MP photo, or a frame of a video) for every timeline tile.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class LocalThumbnailFetcher(
    private val uri: android.net.Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // Ask for the tile's size, not the maximum: every bitmap stays in Coil's memory cache.
        val thumbnail = options.context.contentResolver.loadThumbnail(uri, requestedSize(), null)
        // Like Coil's own decoder: a hardware bitmap lives in graphics memory, not the app's heap.
        val bitmap = if (options.bitmapConfig == Bitmap.Config.HARDWARE) {
            thumbnail.copy(Bitmap.Config.HARDWARE, false)?.also { thumbnail.recycle() } ?: thumbnail
        } else {
            thumbnail
        }
        return ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private fun requestedSize(): Size {
        fun px(d: Dimension) = ((d as? Dimension.Pixels)?.px ?: MAX_THUMBNAIL_PX).coerceIn(1, MAX_THUMBNAIL_PX)
        return Size(px(options.size.width), px(options.size.height))
    }
}

/** Creates [LocalThumbnailFetcher]s for [localThumbnailUrl] models; none before Android 10. */
class LocalThumbnailFetcherFactory : Fetcher.Factory<Uri> {
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
        if (data.scheme != SCHEME || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val id = data.path?.trim('/')?.toLongOrNull() ?: return null
        val uri = when (data.authority) {
            "image" -> localImageUri(id)
            "video" -> localVideoUri(id)
            else -> return null
        }
        return LocalThumbnailFetcher(uri, options)
    }
}
