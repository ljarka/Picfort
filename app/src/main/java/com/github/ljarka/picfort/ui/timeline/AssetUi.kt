package com.github.ljarka.picfort.ui.timeline

import androidx.compose.ui.graphics.ImageBitmap
import com.github.ljarka.picfort.db.AssetType

data class AssetUi(
    val url: String,
    val span: Int,
    val id: String,
    val type: AssetType,
    val isVideo: Boolean = false,
    val durationMs: Long? = null,
    /** Blurred preview drawn under the thumbnail until it loads. */
    val placeholder: ImageBitmap? = null,
)