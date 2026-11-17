package com.github.ljarka.picfort.ui.schooljournal

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Older than this, the "Last successful sync" line turns to the warning color (spec §4.1). */
val STALE_AFTER: Duration = Duration.ofHours(1)

/**
 * The always-visible status line (spec §4.1), e.g.
 * "Last successful sync: today 14:32 (12 min ago) · next 14:47".
 */
data class SyncLine(
    val text: String,
    /** No successful sync, or the last one is older than [STALE_AFTER]. */
    val stale: Boolean,
    /** The last run failed: the UI adds "· last check failed" in the error color. */
    val failed: Boolean,
)

fun syncLine(status: JournalStatus, now: Instant, zone: ZoneId, locale: Locale, is24Hour: Boolean): SyncLine {
    val failed = status.lastSync?.failed == true
    val last = instantOrNull(status.lastSuccessfulSyncAt)
    val next = instantOrNull(status.nextSyncAt)?.takeIf { it.isAfter(now) }
    val nextText = next?.let { " · next ${whenText(it, now, zone, locale, is24Hour, withToday = false)}" }.orEmpty()
    if (last == null) return SyncLine("No successful sync yet$nextText", stale = true, failed = failed)
    val text = "Last successful sync: ${whenText(last, now, zone, locale, is24Hour, withToday = true)} " +
        "(${relative(Duration.between(last, now))})$nextText"
    return SyncLine(text, stale = Duration.between(last, now) > STALE_AFTER, failed = failed)
}

/** "today 14:32", "Mon 16 Nov, 14:32"; plain "14:47" for a time later today when [withToday] is off. */
fun whenText(at: Instant, now: Instant, zone: ZoneId, locale: Locale, is24Hour: Boolean, withToday: Boolean): String {
    val time = DateTimeFormatter.ofPattern(if (is24Hour) "H:mm" else "h:mm a", locale)
    val local = at.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    return when (local.toLocalDate()) {
        today -> if (withToday) "today ${local.format(time)}" else local.format(time)
        today.minusDays(1) -> "yesterday ${local.format(time)}"
        today.plusDays(1) -> "tomorrow ${local.format(time)}"
        else -> local.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)) + ", " + local.format(time)
    }
}

/** "just now", "12 min ago", "3 h ago", "2 days ago". */
fun relative(age: Duration): String {
    val minutes = age.toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 2 * 24 * 60 -> "1 day ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}

/** A list row's date: "14:32" today, "16 Nov" this year, "16 Nov 2025" before. */
fun shortWhen(at: Instant, now: Instant, zone: ZoneId, locale: Locale, is24Hour: Boolean): String {
    val local = at.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    return when {
        local.toLocalDate() == today -> local.format(
            DateTimeFormatter.ofPattern(if (is24Hour) "H:mm" else "h:mm a", locale),
        )

        local.year == today.year -> local.format(DateTimeFormatter.ofPattern("d MMM", locale))

        else -> local.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }
}
