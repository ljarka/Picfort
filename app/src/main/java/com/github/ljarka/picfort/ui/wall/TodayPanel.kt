package com.github.ljarka.picfort.ui.wall

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.ui.events.FamilyEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** "Next: …" is shown only for events starting within this many days. */
const val NEXT_EVENT_WINDOW_DAYS = 30L

/**
 * What the Family Wall's today panel shows (T-027). [event] is today's most important event;
 * without one, [next] is the upcoming event within [NEXT_EVENT_WINDOW_DAYS] (or null: date only).
 */
data class TodayPanelState(
    val date: LocalDate,
    val event: FamilyEvent? = null,
    /** "Day N of M" for a multi-day [event]. */
    val dayOfTrip: Pair<Int, Int>? = null,
    /** Other events today, shown as "+N more". */
    val moreToday: Int = 0,
    val next: FamilyEvent? = null,
    val nextInDays: Int? = null,
)

private val createdThenId = compareBy<FamilyEvent> { it.created }.thenBy { it.id }

/**
 * Builds the panel for [today] at [now] from the loaded upcoming [events]:
 * 1. events starting today with a time: the earliest that hasn't started yet, else the latest;
 * 2. ongoing multi-day events (a start time counts only on the start day);
 * 3. one-day events without a time.
 * Ties: earliest `created`, then lowest `id`.
 */
fun todayPanel(events: List<FamilyEvent>, today: LocalDate, now: LocalTime): TodayPanelState {
    val todays = events.filter { !it.startDate.isAfter(today) && !it.effectiveEndDate.isBefore(today) }
    if (todays.isEmpty()) {
        val next = events.filter { it.startDate.isAfter(today) }
            .minWithOrNull(compareBy<FamilyEvent> { it.startDate }.then(createdThenId))
        val days = next?.let { ChronoUnit.DAYS.between(today, it.startDate) }
        return if (next != null && days != null && days <= NEXT_EVENT_WINDOW_DAYS) {
            TodayPanelState(today, next = next, nextInDays = days.toInt())
        } else {
            TodayPanelState(today)
        }
    }
    val timed = todays.filter { it.startDate == today && it.startTime != null }
    val chosen = if (timed.isNotEmpty()) {
        val byTime = compareBy<FamilyEvent> { it.startTime }.then(createdThenId)
        val upcoming = timed.filter { !it.startTime!!.isBefore(now) }
        if (upcoming.isNotEmpty()) upcoming.minWith(byTime) else timed.maxWith(byTime)
    } else {
        val multiDay = todays.filter { it.isMultiDay }
        (multiDay.ifEmpty { todays }).minWith(createdThenId)
    }
    val trip = if (chosen.isMultiDay) {
        val day = ChronoUnit.DAYS.between(chosen.startDate, today).toInt() + 1
        val total = ChronoUnit.DAYS.between(chosen.startDate, chosen.effectiveEndDate).toInt() + 1
        day to total
    } else {
        null
    }
    return TodayPanelState(today, event = chosen, dayOfTrip = trip, moreToday = todays.size - 1)
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

/** "18:00" (only on the start day) and "Day 2 of 5", joined with " · ". */
internal fun TodayPanelState.eventTimeAndDay(): String? {
    val event = event ?: return null
    return listOfNotNull(
        event.startTime?.takeIf { event.startDate == date }?.format(TIME_FORMAT),
        dayOfTrip?.let { (day, total) -> "Day $day of $total" },
    ).joinToString(" · ").ifEmpty { null }
}

internal fun TodayPanelState.nextLine(): String? {
    val next = next ?: return null
    val days = nextInDays ?: return null
    return "Next: ${next.title} · ${if (days == 1) "tomorrow" else "in $days days"}"
}

/** Today in full for [locale], without the year: "Wednesday, 11 November". */
fun formatPanelDate(date: LocalDate, locale: Locale): String {
    val pattern = runCatching { DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM") }
        .getOrNull()?.takeIf { it.isNotBlank() } ?: "EEEE, d MMMM"
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

@Composable
fun TodayPanel(
    panel: TodayPanelState,
    onEventClick: (Long) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalLocale.current.platformLocale
    val dateText = remember(panel.date, locale) { formatPanelDate(panel.date, locale) }
    val event = panel.event ?: panel.next
    val title = panel.event?.title ?: panel.nextLine()
    val detail = listOfNotNull(panel.eventTimeAndDay(), panel.event?.location?.takeIf { it.isNotBlank() })
        .joinToString(" · ").ifEmpty { null }
    val more = panel.moreToday
    val description = buildString {
        append("Today, ").append(dateText).append('.')
        title?.let { append(' ').append(it) }
        detail?.let { append(", ").append(it) }
        if (more > 0) append(", plus $more more")
    }
    val shape = RoundedCornerShape(16.dp)
    val base = modifier.fillMaxWidth()
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = if (event != null) 2.dp else 0.dp,
        modifier = if (event != null) base.clickable { onEventClick(event.id) } else base,
    ) {
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                Text(
                    dateText,
                    style = if (event == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (title != null) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (more > 0) {
                Text(
                    "+$more more",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .clearAndSetSemantics {}
                        .clickable(onClick = onMoreClick)
                        .padding(8.dp),
                )
            }
        }
    }
}
