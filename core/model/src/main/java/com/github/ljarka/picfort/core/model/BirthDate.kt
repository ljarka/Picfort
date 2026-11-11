package com.github.ljarka.picfort.core.model

import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Birth date: either with a known birth year (e.g. 2015-06-12) or without a year (--06-12).
 */
data class BirthDate(
    val month: Int,
    val day: Int,
    val year: Int? = null,
) {
    /** Serializes to ISO 8601 string: YYYY-MM-DD or --MM-DD */
    fun toIsoString(): String = if (year != null) {
        String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    } else {
        String.format(Locale.US, "--%02d-%02d", month, day)
    }

    /** Formats for display: e.g. "17 May" or "17 May · 36" */
    fun formatDisplay(today: LocalDate = LocalDate.now()): String {
        val monthName = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault())
        val dateText = "$day $monthName"
        if (year == null) return dateText
        val age = today.year - year - if (today.monthValue < month || (today.monthValue == month && today.dayOfMonth < day)) 1 else 0
        return "$dateText · $age"
    }

    companion object {
        fun parse(raw: String?): BirthDate? {
            if (raw.isNullOrBlank()) return null
            if (raw.startsWith("--")) {
                val parts = raw.removePrefix("--").split("-")
                if (parts.size == 2) {
                    val m = parts[0].toIntOrNull() ?: return null
                    val d = parts[1].toIntOrNull() ?: return null
                    return BirthDate(m, d, null)
                }
            } else {
                val parts = raw.split("-")
                if (parts.size == 3) {
                    val y = parts[0].toIntOrNull() ?: return null
                    val m = parts[1].toIntOrNull() ?: return null
                    val d = parts[2].toIntOrNull() ?: return null
                    return BirthDate(m, d, y)
                }
            }
            return null
        }
    }
}
