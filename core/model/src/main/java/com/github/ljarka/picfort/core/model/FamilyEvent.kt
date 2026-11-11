package com.github.ljarka.picfort.core.model

import java.time.LocalDate
import java.time.LocalTime

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
) {
    val effectiveEndDate: LocalDate get() = endDate ?: startDate
    val isMultiDay: Boolean get() = endDate != null && endDate.isAfter(startDate)

    fun isOngoingOn(today: LocalDate): Boolean =
        isMultiDay && !startDate.isAfter(today) && !effectiveEndDate.isBefore(today)
}

enum class EventsSegment { UPCOMING, PAST }
