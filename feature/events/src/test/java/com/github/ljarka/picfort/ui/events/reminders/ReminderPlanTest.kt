package com.github.ljarka.picfort.ui.events.reminders

import com.github.ljarka.picfort.core.model.EventRepeat
import com.github.ljarka.picfort.core.model.FamilyEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPlanTest {
    private val now = LocalDateTime.of(2026, 11, 15, 12, 0)
    private val names = mapOf("ola" to "Ola")

    private fun plan(
        events: List<FamilyEvent>,
        defaults: ReminderDefaults = ReminderDefaults(),
        choices: Map<Long, ReminderChoice> = emptyMap(),
        delivered: Set<String> = emptySet(),
        armed: Set<String> = emptySet(),
        at: LocalDateTime = now,
    ) = planReminders(events, defaults, choices, delivered, armed, at, names::get, Locale.ENGLISH)

    private val trip = FamilyEvent(1, "School trip", LocalDate.of(2026, 11, 20), location = "Kraków")
    private val dentist = FamilyEvent(2, "Dentist", LocalDate.of(2026, 11, 16), startTime = LocalTime.of(17, 30))
    private val birthday = FamilyEvent(
        3,
        "Birthday",
        LocalDate.of(2026, 11, 18),
        birthdayOf = "ola",
        turns = 9,
        repeat = EventRepeat.YEARLY,
    )

    @Test
    fun `defaults - the day before for all-day events, an hour before timed ones, the day itself for birthdays`() {
        val byId = plan(listOf(trip, dentist, birthday)).associateBy { it.eventId }
        assertEquals("2026-11-19T19:00", byId.getValue(1).trigger)
        assertEquals("Tomorrow · Kraków", byId.getValue(1).text)
        assertEquals("2026-11-16T16:30", byId.getValue(2).trigger)
        assertEquals("Today at 17:30", byId.getValue(2).text)
        assertEquals("2026-11-18T09:00", byId.getValue(3).trigger)
        assertEquals("Ola's birthday", byId.getValue(3).title)
        assertEquals("Ola turns 9 today", byId.getValue(3).text)
    }

    @Test
    fun `an event's own choice wins over the default, and Off silences it`() {
        val byId = plan(
            listOf(trip, birthday),
            choices = mapOf(1L to ReminderChoice.Off, 3L to ReminderChoice.Preset(ReminderPreset.DAYS_3)),
        ).associateBy { it.eventId }
        assertNull(byId[1])
        assertEquals("2026-11-15T18:00", byId.getValue(3).trigger)
        assertEquals("Ola turns 9 in 3 days, Wed 18 Nov", byId.getValue(3).text)
    }

    @Test
    fun `a default turned off and minute presets on an all-day event give no reminder`() {
        assertTrue(plan(listOf(trip), defaults = ReminderDefaults(allDay = null)).isEmpty())
        assertTrue(plan(listOf(trip), choices = mapOf(1L to ReminderChoice.Preset(ReminderPreset.HOUR_1))).isEmpty())
    }

    @Test
    fun `a missed reminder still comes before the event, a delivered one never again`() {
        val key = "1@2026-11-20@2026-11-19T19:00"
        // The day-before reminder (19:00 yesterday) was armed, then missed while the phone was off.
        val late = plan(listOf(trip), armed = setOf(key), at = LocalDateTime.of(2026, 11, 20, 6, 0))
        assertEquals(1, late.size)
        // Never armed (the event was added after 19:00): no reminder out of the blue.
        assertTrue(plan(listOf(trip), at = LocalDateTime.of(2026, 11, 20, 6, 0)).isEmpty())
        // More than 12 hours late: dropped.
        assertTrue(plan(listOf(trip), armed = setOf(key), at = LocalDateTime.of(2026, 11, 20, 8, 0)).isEmpty())
        // Already shown; another preset afterwards is a new reminder.
        assertTrue(plan(listOf(trip), delivered = setOf(key)).isEmpty())
        val threeDays = mapOf(1L to ReminderChoice.Preset(ReminderPreset.DAYS_3))
        assertEquals(1, plan(listOf(trip), choices = threeDays, delivered = setOf(key)).size)
        // A timed event that has started: nothing.
        assertTrue(plan(listOf(dentist), at = LocalDateTime.of(2026, 11, 16, 17, 31)).isEmpty())
    }

    @Test
    fun `the next occurrence of a series gets its own reminder`() {
        val nextYear = birthday.copy(startDate = LocalDate.of(2027, 11, 18), turns = 10)
        val planned = plan(listOf(nextYear), delivered = setOf("3@2026-11-18@2026-11-18T09:00")).single()
        assertEquals("3@2027-11-18@2027-11-18T09:00", planned.key)
        assertEquals("Ola turns 10 today", planned.text)
    }

    @Test
    fun `stored choices round trip`() {
        listOf(ReminderChoice.Off, ReminderChoice.Preset(ReminderPreset.WEEK)).forEach {
            assertEquals(it, ReminderChoice.fromStored(it.stored()))
        }
        assertEquals(ReminderChoice.Default, ReminderChoice.fromStored("gone"))
    }
}
