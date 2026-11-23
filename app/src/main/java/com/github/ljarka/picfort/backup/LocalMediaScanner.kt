package com.github.ljarka.picfort.backup

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaBucket(val id: String, val name: String, val mediaCount: Int)

/** Lists every photo and video on the phone, for backup. */
class LocalMediaScanner @Inject constructor(@ApplicationContext private val context: Context) {
    private val BUCKET_ID = MediaStore.Images.ImageColumns.BUCKET_ID
    private val BUCKET_NAME = MediaStore.Images.ImageColumns.BUCKET_DISPLAY_NAME
    private val MEDIA_SELECTION = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
        "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

    private fun bucketSelection(bucketIds: Set<String>?): Pair<String, Array<String>?> {
        if (bucketIds.isNullOrEmpty()) return MEDIA_SELECTION to null
        val placeholders = bucketIds.joinToString(",") { "?" }
        return "$MEDIA_SELECTION AND $BUCKET_ID IN ($placeholders)" to bucketIds.toTypedArray()
    }

    /** Number of photos and videos on the phone, optionally filtered to specific buckets. */
    suspend fun count(bucketIds: Set<String>? = null): Int = withContext(Dispatchers.IO) {
        val (sel, args) = bucketSelection(bucketIds)
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns._ID),
            sel, args, null,
        )?.use { it.count } ?: 0
    }

    /** All MediaStore buckets (folders) that contain photos or videos, sorted by size descending. */
    suspend fun listBuckets(): List<MediaBucket> = withContext(Dispatchers.IO) {
        val projection = arrayOf(BUCKET_ID, BUCKET_NAME)
        val counts = mutableMapOf<String, Pair<String, Int>>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"), projection, MEDIA_SELECTION, null, null,
        )?.use { c ->
            val idCol = c.getColumnIndex(BUCKET_ID)
            val nameCol = c.getColumnIndex(BUCKET_NAME)
            while (c.moveToNext()) {
                val id = c.getString(idCol) ?: continue
                val name = c.getString(nameCol) ?: id
                val prev = counts[id]
                counts[id] = name to ((prev?.second ?: 0) + 1)
            }
        }
        counts.map { (id, pair) -> MediaBucket(id, pair.first, pair.second) }
            .sortedByDescending { it.mediaCount }
    }

    suspend fun scanAll(bucketIds: Set<String>? = null): List<LocalMedia> = withContext(Dispatchers.IO) {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_MODIFIED)
            add(MediaStore.Files.FileColumns.MEDIA_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.DURATION)
                add(MediaStore.MediaColumns.IS_PENDING)
            }
        }.toTypedArray()
        val (selection, selectionArgs) = bucketSelection(bucketIds)

        val result = mutableListOf<LocalMedia>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_TAKEN} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val name = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mime = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val size = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val taken = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            val modified = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            val type = c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val duration = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
            val pending = c.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
            while (c.moveToNext()) {
                if (pending >= 0 && c.getInt(pending) == 1) continue // still being written
                val isVideo = c.getInt(type) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                result += LocalMedia(
                    id = c.getLong(id),
                    fileName = c.getString(name) ?: "file-${c.getLong(id)}",
                    mimeType = c.getString(mime) ?: if (isVideo) "video/*" else "image/*",
                    size = if (size >= 0) c.getLong(size) else 0,
                    dateTaken = if (taken >= 0) c.getLong(taken) else 0,
                    dateModified = if (modified >= 0) c.getLong(modified) * 1000 else 0, // seconds
                    isVideo = isVideo,
                    durationMs = if (duration >= 0 && !c.isNull(duration)) c.getLong(duration) else null,
                )
            }
        }
        result
    }
}
