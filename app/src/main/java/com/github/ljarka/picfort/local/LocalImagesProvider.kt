package com.github.ljarka.picfort.local

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId

data class ImageData(
    val id: Long,
    val uri: Uri,
    val path: String,
    val fileName: String,
    val dateTaken: Long,
    val width: Int?,
    val height: Int?,
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

private const val MONTH_SELECTION =
    "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} < ?"

private fun monthSelectionArgs(month: YearMonth, zone: ZoneId): Array<String> {
    val range = monthRangeMillis(month, zone)
    return arrayOf(range.first.toString(), (range.last + 1).toString())
}

class LocalImagesProvider(
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    suspend fun getImagesForMonth(context: Context, month: YearMonth): List<ImageData> {
        return withContext(Dispatchers.IO) {

            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,  // Path (deprecated in API 29+)
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
            )
            val selection = MONTH_SELECTION
            val selectionArgs = monthSelectionArgs(month, zone)
            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

            val images = mutableListOf<ImageData>()
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor ->
                    val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                    val dataColumn =
                        cursor.getColumnIndex(MediaStore.Images.Media.DATA) // Deprecated but works for older APIs
                    val nameColumn = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    val widthColumn = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                    val heightColumn = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val contentUri = Uri.withAppendedPath(uri, id.toString())
                        val filePath = if (dataColumn != -1) cursor.getString(dataColumn) else ""
                        val fileName =
                            if (nameColumn != -1) cursor.getString(nameColumn) else "Unknown"
                        val dateTaken = if (dateColumn != -1) cursor.getLong(dateColumn) else 0
                        val width = if (widthColumn != -1) cursor.getInt(widthColumn) else 0
                        val height = if (heightColumn != -1) cursor.getInt(heightColumn) else 0

                        images.add(
                            ImageData(
                                id,
                                contentUri,
                                filePath,
                                fileName,
                                dateTaken,
                                width,
                                height
                            )
                        )
                    }
                }
            images
        }
    }

    suspend fun getImagesCountForMonth(context: Context, month: YearMonth): Int {
        return withContext(Dispatchers.IO) {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selectionArgs = monthSelectionArgs(month, zone)

            context.contentResolver.query(uri, projection, MONTH_SELECTION, selectionArgs, null)
                ?.use { cursor ->
                    return@withContext cursor.count
                } ?: 0
        }
    }
}