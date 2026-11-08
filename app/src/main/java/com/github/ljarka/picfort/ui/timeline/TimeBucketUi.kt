package com.github.ljarka.picfort.ui.timeline

data class TimeBucketUi(
    val timeStamp: Long = 0,
    val count: Int = 0,
    val formattedDate: String = "",
    val index: Int = 0,
    val numberOfRows: Int? = null,
    val items: List<AssetUi> = emptyList(),
    val lastUpdate: Long = 0,
)