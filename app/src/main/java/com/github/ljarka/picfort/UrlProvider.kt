package com.github.ljarka.picfort

import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.local.localImageUri
import com.github.ljarka.picfort.local.localVideoUri
import com.github.ljarka.picfort.ui.server.ServerUrlStore
import javax.inject.Inject

class UrlProvider @Inject constructor(
    private val serverUrlStore: ServerUrlStore,
) {

    fun getPreview(assetId: String, type: AssetType, isVideo: Boolean = false): String {
        return when (type) {
            AssetType.LOCAL -> localUri(assetId, isVideo)
            AssetType.REMOTE -> "${serverUrlStore.serverUrl}/api/assets/$assetId/thumbnail?size=preview"
        }
    }

    fun getThumbnail(assetId: String, type: AssetType, isVideo: Boolean = false): String {
        return when (type) {
            AssetType.LOCAL -> localUri(assetId, isVideo)
            AssetType.REMOTE -> "${serverUrlStore.serverUrl}/api/assets/$assetId/thumbnail"
        }
    }

    /** Playable stream: Immich transcodes to a phone-friendly format when needed. */
    fun getVideo(assetId: String, type: AssetType): String = when (type) {
        AssetType.LOCAL -> localUri(assetId, isVideo = true)
        AssetType.REMOTE -> "${serverUrlStore.serverUrl}/api/assets/$assetId/video/playback"
    }

    private fun localUri(id: String, isVideo: Boolean) =
        (if (isVideo) localVideoUri(id.toLong()) else localImageUri(id.toLong())).toString()
}