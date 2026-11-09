package com.github.ljarka.picfort.local

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId

data class ImageData(
    val id: Long,
    val uri: Uri,
    val fileName: String,
    val dateTaken: Long,
    val width: Int?,
    val height: Int?,
    val isVideo: Boolean = false,
    val durationMs: Long? = null,
)

/**
 * [start, end) in epoch millis covering the whole calendar month in [zone].
 * Photos' DATE_TAKEN is wall-clock time where they were taken, so months are
 * bounded in the device's zone, matching how Immich buckets by local date.
 */
fun monthRangeMillis(month: YearMonth, zone: ZoneId): LongRange {
    val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return start until end
}

private val FILES_URI: Uri get() = MediaStore.Files.getContentUri("external")

fun localImageUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
fun localVideoUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

private val ALL_SELECTION =
    "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
        "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})" +
        " AND ${MediaStore.MediaColumns.DATE_TAKEN} IS NOT NULL"

/** Reads the phone's photos and videos. Returns nothing if media access isn't granted. */
class LocalImagesProvider {

    /** Every photo and video with a date taken, newest first. One query for the whole timeline. */
    suspend fun getAll(context: Context): List<ImageData> {
        if (!hasMediaAccess(context)) return emptyList()
        return withContext(Dispatchers.IO) {
            context.contentResolver.query(
                FILES_URI, PROJECTION, ALL_SELECTION, null, "${MediaStore.MediaColumns.DATE_TAKEN} DESC",
            )?.use(::readAll).orEmpty()
        }
    }

    private fun readAll(cursor: Cursor): List<ImageData> {
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val dateColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
        val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val orientationColumn = cursor.getColumnIndex(ORIENTATION)
        val typeColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val durationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)
        val items = ArrayList<ImageData>(cursor.count)
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val isVideo = cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
            val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0
            // MediaStore stores the sensor's size; a photo rotated 90° or 270° shows the other way round.
            val sideways = orientationColumn != -1 && cursor.getInt(orientationColumn).let { it == 90 || it == 270 }
            items += ImageData(
                id = id,
                uri = if (isVideo) localVideoUri(id) else localImageUri(id),
                fileName = if (nameColumn != -1) cursor.getString(nameColumn) ?: "" else "",
                dateTaken = if (dateColumn != -1) cursor.getLong(dateColumn) else 0,
                width = if (sideways) height else width,
                height = if (sideways) width else height,
                isVideo = isVideo,
                durationMs = if (durationColumn != -1 && !cursor.isNull(durationColumn)) cursor.getLong(durationColumn) else null,
            )
        }
        return items
    }
}

/** Images only; `orientation` exists in the Files table on all supported versions. */
@Suppress("DEPRECATION")
private const val ORIENTATION = MediaStore.Images.ImageColumns.ORIENTATION

private val PROJECTION: Array<String> = buildList {
    add(MediaStore.MediaColumns._ID)
    add(MediaStore.MediaColumns.DISPLAY_NAME)
    add(MediaStore.MediaColumns.DATE_TAKEN)
    add(MediaStore.MediaColumns.WIDTH)
    add(MediaStore.MediaColumns.HEIGHT)
    add(ORIENTATION)
    add(MediaStore.Files.FileColumns.MEDIA_TYPE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.DURATION)
}.toTypedArray()
