package com.github.ljarka.picfort

import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.ui.server.ServerUrlStore
import javax.inject.Inject

class UrlProvider @Inject constructor(
    private val serverUrlStore: ServerUrlStore,
) {

    fun getPreview(assetId: String, type: AssetType): String {
        return when (type) {
            AssetType.LOCAL -> "content://media/external/images/media/$assetId"
            AssetType.REMOTE -> "${serverUrlStore.serverUrl}/api/assets/$assetId/thumbnail?size=preview"
        }
    }

    fun getThumbnail(assetId: String, type: AssetType): String {
        return when (type) {
            AssetType.LOCAL -> "content://media/external/images/media/$assetId"
            AssetType.REMOTE -> "${serverUrlStore.serverUrl}/api/assets/$assetId/thumbnail"
        }
    }
}