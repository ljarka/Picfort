package com.github.ljarka.picfort.ui.timeline.layout

import com.github.ljarka.picfort.core.ui.layout.justify
import com.github.ljarka.picfort.ui.timeline.TimelineAsset
import com.github.ljarka.picfort.ui.timeline.TimelineIndex

/** What the timeline list draws, top to bottom. Sizes are in pixels. */
sealed interface TimelineRow {
    val key: String
}

data class MonthHeaderRow(val month: Long, val title: String) : TimelineRow {
    override val key: String get() = "m$month"
}

/** A tile: [index] is the asset's position in the whole timeline (for the viewer). */
data class Tile(val asset: TimelineAsset, val index: Int, val url: String, val width: Float)

data class TilesRow(val month: Long, val row: Int, val tiles: List<Tile>, val height: Float) : TimelineRow {
    override val key: String get() = "m${month}_$row"
}

/** A month's title and the position of its header in [TimelineLayout.rows] (for the fast scroller). */
data class MonthMarker(val month: Long, val title: String, val rowIndex: Int)

data class TimelineLayout(val rows: List<TimelineRow>, val months: List<MonthMarker>) {
    companion object {
        val EMPTY = TimelineLayout(emptyList(), emptyList())
    }
}

/** Screen space for the timeline, in pixels. */
data class Viewport(val width: Float, val height: Float, val spacing: Float)

/**
 * About this many square photos per row. Measured with the scroll benchmark: 2.5 in portrait
 * scrolls as smoothly as the old grid; 4 (Google Photos density) doubled the slow frames and
 * added ~65 MB, because about three times as many tiles were on screen. Landscape keeps the
 * same tile size.
 */
private const val PORTRAIT_PER_ROW = 2.5f
private const val LANDSCAPE_PER_ROW = 4.5f

/**
 * Lays out every month of [index] in justified rows for [viewport]. Pure, so the whole
 * timeline is laid out once (off the main thread) and the list only draws what it is given.
 */
fun layoutTimeline(
    index: TimelineIndex,
    viewport: Viewport,
    titleOf: (Long) -> String,
    urlOf: (TimelineAsset) -> String,
): TimelineLayout {
    if (viewport.width <= 0f) return TimelineLayout.EMPTY
    val perRow = if (viewport.width > viewport.height) LANDSCAPE_PER_ROW else PORTRAIT_PER_ROW
    val target = (viewport.width - viewport.spacing * (perRow - 1f)) / perRow
    val rows = ArrayList<TimelineRow>()
    val months = ArrayList<MonthMarker>()
    index.months.forEach { month ->
        val title = titleOf(month.key)
        months += MonthMarker(month.key, title, rows.size)
        rows += MonthHeaderRow(month.key, title)
        justify(month.assets.map { it.ratio }, viewport.width, target, viewport.spacing)
            .forEachIndexed { r, row ->
                val tiles = row.widths.mapIndexed { k, w ->
                    val asset = month.assets[row.start + k]
                    Tile(asset, month.start + row.start + k, urlOf(asset), w)
                }
                rows += TilesRow(month.key, r, tiles, row.height)
            }
    }
    return TimelineLayout(rows, months)
}
