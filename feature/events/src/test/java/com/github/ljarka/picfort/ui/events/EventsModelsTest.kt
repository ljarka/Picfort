package com.github.ljarka.picfort.ui.events

import com.github.ljarka.picfort.core.model.EventRepeat
import com.github.ljarka.picfort.core.model.FamilyEvent
import kotlinx.serialization.json.Json
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

    // --- T-037: repeat and birthdays ---

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun dtoShowsTheOccurrenceAndKeepsTheSeriesDatesForTheForm() {
        val dto = json.decodeFromString<EventDto>(
            """{"id": 7, "title": "Swimming", "start_date": "2026-09-04", "end_date": null,
               "start_time": "16:00", "repeat": "weekly", "repeat_until": "2027-06-25",
               "kind": null, "member_id": null, "occurrence_start": "2026-11-13", "occurrence_end": null,
               "turns": null, "created_by": "tata"}""",
        )
        val e = dto.toEvent()
        assertEquals(LocalDate.of(2026, 11, 13), e.startDate)
        assertNull(e.endDate)
        assertEquals(LocalDate.of(2026, 9, 4), e.seriesStartDate)
        assertEquals(EventRepeat.WEEKLY, e.repeat)
        assertEquals(LocalDate.of(2027, 6, 25), e.repeatUntil)
        assertFalse(e.isBirthday)
    }

    @Test
    fun dtoBirthdayAndOldEventWithoutOccurrence() {
        val birthday = json.decodeFromString<EventDto>(
            """{"id": 8, "title": "Ola's birthday", "start_date": "2017-11-16", "repeat": "yearly",
               "kind": "birthday", "member_id": "ola", "occurrence_start": "2026-11-16", "turns": 9}""",
        ).toEvent()
        assertEquals("ola", birthday.birthdayOf)
        assertEquals(9, birthday.turns)
        assertEquals(LocalDate.of(2026, 11, 16), birthday.startDate)

        val plain = json.decodeFromString<EventDto>(
            """{"id": 9, "title": "Trip", "start_date": "2026-11-20", "end_date": "2026-11-22"}""",
        ).toEvent()
        assertEquals(LocalDate.of(2026, 11, 20), plain.startDate)
        assertEquals(LocalDate.of(2026, 11, 22), plain.endDate)
        assertNull(plain.repeat)
    }

    @Test
    fun seriesIsGroupedAndLabelledByItsOccurrence() {
        val series = FamilyEvent(
            id = 1, title = "Swimming", startDate = today.plusDays(2), repeat = EventRepeat.WEEKLY,
            seriesStartDate = LocalDate.of(2026, 9, 4),
        )
        val sections = groupUpcomingEvents(listOf(series), today)
        assertEquals(listOf("NOVEMBER 2026"), sections.map { it.title })
        assertEquals("in 2 days", relativeEventLabel(series, today))
    }

    @Test
    fun birthdayTextFollowsTheMemberName() {
        val birthday = FamilyEvent(
            id = 1, title = "Ola's birthday", startDate = today, repeat = EventRepeat.YEARLY,
            birthdayOf = "ola", turns = 9,
        )
        assertEquals("Aleksandra's birthday", birthday.displayTitle { if (it == "ola") "Aleksandra" else null })
        assertEquals("Ola's birthday", birthday.displayTitle { null })
        assertEquals("turns 9", turnsLabel(birthday))
        assertNull(turnsLabel(birthday.copy(turns = null)))
        assertNull(turnsLabel(event(2, today).copy(turns = 3)))
    }

    @Test
    fun repeatSummaries() {
        val weekly = event(1, today).copy(repeat = EventRepeat.WEEKLY)
        assertNull(repeatSummary(event(2, today), today))
        assertEquals("Every week", repeatSummary(weekly, today))
        assertEquals("Every week · until 20 Dec", repeatSummary(weekly.copy(repeatUntil = LocalDate.of(2026, 12, 20)), today))
        assertEquals("Every week · until 25 Jun 2027", repeatSummary(weekly.copy(repeatUntil = LocalDate.of(2027, 6, 25)), today))
    }

    @Test
    fun patchComparesWithTheSeriesAndSendsRepeatChanges() {
        val series = FamilyEvent(
            id = 1, title = "Swimming", startDate = today.plusDays(2), repeat = EventRepeat.WEEKLY,
            repeatUntil = LocalDate.of(2027, 6, 25), seriesStartDate = LocalDate.of(2026, 9, 4),
        )
        val same = EventDraft(
            "Swimming", LocalDate.of(2026, 9, 4), repeat = EventRepeat.WEEKLY, repeatUntil = LocalDate.of(2027, 6, 25),
        )
        // The occurrence date differs from the series start, but the form edits the series: nothing changed.
        assertTrue(buildEventPatchJson(series, same).isEmpty())

        val stop = buildEventPatchJson(series, same.copy(repeat = null))
        assertEquals(JsonNull, stop["repeat"])
        assertEquals(JsonNull, stop["repeat_until"])

        val monthlyForever = buildEventPatchJson(series, same.copy(repeat = EventRepeat.MONTHLY, repeatUntil = null))
        assertEquals(JsonPrimitive("monthly"), monthlyForever["repeat"])
        assertEquals(JsonNull, monthlyForever["repeat_until"])

        // An end before the first occurrence is dropped rather than sent.
        assertNull(same.copy(repeatUntil = LocalDate.of(2026, 1, 1)).normalizedRepeatUntil)
    }
}
