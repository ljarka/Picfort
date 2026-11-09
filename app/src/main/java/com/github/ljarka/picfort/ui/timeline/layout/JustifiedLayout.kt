package com.github.ljarka.picfort.ui.timeline.layout

import kotlin.math.abs

/** Panoramas and very tall screenshots are cropped to this range instead of making odd rows. */
const val MIN_RATIO = 0.5f
const val MAX_RATIO = 2.5f

/**
 * One row of tiles: items [start] until `start + widths.size`, each [height] tall.
 * Widths include no spacing; tiles are separated by the spacing given to [justify].
 */
data class JustifiedRow(val start: Int, val widths: List<Float>, val height: Float) {
    val count: Int get() = widths.size
}

/**
 * Lays out items with the given aspect ratios (width / height) in rows of [width], like
 * Google Photos: every row but the last fills the width exactly, items keep their (clamped)
 * ratio, and row heights stay near [targetHeight]. The last row keeps [targetHeight] and is
 * left ragged. Unknown ratios (null, 0, NaN) count as square.
 */
fun justify(
    ratios: List<Float?>,
    width: Float,
    targetHeight: Float,
    spacing: Float,
): List<JustifiedRow> {
    if (ratios.isEmpty() || width <= 0f || targetHeight <= 0f) return emptyList()
    val r = ratios.map { clampRatio(it) }
    val rows = ArrayList<JustifiedRow>()
    var start = 0
    var sum = 0f // sum of ratios in the current row

    fun gaps(n: Int) = spacing * (n - 1).coerceAtLeast(0)
    fun heightFor(sumRatios: Float, n: Int) = (width - gaps(n)) / sumRatios
    fun close(end: Int, height: Float) {
        val widths = (start until end).map { r[it] * height }.toMutableList()
        // Absorb rounding so the row is exactly as wide as asked.
        widths[widths.lastIndex] = width - gaps(widths.size) - widths.dropLast(1).sum()
        rows += JustifiedRow(start, widths, height)
        start = end
        sum = 0f
    }

    var i = 0
    while (i < r.size) {
        val n = i - start + 1
        val withItem = sum + r[i]
        if (withItem * targetHeight + gaps(n) < width) {
            sum = withItem // still room in the row
            i++
            continue
        }
        // The row is full with this item. Keep it, or move it to the next row, whichever
        // leaves this row's height closer to the target.
        val hWith = heightFor(withItem, n)
        val hWithout = if (n > 1) heightFor(sum, n - 1) else Float.NaN
        if (n > 1 && abs(hWithout - targetHeight) < abs(hWith - targetHeight)) {
            close(i, hWithout)
        } else {
            close(i + 1, hWith)
            i++
        }
    }
    if (start < r.size) {
        rows += JustifiedRow(start, (start until r.size).map { r[it] * targetHeight }, targetHeight)
    }
    return rows
}

fun clampRatio(ratio: Float?): Float =
    if (ratio == null || ratio.isNaN() || ratio <= 0f) 1f else ratio.coerceIn(MIN_RATIO, MAX_RATIO)
