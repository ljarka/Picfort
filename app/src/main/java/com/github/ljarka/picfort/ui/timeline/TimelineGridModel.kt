package com.github.ljarka.picfort.ui.timeline

import kotlinx.coroutines.flow.StateFlow

/** What the timeline grid needs from its ViewModel; lets tests render it with fake data. */
interface TimelineGridModel {
    val selectedAssets: StateFlow<Set<String>>
    fun getAsset(bucket: Long, position: Int): AssetUi?
    fun getAssetLoadingState(bucket: Long): StateFlow<AssetLoadingState>
    fun fetchAssets(bucket: Long)
    fun isFixedSpan(bucket: Long): Boolean
    fun setFixedSpan(bucket: Long)
    fun selectAsset(asset: AssetUi, bucket: Long)
    fun deselectAsset(assetId: String)
    fun refresh()
}
