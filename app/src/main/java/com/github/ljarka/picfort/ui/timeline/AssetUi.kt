package com.github.ljarka.picfort.ui.timeline

import com.github.ljarka.picfort.db.AssetType

data class AssetUi(
    val url: String,
    val span: Int,
    val id: String,
    val type: AssetType,
)