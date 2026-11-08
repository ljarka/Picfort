package com.github.ljarka.picfort.ui.timeline

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/** Columns in the timeline grid. */
const val GRID_COLUMNS = 4

/** How many of the 4 columns a photo with this width/height ratio takes. */
fun calculateSpan(ratio: Double): Int = when {
    ratio >= 1.5 -> 4
    ratio >= 1.35 -> 3
    ratio >= 1 -> 2
    else -> 1
}

/**
 * Shrinks spans so every row is exactly [GRID_COLUMNS] wide (except possibly the
 * last): an item that would overflow a row is narrowed to fill what's left of it.
 */
fun adjustSpans(spans: List<Int>): List<Int> {
    val result = ArrayList<Int>(spans.size)
    var used = 0
    for (span in spans) {
        val s = span.coerceIn(1, GRID_COLUMNS)
        val free = GRID_COLUMNS - used
        val fitted = if (s <= free) s else free
        result += fitted
        used = (used + fitted) % GRID_COLUMNS
    }
    return result
}

/** Rows needed for items with these (adjusted) spans. */
fun rowCount(spans: List<Int>): Int = (spans.sum() + GRID_COLUMNS - 1) / GRID_COLUMNS

/** Immich bucket keys: full ISO instants, local date-times (UTC) or plain dates (midnight UTC). */
fun parseBucketDate(timeBucket: String): Instant = try {
    Instant.parse(timeBucket)
} catch (e: DateTimeParseException) {
    try {
        LocalDateTime.parse(timeBucket).toInstant(ZoneOffset.UTC)
    } catch (e: DateTimeParseException) {
        LocalDate.parse(timeBucket).atStartOfDay().toInstant(ZoneOffset.UTC)
    }
}

/**
 * Combines per-month counts from the phone and from Immich (keyed by month start,
 * epoch millis) into the timeline's months: newest first, empty months dropped.
 */
fun mergeMonthCounts(local: Map<Long, Int>, remote: Map<Long, Int>): List<Pair<Long, Int>> =
    (local.keys + remote.keys)
        .map { month -> month to (local[month] ?: 0) + (remote[month] ?: 0) }
        .filter { (_, count) -> count > 0 }
        .sortedByDescending { (month, _) -> month }
