package com.github.ljarka.picfort.local

import android.content.ContentUris
import android.content.Context
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

// Photos and videos taken in the month. The Files table holds both, with shared ids.
private val MONTH_SELECTION =
    "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
        "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})" +
        " AND ${MediaStore.MediaColumns.DATE_TAKEN} >= ? AND ${MediaStore.MediaColumns.DATE_TAKEN} < ?"

fun localImageUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
fun localVideoUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

private fun monthSelectionArgs(month: YearMonth, zone: ZoneId): Array<String> {
    val range = monthRangeMillis(month, zone)
    return arrayOf(range.first.toString(), (range.last + 1).toString())
}

/** Reads the phone's photos and videos. Returns nothing if media access isn't granted. */
class LocalImagesProvider(
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun getImagesForMonth(context: Context, month: YearMonth): List<ImageData> {
        if (!hasMediaAccess(context)) return emptyList()
        return withContext(Dispatchers.IO) {
            val projection = buildList {
                add(MediaStore.MediaColumns._ID)
                add(MediaStore.MediaColumns.DISPLAY_NAME)
                add(MediaStore.MediaColumns.DATE_TAKEN)
                add(MediaStore.MediaColumns.WIDTH)
                add(MediaStore.MediaColumns.HEIGHT)
                add(MediaStore.Files.FileColumns.MEDIA_TYPE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.MediaColumns.DURATION)
            }.toTypedArray()
            val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC"

            val items = mutableListOf<ImageData>()
            context.contentResolver.query(
                FILES_URI, projection, MONTH_SELECTION, monthSelectionArgs(month, zone), sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val typeColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val durationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val isVideo = cursor.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    items.add(
                        ImageData(
                            id = id,
                            uri = if (isVideo) localVideoUri(id) else localImageUri(id),
                            fileName = if (nameColumn != -1) cursor.getString(nameColumn) ?: "" else "",
                            dateTaken = if (dateColumn != -1) cursor.getLong(dateColumn) else 0,
                            width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0,
                            height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0,
                            isVideo = isVideo,
                            durationMs = if (durationColumn != -1 && !cursor.isNull(durationColumn)) {
                                cursor.getLong(durationColumn)
                            } else {
                                null
                            },
                        )
                    )
                }
            }
            items
        }
    }

    suspend fun getImagesCountForMonth(context: Context, month: YearMonth): Int {
        if (!hasMediaAccess(context)) return 0
        return withContext(Dispatchers.IO) {
            val projection = arrayOf(MediaStore.MediaColumns._ID)
            context.contentResolver.query(
                FILES_URI, projection, MONTH_SELECTION, monthSelectionArgs(month, zone), null
            )?.use { it.count } ?: 0
        }
    }
}
