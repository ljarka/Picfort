package com.github.ljarka.picfort.ui.timeline

import com.github.ljarka.picfort.core.database.AssetType

/** An asset as the full-screen viewer needs it. */
data class AssetUi(
    val url: String,
    val id: String,
    val type: AssetType,
    val isVideo: Boolean = false,
    val durationMs: Long? = null,
)

/** An asset's position in the whole timeline, as the full-screen viewer pages through it. */
data class AssetIndex(val index: Int, val assetId: String, val assetType: AssetType, val isVideo: Boolean = false)
