package com.github.ljarka.picfort.backup

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lists every photo and video on the phone, for backup. */
class LocalMediaScanner @Inject constructor(@ApplicationContext private val context: Context) {
    /** Number of photos and videos on the phone. */
    suspend fun count(): Int = withContext(Dispatchers.IO) {
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
            null,
            null,
        )?.use { it.count } ?: 0
    }

    suspend fun scanAll(): List<LocalMedia> = withContext(Dispatchers.IO) {
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
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (" +
            "${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

        val result = mutableListOf<LocalMedia>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
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
