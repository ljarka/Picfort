package com.github.ljarka.picfort.ui.school

import java.time.DayOfWeek
import java.time.LocalTime

enum class EntryKind(val wire: String) {
    LESSON("lesson"),
    ACTIVITY("activity"),
    ;

    companion object {
        fun fromWire(value: String): EntryKind = entries.firstOrNull { it.wire == value } ?: LESSON
    }
}

/** One period of a child's bell schedule: "period 3 is 09:50–10:35". */
data class Period(val number: Int, val start: LocalTime, val end: LocalTime)

/** A lesson or an activity in the weekly template (no dates: the same every week). */
data class SchoolEntry(
    val id: Long,
    val kind: EntryKind,
    val weekday: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val title: String,
    val period: Int? = null,
    val place: String? = null,
    val teacher: String? = null,
    val note: String? = null,
) {
    val isLesson: Boolean get() = kind == EntryKind.LESSON
}

data class Child(
    val id: Long,
    val name: String,
    /** Immich avatar color name (T-002). */
    val color: String,
    val photoUrl: String? = null,
    val myPersonId: String? = null,
    val periods: List<Period> = emptyList(),
    val entries: List<SchoolEntry> = emptyList(),
) {
    fun on(day: DayOfWeek): List<SchoolEntry> =
        entries.filter { it.weekday == day }.sortedWith(compareBy({ it.start }, { it.id }))
}

/** Avatar colors a child can wear, as the server accepts them. */
val CHILD_COLORS = listOf("pink", "red", "orange", "yellow", "green", "blue", "purple", "gray")

/** A new child's color: different children get different ones, without asking. */
fun defaultChildColor(taken: List<String>): String =
    CHILD_COLORS.firstOrNull { it !in taken } ?: CHILD_COLORS[taken.size % CHILD_COLORS.size]

// --- Today's summary (the card on top) ---

sealed interface SchoolStatus {
    data object NoSchool : SchoolStatus

    /** Lessons today, not finished yet; [startsAt] only before the first one. */
    data class Today(val finishesAt: LocalTime, val startsAt: LocalTime?) : SchoolStatus

    data class Finished(val finishedAt: LocalTime) : SchoolStatus
}

/** Today's school for one child: lessons' status and the activities that haven't ended. */
data class SchoolDay(val status: SchoolStatus, val activities: List<SchoolEntry>)

/** The summary card's content for [day] at [now] (local wall-clock time). */
fun schoolDay(entries: List<SchoolEntry>, day: DayOfWeek, now: LocalTime): SchoolDay {
    val today = entries.filter { it.weekday == day }
    val lessons = today.filter { it.isLesson }
    val status = if (lessons.isEmpty()) {
        SchoolStatus.NoSchool
    } else {
        val start = lessons.minOf { it.start }
        val end = lessons.maxOf { it.end }
        if (now < end) SchoolStatus.Today(end, start.takeIf { now < it }) else SchoolStatus.Finished(end)
    }
    val activities = today.filter { !it.isLesson && now < it.end }.sortedBy { it.start }
    return SchoolDay(status, activities)
}

/** "8:00–8:45" or, on a 12-hour clock, "8:00–8:45 AM" (the AM/PM said once when both share it). */
fun timeRange(start: LocalTime, end: LocalTime, time: (LocalTime) -> String): String {
    val a = time(start)
    val b = time(end)
    val suffix = b.substringAfterLast(' ', "")
    val shared = suffix.isNotEmpty() && suffix.none(Char::isDigit) && a.endsWith(" $suffix")
    return (if (shared) a.removeSuffix(" $suffix") else a) + "–" + b
}

/** Whether [time] writes AM/PM. */
fun isTwelveHour(time: (LocalTime) -> String): Boolean = time(LocalTime.of(13, 0)).any(Char::isLetter)

/** The card's lines: "Today Ola finishes school at 13:40", "Starts at 08:00", "then Swimming …". */
fun summaryLines(name: String, day: SchoolDay, time: (LocalTime) -> String): List<String> {
    val lines = when (val s = day.status) {
        SchoolStatus.NoSchool -> listOf("No school today")

        is SchoolStatus.Today -> listOfNotNull(
            "Today $name finishes school at ${time(s.finishesAt)}",
            s.startsAt?.let { "Starts at ${time(it)}" },
        )

        is SchoolStatus.Finished -> listOf("$name finished school at ${time(s.finishedAt)}")
    }
    val withLessons = day.status != SchoolStatus.NoSchool
    val activities = day.activities.mapIndexed { i, a ->
        val text = "${a.title} ${timeRange(a.start, a.end, time)}" + (a.place?.let { " ($it)" } ?: "")
        if (withLessons && i == 0) "then $text" else text
    }
    return lines + activities
}

// --- Days shown ---

/** Mon–Fri always; Saturday and Sunday when the child has something then, or while editing. */
fun visibleDays(child: Child?, editing: Boolean): List<DayOfWeek> = DayOfWeek.entries.filter { day ->
    day <= DayOfWeek.FRIDAY || editing || child?.entries?.any { it.weekday == day } == true
}

/** The day to open on: today if it has a chip, else Monday. */
fun startDay(today: DayOfWeek, days: List<DayOfWeek>): DayOfWeek = today.takeIf { it in days } ?: DayOfWeek.MONDAY

// --- Editing helpers ---

/**
 * A bell schedule from the first lesson's start, lesson length, break and number of lessons
 * (periods 1..count). Stops before a lesson would pass midnight.
 */
fun fillBells(first: LocalTime, lessonMinutes: Int, breakMinutes: Int, count: Int): List<Period> {
    val periods = mutableListOf<Period>()
    var start = first
    for (n in 1..count) {
        val end = start.plusMinutes(lessonMinutes.toLong())
        if (end <= start) break
        periods += Period(n, start, end)
        val next = end.plusMinutes(breakMinutes.toLong())
        if (next < end) break
        start = next
    }
    return periods
}

/** The first period with no lesson on [day], or null when all are taken (or there are none). */
fun firstFreePeriod(child: Child, day: DayOfWeek, after: Int? = null): Int? {
    val taken = child.entries.filter { it.weekday == day && it.isLesson }.mapNotNull { it.period }.toSet()
    return child.periods.map { it.number }.sorted().firstOrNull { it !in taken && (after == null || it > after) }
}

/** Subjects the child already has, most frequent first: suggestions for a new lesson. */
fun subjects(child: Child): List<String> = child.entries.filter { it.isLesson }
    .groupingBy { it.title }.eachCount().entries
    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    .map { it.key }

/** The teacher of the latest-entered lesson of [subject], to prefill. */
fun teacherOf(child: Child, subject: String): String? = child.entries
    .filter { it.isLesson && it.title.equals(subject.trim(), ignoreCase = true) && !it.teacher.isNullOrBlank() }
    .maxByOrNull { it.id }?.teacher

/** What TalkBack reads for a row: "Period 1, 8:00 to 8:45, Maths, room 12". */
fun spokenEntry(entry: SchoolEntry, time: (LocalTime) -> String): String {
    val times = "${time(entry.start)} to ${time(entry.end)}"
    return if (entry.isLesson) {
        listOfNotNull(
            entry.period?.let { "Period $it" },
            times,
            entry.title,
            entry.place?.let { "room $it" },
            entry.teacher,
            entry.note,
        ).joinToString(", ")
    } else {
        listOfNotNull("Activity", entry.title, times, entry.place, entry.note).joinToString(", ")
    }
}
