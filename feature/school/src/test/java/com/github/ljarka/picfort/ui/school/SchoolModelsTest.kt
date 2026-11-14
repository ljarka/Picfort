package com.github.ljarka.picfort.ui.school

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun t(text: String) = LocalTime.parse(text)

private fun lesson(id: Long, day: DayOfWeek, start: String, end: String, title: String = "Maths", period: Int? = null) =
    SchoolEntry(
        id, EntryKind.LESSON, day,
        t(
            start,
        ),
        t(end), title, period = period, place = "12", teacher = "Mrs Nowak",
    )

private fun activity(id: Long, day: DayOfWeek, start: String, end: String, title: String, place: String? = null) =
    SchoolEntry(id, EntryKind.ACTIVITY, day, t(start), t(end), title, place = place)

class SchoolModelsTest {
    private val time: (LocalTime) -> String = { "%d:%02d".format(it.hour, it.minute) }
    private val week = listOf(
        lesson(1, MONDAY, "08:00", "08:45", period = 1),
        lesson(2, MONDAY, "08:55", "09:40", "Polish", period = 2),
        lesson(3, MONDAY, "12:55", "13:40", "Art"),
        activity(4, MONDAY, "15:00", "16:00", "Swimming", "Aquapark"),
        activity(5, MONDAY, "17:00", "17:45", "Chess"),
        activity(6, SATURDAY, "10:00", "11:00", "Football"),
    )

    private fun lines(day: DayOfWeek, now: String) = summaryLines("Ola", schoolDay(week, day, t(now)), time)

    @Test
    fun beforeSchoolSaysWhenItStartsAndEnds() {
        assertEquals(
            listOf(
                "Today Ola finishes school at 13:40",
                "Starts at 8:00",
                "then Swimming 15:00–16:00 (Aquapark)",
                "Chess 17:00–17:45",
            ),
            lines(MONDAY, "07:10"),
        )
    }

    @Test
    fun duringSchoolOnlyTheEnd() {
        assertEquals(
            listOf("Today Ola finishes school at 13:40", "then Swimming 15:00–16:00 (Aquapark)", "Chess 17:00–17:45"),
            lines(MONDAY, "08:00"),
        )
        assertEquals("Today Ola finishes school at 13:40", lines(MONDAY, "13:39").first())
    }

    @Test
    fun afterSchoolFinishedAndOnlyActivitiesNotOver() {
        assertEquals(
            listOf("Ola finished school at 13:40", "then Swimming 15:00–16:00 (Aquapark)", "Chess 17:00–17:45"),
            lines(MONDAY, "13:40"),
        )
        assertEquals(listOf("Ola finished school at 13:40", "then Chess 17:00–17:45"), lines(MONDAY, "16:00"))
        assertEquals(listOf("Ola finished school at 13:40"), lines(MONDAY, "23:59"))
    }

    @Test
    fun midnightIsBeforeSchool() {
        assertEquals("Starts at 8:00", lines(MONDAY, "00:00")[1])
    }

    @Test
    fun noLessonsNoSchoolWithActivitiesWithoutThen() {
        assertEquals(listOf("No school today", "Football 10:00–11:00"), lines(SATURDAY, "09:00"))
        assertEquals(listOf("No school today"), lines(SATURDAY, "11:00"))
        assertEquals(listOf("No school today"), lines(SUNDAY, "09:00"))
        assertEquals(listOf("No school today"), lines(TUESDAY, "09:00"))
    }

    @Test
    fun weekendChipsOnlyWithEntriesOrWhileEditing() {
        val child = Child(1, "Ola", "pink", entries = week)
        assertEquals(DayOfWeek.entries.take(6), visibleDays(child, editing = false))
        assertEquals(DayOfWeek.entries, visibleDays(child, editing = true))
        assertEquals(DayOfWeek.entries.take(5), visibleDays(child.copy(entries = emptyList()), editing = false))
        assertEquals(MONDAY, startDay(SUNDAY, DayOfWeek.entries.take(5)))
        assertEquals(FRIDAY, startDay(FRIDAY, DayOfWeek.entries.take(5)))
    }

    @Test
    fun fillBellsGeneratesPeriodsAndStopsBeforeMidnight() {
        val bells = fillBells(t("08:00"), 45, 10, 3)
        assertEquals(
            listOf(
                Period(1, t("08:00"), t("08:45")),
                Period(2, t("08:55"), t("09:40")),
                Period(3, t("09:50"), t("10:35")),
            ),
            bells,
        )
        assertEquals(2, fillBells(t("22:00"), 45, 10, 8).size)
    }

    @Test
    fun firstFreePeriodSkipsTakenOnes() {
        val child = Child(1, "Ola", "pink", periods = fillBells(t("08:00"), 45, 10, 4), entries = week)
        assertEquals(3, firstFreePeriod(child, MONDAY))
        assertEquals(1, firstFreePeriod(child, TUESDAY))
        assertEquals(4, firstFreePeriod(child, MONDAY, after = 3))
        assertNull(firstFreePeriod(child, MONDAY, after = 4))
        assertNull(firstFreePeriod(child.copy(periods = emptyList()), MONDAY))
    }

    @Test
    fun subjectsAndTeacherSuggestions() {
        val child = Child(1, "Ola", "pink", entries = week + lesson(7, TUESDAY, "08:00", "08:45", "Maths"))
        assertEquals(listOf("Maths", "Art", "Polish"), subjects(child))
        assertEquals("Mrs Nowak", teacherOf(child, " maths "))
        assertNull(teacherOf(child, "Swimming"))
    }

    @Test
    fun talkBackReadsLessonsAndActivities() {
        assertEquals("Period 1, 8:00 to 8:45, Maths, room 12, Mrs Nowak", spokenEntry(week[0], time))
        assertEquals("Activity, Swimming, 15:00 to 16:00, Aquapark", spokenEntry(week[3], time))
    }

    @Test
    fun timeRangeSaysAmPmOnceWhenShared() {
        val twelve = timeFormatter(false, java.util.Locale.US)
        assertEquals("8:00–8:45 AM", timeRange(t("08:00"), t("08:45"), twelve))
        assertEquals("11:40 AM–12:25 PM", timeRange(t("11:40"), t("12:25"), twelve))
        assertEquals("8:00–8:45", timeRange(t("08:00"), t("08:45"), timeFormatter(true, java.util.Locale.US)))
    }

    @Test
    fun newChildrenGetDifferentColors() {
        assertEquals("pink", defaultChildColor(emptyList()))
        assertEquals("red", defaultChildColor(listOf("pink")))
    }
}
