package com.github.ljarka.picfort.ui.shopping.reminders

import com.github.ljarka.picfort.core.model.ShoppingList
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** A quick choice in the reminder sheet (T-040): its label and when it goes off. */
data class ReminderPresetTime(val label: String, val at: LocalDateTime)

/** Items named in the notification's text; the rest are counted. */
private const val NAMED_ITEMS = 5

/**
 * The sheet's presets for [now]: today's ones only while still ahead, Saturday's the next
 * Saturday 09:00 still ahead (today when it's Saturday before 9).
 */
fun reminderPresets(now: LocalDateTime): List<ReminderPresetTime> {
    val today = now.toLocalDate()
    val saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).atTime(9, 0)
    return listOfNotNull(
        ReminderPresetTime("Today at 17:00", today.atTime(17, 0)),
        ReminderPresetTime("Tonight at 19:00", today.atTime(19, 0)),
        ReminderPresetTime("Tomorrow at 10:00", today.plusDays(1).atTime(10, 0)),
        ReminderPresetTime(
            "Saturday morning at 09:00",
            if (saturday.isAfter(now)) saturday else saturday.plusWeeks(1),
        ),
    ).filter { it.at.isAfter(now) }
}

/** "Today, 17:00", "Tomorrow, 10:00", "Saturday, 09:00" (within a week), else "28 Nov, 17:00". */
fun reminderLabel(at: LocalDateTime, today: LocalDate, locale: Locale = Locale.ENGLISH): String {
    val time = at.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm", locale))
    val day = at.toLocalDate()
    val dayLabel = when {
        day == today -> "Today"
        day == today.plusDays(1) -> "Tomorrow"
        day.isAfter(today) && day.isBefore(today.plusDays(7)) -> day.format(DateTimeFormatter.ofPattern("EEEE", locale))
        else -> day.format(DateTimeFormatter.ofPattern("d MMM", locale))
    }
    return "$dayLabel, $time"
}

/** The custom date and time from the pickers, or null when it's not ahead of [now]. */
fun customReminderTime(date: LocalDate, time: LocalTime, now: LocalDateTime): LocalDateTime? =
    date.atTime(time).takeIf { it.isAfter(now) }

/** "Time for groceries: Biedronka". */
fun reminderTitle(listTitle: String): String = "Time for groceries: $listTitle"

/**
 * The notification's text for [list], or null when nothing is left to buy (the reminder is then
 * dropped). A list the phone doesn't know (no copy yet) gets a plain prompt.
 */
fun reminderText(list: ShoppingList?): String? {
    if (list == null) return "Open the list to see what's left to buy."
    val left = list.unchecked
    if (left.isEmpty()) return null
    val names = left.take(NAMED_ITEMS).joinToString(", ") { it.text } + if (left.size > NAMED_ITEMS) "…" else ""
    val count = if (left.size == 1) "1 item left to buy" else "${left.size} items left to buy"
    return "$count: $names"
}

/**
 * Lists in [reminded] that lose their reminder: archived, or known [before] and gone [after]
 * (deleted, or archived by someone else). A list never loaded yet keeps its reminder.
 */
fun goneLists(reminded: Set<Long>, before: Map<Long, ShoppingList>, after: Map<Long, ShoppingList>): Set<Long> =
    reminded.filterTo(HashSet()) { id ->
        val list = after[id]
        (list == null && id in before) || list?.archived == true
    }

/** Lists whose last item to buy was just checked off: a shown reminder for them goes away. */
fun finishedLists(before: Map<Long, ShoppingList>, after: Map<Long, ShoppingList>): Set<Long> =
    after.values.filter { it.unchecked.isEmpty() && before[it.id]?.unchecked?.isNotEmpty() == true }
        .mapTo(HashSet()) { it.id }
