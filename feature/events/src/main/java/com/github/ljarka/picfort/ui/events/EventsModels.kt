package com.github.ljarka.picfort.ui.events

import com.github.ljarka.picfort.core.model.FamilyEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class EventSection(val title: String, val isNow: Boolean = false, val events: List<FamilyEvent>)

data class DateBadgeText(val top: String, val bottom: String)

/**
 * Converts Material3 DatePicker UTC milliseconds to a [LocalDate] without shifting by time zone.
 */
fun pickerMillisToLocalDate(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

fun localDateToPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * Relative label for upcoming events: "Today", "Tomorrow", "in N days" (up to 60 days; null after).
 * Ongoing multi-day events and past events return null.
 */
fun relativeEventLabel(event: FamilyEvent, today: LocalDate): String? {
    if (event.isOngoingOn(today)) return null
    val days = ChronoUnit.DAYS.between(today, event.startDate)
    return when {
        days < 0L -> null
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days <= 60L -> "in $days days"
        else -> null
    }
}

/**
 * Formats an event's date or date range for display.
 * Within one month: "10–14 Nov"; across months: "28 Dec – 3 Jan".
 * Shows the year when it differs from [today]'s year.
 */
fun formatEventDateRange(
    startDate: LocalDate,
    endDate: LocalDate?,
    today: LocalDate,
    locale: Locale = Locale.ENGLISH,
): String {
    val effectiveEnd = endDate?.takeIf { it.isAfter(startDate) }
    val showStartYear = startDate.year != today.year || (effectiveEnd != null && effectiveEnd.year != startDate.year)
    if (effectiveEnd == null) {
        val pattern = if (showStartYear) "d MMM yyyy" else "d MMM"
        return startDate.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
    val showEndYear = effectiveEnd.year != today.year || effectiveEnd.year != startDate.year
    return if (startDate.year == effectiveEnd.year && startDate.month == effectiveEnd.month) {
        val monthPattern = if (showEndYear) "MMM yyyy" else "MMM"
        val month = effectiveEnd.format(DateTimeFormatter.ofPattern(monthPattern, locale))
        "${startDate.dayOfMonth}–${effectiveEnd.dayOfMonth} $month"
    } else {
        val startPattern = if (showStartYear) "d MMM yyyy" else "d MMM"
        val endPattern = if (showEndYear) "d MMM yyyy" else "d MMM"
        val startStr = startDate.format(DateTimeFormatter.ofPattern(startPattern, locale))
        val endStr = effectiveEnd.format(DateTimeFormatter.ofPattern(endPattern, locale))
        "$startStr – $endStr"
    }
}

fun formatEventDateBadge(startDate: LocalDate, endDate: LocalDate?, locale: Locale = Locale.ENGLISH): DateBadgeText {
    val monthFmt = DateTimeFormatter.ofPattern("MMM", locale)
    val effectiveEnd = endDate?.takeIf { it.isAfter(startDate) }
    if (effectiveEnd == null) {
        return DateBadgeText(
            top = startDate.dayOfMonth.toString(),
            bottom = startDate.format(monthFmt).uppercase(locale),
        )
    }
    return if (startDate.month == effectiveEnd.month && startDate.year == effectiveEnd.year) {
        DateBadgeText(
            top = "${startDate.dayOfMonth}–${effectiveEnd.dayOfMonth}",
            bottom = startDate.format(monthFmt).uppercase(locale),
        )
    } else {
        val sMon = startDate.format(monthFmt).uppercase(locale)
        val eMon = effectiveEnd.format(monthFmt).uppercase(locale)
        DateBadgeText(
            top = "${startDate.dayOfMonth} $sMon",
            bottom = "– ${effectiveEnd.dayOfMonth} $eMon",
        )
    }
}

fun formatMonthHeader(yearMonth: YearMonth, locale: Locale = Locale.ENGLISH): String =
    yearMonth.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).uppercase(locale)

/**
 * Groups upcoming events into an optional "NOW" section (ongoing multi-day events where
 * start <= today <= end) followed by month sections ordered by start date, then start time
 * (untimed first), then id.
 */
fun groupUpcomingEvents(
    events: List<FamilyEvent>,
    today: LocalDate,
    locale: Locale = Locale.ENGLISH,
): List<EventSection> {
    val upcoming = events.filter { !it.effectiveEndDate.isBefore(today) }
    val comparator = compareBy<FamilyEvent>(
        { it.startDate },
        { it.startTime != null },
        { it.startTime },
        { it.id },
    )
    val (ongoing, futureOrTodayOneDay) = upcoming.sortedWith(comparator).partition { it.isOngoingOn(today) }
    val sections = mutableListOf<EventSection>()
    if (ongoing.isNotEmpty()) {
        sections += EventSection(title = "NOW", isNow = true, events = ongoing)
    }
    futureOrTodayOneDay
        .groupBy { YearMonth.from(it.startDate) }
        .forEach { (ym, monthEvents) ->
            sections += EventSection(
                title = formatMonthHeader(ym, locale),
                isNow = false,
                events = monthEvents,
            )
        }
    return sections
}

/**
 * Groups past events (latest effective end date first) into month sections by start date's month.
 */
fun groupPastEvents(events: List<FamilyEvent>, today: LocalDate, locale: Locale = Locale.ENGLISH): List<EventSection> {
    val past = events
        .filter { it.effectiveEndDate.isBefore(today) }
        .sortedWith(
            compareByDescending<FamilyEvent> { it.effectiveEndDate }
                .thenByDescending { it.startDate }
                .thenByDescending { it.id },
        )
    val sections = mutableListOf<EventSection>()
    for (event in past) {
        val header = formatMonthHeader(YearMonth.from(event.startDate), locale)
        val last = sections.lastOrNull()
        if (last != null && last.title == header) {
            sections[sections.lastIndex] = last.copy(events = last.events + event)
        } else {
            sections += EventSection(title = header, isNow = false, events = listOf(event))
        }
    }
    return sections
}

/** "turns 9" for a birthday whose birth year is known; null otherwise. */
fun turnsLabel(event: FamilyEvent): String? = event.turns?.takeIf { event.isBirthday && it > 0 }?.let { "turns $it" }

/** "Every week", or "Every week · until 20 Dec 2026"; null for an event that doesn't repeat. */
fun repeatSummary(event: FamilyEvent, today: LocalDate, locale: Locale = Locale.ENGLISH): String? {
    val repeat = event.repeat ?: return null
    val until = event.repeatUntil ?: return repeat.label
    val pattern = if (until.year != today.year) "d MMM yyyy" else "d MMM"
    return "${repeat.label} · until ${until.format(DateTimeFormatter.ofPattern(pattern, locale))}"
}
