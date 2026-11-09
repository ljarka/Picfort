package com.github.ljarka.picfort.ui.timeline

import com.github.ljarka.picfort.ui.timeline.layout.Viewport
import kotlinx.coroutines.flow.StateFlow

/** What the timeline list needs from its ViewModel; lets tests render it with fake data. */
interface TimelineGridModel {
    val selectedAssets: StateFlow<Set<String>>
    fun selectAsset(asset: TimelineAsset, month: Long)
    fun deselectAsset(assetId: String)
    fun refresh()

    /** The list's size; the timeline is laid out for it. */
    fun setViewport(viewport: Viewport)
}
