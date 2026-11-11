package com.github.ljarka.picfort.ui.wall

import com.github.ljarka.picfort.ui.events.FamilyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TodayPanelTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val noon = LocalTime.NOON

    private fun event(
        id: Long,
        start: LocalDate = today,
        end: LocalDate? = null,
        time: LocalTime? = null,
        created: String = "2026-11-01T10:00:00Z",
    ) = FamilyEvent(id = id, title = "e$id", startDate = start, endDate = end, startTime = time, created = created)

    private fun panel(vararg events: FamilyEvent, now: LocalTime = noon) = todayPanel(events.toList(), today, now)

    @Test
    fun `empty input is date only`() {
        val p = panel()
        assertEquals(today, p.date)
        assertNull(p.event)
        assertNull(p.next)
        assertEquals(0, p.moreToday)
    }

    @Test
    fun `one-day event without a time`() {
        val p = panel(event(1))
        assertEquals(1L, p.event?.id)
        assertNull(p.dayOfTrip)
        assertEquals(0, p.moreToday)
    }

    @Test
    fun `timed event that has not started wins over ongoing trip and untimed`() {
        val trip = event(1, today.minusDays(1), today.plusDays(3))
        val timed = event(2, time = LocalTime.of(18, 0))
        assertEquals(2L, panel(trip, event(3), timed).event?.id)
        assertEquals(2, panel(trip, event(3), timed).moreToday)
    }

    @Test
    fun `earliest not-yet-started timed event, else the latest`() {
        val a = event(1, time = LocalTime.of(9, 0))
        val b = event(2, time = LocalTime.of(15, 0))
        val c = event(3, time = LocalTime.of(18, 0))
        assertEquals(2L, panel(c, b, a, now = LocalTime.of(10, 0)).event?.id)
        assertEquals(3L, panel(a, b, c, now = LocalTime.of(19, 0)).event?.id)
        assertEquals(1L, panel(a, b, c, now = LocalTime.of(8, 0)).event?.id)
    }

    @Test
    fun `ongoing multi-day beats a one-day event without a time`() {
        val trip = event(5, today.minusDays(1), today.plusDays(1))
        assertEquals(5L, panel(event(1), trip).event?.id)
    }

    @Test
    fun `start time counts only on the start day`() {
        val trip = event(1, today.minusDays(1), today.plusDays(1), time = LocalTime.of(23, 0))
        val timed = event(2, time = LocalTime.of(8, 0))
        assertEquals(2L, panel(trip, timed, now = LocalTime.of(7, 0)).event?.id)
        // The trip's 23:00 start was yesterday, so it is an ordinary ongoing event: the timed one still wins.
        assertEquals(2L, panel(trip, timed, now = LocalTime.of(20, 0)).event?.id)
        assertEquals(1L, panel(trip).event?.id)
    }

    @Test
    fun `ties go to earliest created, then lowest id`() {
        val older = event(9, created = "2026-10-01T00:00:00Z")
        val newer = event(1, created = "2026-10-02T00:00:00Z")
        assertEquals(9L, panel(newer, older).event?.id)
        val a = event(3)
        val b = event(2)
        assertEquals(2L, panel(a, b).event?.id)
    }

    @Test
    fun `day of trip counts across months`() {
        val trip = event(1, LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 13))
        assertEquals(13 to 15, panel(trip).dayOfTrip)
        val first = event(2, today, today.plusDays(4))
        assertEquals(1 to 5, panel(first).dayOfTrip)
        val last = event(3, today.minusDays(4), today)
        assertEquals(5 to 5, panel(last).dayOfTrip)
    }

    @Test
    fun `next event within 30 days`() {
        val p = panel(event(2, today.plusDays(30)), event(1, today.plusDays(12)))
        assertNull(p.event)
        assertEquals(1L, p.next?.id)
        assertEquals(12, p.nextInDays)
        assertEquals("Next: e1 · in 12 days", p.nextLine())
        assertEquals("Next: e3 · tomorrow", panel(event(3, today.plusDays(1))).nextLine())
        assertEquals(30, panel(event(2, today.plusDays(30))).nextInDays)
    }

    @Test
    fun `next event further than 30 days is date only`() {
        val p = panel(event(1, today.plusDays(31)))
        assertNull(p.next)
        assertNull(p.nextInDays)
        assertNull(p.nextLine())
    }

    @Test
    fun `time and day text`() {
        assertEquals("18:00", panel(event(1, time = LocalTime.of(18, 0))).eventTimeAndDay())
        assertEquals("Day 2 of 5", panel(event(1, today.minusDays(1), today.plusDays(3))).eventTimeAndDay())
        assertEquals(
            "18:00 · Day 1 of 3",
            panel(event(1, today, today.plusDays(2), LocalTime.of(18, 0))).eventTimeAndDay(),
        )
        assertNull(panel(event(1)).eventTimeAndDay())
    }
}
