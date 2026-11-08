package com.github.ljarka.picfort.assets

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.local.localImageUri
import com.github.ljarka.picfort.local.localVideoUri
import com.github.ljarka.picfort.ui.timeline.formatDuration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Provider

class AssetDetailsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetsService: Provider<AssetsService>,
) {
    suspend fun load(id: String, type: AssetType, isVideo: Boolean): AssetDetails = when (type) {
        AssetType.REMOTE -> assetsService.get().getAssetInfo(id).toDetails()
        AssetType.LOCAL -> loadLocal(id, isVideo)
    }

    private suspend fun loadLocal(id: String, isVideo: Boolean): AssetDetails = withContext(Dispatchers.IO) {
        val uri = if (isVideo) localVideoUri(id.toLong()) else localImageUri(id.toLong())
        val projection = buildList {
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                add(MediaStore.MediaColumns.DURATION)
            }
        }.toTypedArray()

        val rows = mutableListOf<DetailRow>()
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use
            fun str(col: String) = c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getString)
            fun long(col: String) = c.getColumnIndex(col).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong)

            long(MediaStore.MediaColumns.DATE_TAKEN)?.takeIf { it > 0 }?.let {
                formatTakenAt(Instant.ofEpochMilli(it).toString(), ZoneId.systemDefault())
                    ?.let { v -> rows += DetailRow("Taken", v) }
            }
            str(MediaStore.MediaColumns.DISPLAY_NAME)?.let { rows += DetailRow("File", it) }
            val width = long(MediaStore.MediaColumns.WIDTH)?.toInt() ?: 0
            val height = long(MediaStore.MediaColumns.HEIGHT)?.toInt() ?: 0
            listOfNotNull(
                if (width > 0 && height > 0) formatResolution(width, height) else null,
                long(MediaStore.MediaColumns.SIZE)?.let(::formatFileSize),
            ).joinToString(" · ").ifBlank { null }?.let { rows += DetailRow("Size", it) }
            long(MediaStore.MediaColumns.DURATION)?.takeIf { it > 0 }
                ?.let { rows += DetailRow("Length", formatDuration(it)) }
            str(MediaStore.MediaColumns.RELATIVE_PATH)?.let { rows += DetailRow("Folder", it.trimEnd('/')) }
        }
        rows += DetailRow("Backup", "Only on this phone")
        AssetDetails(rows)
    }
}
