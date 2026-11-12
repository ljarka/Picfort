package com.github.ljarka.picfort.core.model

import java.time.LocalDate
import java.time.LocalTime

/** How a series repeats (T-035); [wire] is the server's `repeat` value. */
enum class EventRepeat(val wire: String, val label: String) {
    WEEKLY("weekly", "Every week"),
    MONTHLY("monthly", "Every month"),
    YEARLY("yearly", "Every year"),
    ;

    companion object {
        fun fromWire(value: String?): EventRepeat? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One event as shown: for a series, [startDate] / [endDate] are the occurrence the server picked
 * (ongoing or next; the last one in Past), so lists, labels and the Wall's today panel need no
 * repeat logic. [seriesStartDate] / [seriesEndDate] are the first occurrence, which the form edits.
 */
data class FamilyEvent(
    val id: Long,
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val location: String? = null,
    val note: String? = null,
    val albumId: String? = null,
    val createdBy: String = "",
    val created: String = "",
    val updated: String = "",
    val repeat: EventRepeat? = null,
    val repeatUntil: LocalDate? = null,
    val seriesStartDate: LocalDate = startDate,
    val seriesEndDate: LocalDate? = endDate,
    /** Set for birthday events (T-036): the family member whose birthday it is. */
    val birthdayOf: String? = null,
    /** Age reached on this occurrence, when the birth year is known. */
    val turns: Int? = null,
) {
    val effectiveEndDate: LocalDate get() = endDate ?: startDate
    val isMultiDay: Boolean get() = endDate != null && endDate.isAfter(startDate)
    val isBirthday: Boolean get() = birthdayOf != null
    val repeats: Boolean get() = repeat != null

    /** A series without an end date never ends; one with an end date ends after [repeatUntil]. */
    fun seriesContinuesAfter(day: LocalDate): Boolean =
        repeat != null && (repeatUntil == null || repeatUntil.isAfter(day))

    fun isOngoingOn(today: LocalDate): Boolean =
        isMultiDay && !startDate.isAfter(today) && !effectiveEndDate.isBefore(today)

    /** "Ola's birthday" from the member's current name; the stored title otherwise. */
    fun displayTitle(memberName: (String) -> String?): String =
        birthdayOf?.let(memberName)?.let { "$it's birthday" } ?: title
}

enum class EventsSegment { UPCOMING, PAST }
