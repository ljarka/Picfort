package com.github.ljarka.picfort.ui.events

import com.github.ljarka.picfort.core.model.FamilyEvent
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class EventsModelsTest {
    private val today = LocalDate.of(2026, 11, 11)

    private fun event(
        id: Long,
        start: LocalDate,
        end: LocalDate? = null,
        time: LocalTime? = null,
    ) = FamilyEvent(id = id, title = "e$id", startDate = start, endDate = end, startTime = time)

    @Test
    fun ongoingTripIsUnderNowAndOneDayTodayIsNot() {
        val trip = event(1, today.minusDays(1), today.plusDays(1))
        val todayParty = event(2, today)
        val endedYesterday = event(3, today.minusDays(3), today.minusDays(1))
        val endsToday = event(4, today.minusDays(2), today)

        val sections = groupUpcomingEvents(listOf(todayParty, endedYesterday, trip, endsToday), today)

        assertEquals("NOW", sections[0].title)
        assertTrue(sections[0].isNow)
        assertEquals(listOf(4L, 1L), sections[0].events.map { it.id })
        assertEquals("NOVEMBER 2026", sections[1].title)
        assertEquals(listOf(2L), sections[1].events.map { it.id })
        assertEquals(2, sections.size)
    }

    @Test
    fun upcomingOrdersByDateThenUntimedFirstThenId() {
        val day = today.plusDays(5)
        val events = listOf(
            event(5, day, time = LocalTime.of(18, 0)),
            event(3, day, time = LocalTime.of(9, 30)),
            event(9, day),
            event(2, day),
            event(1, today.plusDays(40)),
        )

        val sections = groupUpcomingEvents(events, today)

        assertEquals(listOf("NOVEMBER 2026", "DECEMBER 2026"), sections.map { it.title })
        assertEquals(listOf(2L, 9L, 3L, 5L), sections[0].events.map { it.id })
        assertEquals(listOf(1L), sections[1].events.map { it.id })
    }

    @Test
    fun pastIsLatestEndFirstWithMonthHeaders() {
        val events = listOf(
            event(1, LocalDate.of(2026, 9, 1)),
            event(2, LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 2)),
            event(3, LocalDate.of(2026, 10, 5)),
            event(4, today),
        )

        val sections = groupPastEvents(events, today)

        assertEquals(listOf("OCTOBER 2026", "SEPTEMBER 2026"), sections.map { it.title })
        assertEquals(listOf(2L, 3L), sections[0].events.map { it.id })
        assertEquals(listOf(1L), sections[1].events.map { it.id })
    }

    @Test
    fun relativeLabels() {
        assertEquals("Today", relativeEventLabel(event(1, today), today))
        assertEquals("Tomorrow", relativeEventLabel(event(1, today.plusDays(1)), today))
        assertEquals("in 43 days", relativeEventLabel(event(1, LocalDate.of(2026, 12, 24)), today))
        assertEquals("in 60 days", relativeEventLabel(event(1, today.plusDays(60)), today))
        assertNull(relativeEventLabel(event(1, today.plusDays(61)), today))
        assertNull(relativeEventLabel(event(1, today.minusDays(1), today.plusDays(1)), today))
        assertNull(relativeEventLabel(event(1, today.minusDays(1)), today))
    }

    @Test
    fun dateRanges() {
        val en = Locale.ENGLISH
        assertEquals("24 Dec", formatEventDateRange(LocalDate.of(2026, 12, 24), null, today, en))
        assertEquals("24 Dec", formatEventDateRange(LocalDate.of(2026, 12, 24), LocalDate.of(2026, 12, 24), today, en))
        assertEquals("10–14 Nov", formatEventDateRange(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 14), today, en))
        assertEquals("3 Jan 2027", formatEventDateRange(LocalDate.of(2027, 1, 3), null, today, en))
        assertEquals("3–10 Jan 2027", formatEventDateRange(LocalDate.of(2027, 1, 3), LocalDate.of(2027, 1, 10), today, en))
        assertEquals("1 Nov – 3 Dec", formatEventDateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 3), today, en))
        assertEquals(
            "28 Dec 2026 – 3 Jan 2027",
            formatEventDateRange(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3), today, en),
        )
    }

    @Test
    fun pickerMillisAreReadInUtcWhateverTheZone() {
        // What the M3 DatePicker returns for 11 Nov 2026: midnight UTC.
        val millis = Instant.parse("2026-11-11T00:00:00Z").toEpochMilli()
        // Read in a zone west of UTC this instant is still 10 Nov; the picked date must not shift.
        assertEquals(LocalDate.of(2026, 11, 10), Instant.ofEpochMilli(millis).atZone(ZoneId.of("America/New_York")).toLocalDate())
        assertEquals(today, pickerMillisToLocalDate(millis))
        assertEquals(millis, localDateToPickerMillis(today))
        assertEquals(today, pickerMillisToLocalDate(localDateToPickerMillis(today)))
    }

    @Test
    fun patchContainsOnlyChangedFieldsAndNullForCleared() {
        val original = FamilyEvent(
            id = 7,
            title = "Trip",
            startDate = today,
            endDate = today.plusDays(2),
            startTime = LocalTime.of(9, 0),
            location = "Giżycko",
            note = "Bring boots",
        )
        val unchanged = EventDraft(
            title = " Trip ",
            startDate = today,
            endDate = today.plusDays(2),
            startTime = LocalTime.of(9, 0),
            location = "Giżycko",
            note = "Bring boots",
        )
        assertTrue(buildEventPatchJson(original, unchanged).isEmpty())

        val patch = buildEventPatchJson(
            original,
            unchanged.copy(endDate = null, startTime = LocalTime.of(18, 5), location = "  "),
        )
        assertEquals(setOf("end_date", "start_time", "location"), patch.keys)
        assertEquals(JsonNull, patch["end_date"])
        assertEquals(JsonPrimitive("18:05"), patch["start_time"])
        assertEquals(JsonNull, patch["location"])
    }

    @Test
    fun endDateEqualToStartIsOneDay() {
        val e = event(1, today, today)
        assertFalse(e.isMultiDay)
        assertNull(EventDraft("x", today, today).normalizedEndDate)
    }
}
