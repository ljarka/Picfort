package com.github.ljarka.picfort.ui.timeline.layout

import com.github.ljarka.picfort.core.ui.layout.justify
import com.github.ljarka.picfort.ui.timeline.TimelineAsset
import com.github.ljarka.picfort.ui.timeline.TimelineIndex
import java.time.LocalDate
import java.time.ZoneOffset

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

/**
 * "Best of the month" strip right under a month's header (T-094), scrolled sideways: every tile
 * is [height] px high and as wide as its photo's shape (portrait narrow, landscape wide).
 */
data class BestRow(val month: Long, val tiles: List<Tile>, val height: Float) : TimelineRow {
    override val key: String get() = "m${month}_best"
}

/** The strip is half the width high (capped at 40% of the height in landscape). */
private const val BEST_HEIGHT_OF_WIDTH = 0.5f
private const val BEST_MAX_HEIGHT_FACTOR = 0.4f

/** Shapes kept between a tall phone photo and a wide one; panoramas are cropped to this. */
private const val BEST_MIN_RATIO = 0.5f
private const val BEST_MAX_RATIO = 1.8f

/** No tile wider than this share of the width, so the next one always peeks in. */
private const val BEST_MAX_WIDTH_FACTOR = 0.85f

/** A month's title and the position of its header in [TimelineLayout.rows] (for the fast scroller). */
data class MonthMarker(val month: Long, val title: String, val rowIndex: Int)

data class TimelineLayout(val rows: List<TimelineRow>, val months: List<MonthMarker>) {
    /**
     * The row to scroll to for [day] (T-056): the row with its newest photo, or the month header
     * just above when that row starts the month. Days without photos land on the next older one;
     * days older than the whole timeline on its end. Null when there are no photos.
     */
    fun rowIndexOf(day: LocalDate): Int? {
        // dateTaken is wall-clock time written as UTC, like the day itself.
        val endOfDay = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val row = rows.indexOfFirst { r -> r is TilesRow && r.tiles.any { it.asset.dateTaken < endOfDay } }
            .takeIf { it >= 0 } ?: rows.indexOfLast { it is TilesRow }.takeIf { it >= 0 } ?: return null
        var top = row
        if (top > 0 && rows[top - 1] is BestRow) top--
        return if (top > 0 && rows[top - 1] is MonthHeaderRow) top - 1 else row
    }

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
        if (month.best.isNotEmpty()) {
            val height = minOf(viewport.width * BEST_HEIGHT_OF_WIDTH, viewport.height * BEST_MAX_HEIGHT_FACTOR)
            val tiles = month.best.map { k ->
                val asset = month.assets[k]
                val ratio = (asset.ratio ?: 1f).coerceIn(BEST_MIN_RATIO, BEST_MAX_RATIO)
                Tile(
                    asset,
                    month.start + k,
                    urlOf(asset),
                    minOf(height * ratio, viewport.width * BEST_MAX_WIDTH_FACTOR),
                )
            }
            rows += BestRow(month.key, tiles, height)
        }
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
