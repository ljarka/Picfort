package com.github.ljarka.picfort.ui.timeline

import com.github.ljarka.picfort.core.database.AssetType
import kotlinx.serialization.Serializable

@Serializable
data class AlbumRoute(val id: String, val name: String)

@Serializable
data class ImageDetailsRoute(
    val assetId: String,
    val assetType: AssetType,
    val imageIndex: Int,
    val source: String? = null,
)
