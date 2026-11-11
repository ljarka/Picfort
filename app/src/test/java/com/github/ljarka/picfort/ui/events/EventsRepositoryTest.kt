package com.github.ljarka.picfort.ui.events

import com.github.ljarka.picfort.network.PicfortServerException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** An in-memory events server applying the T-022 upcoming/past split. */
private class FakeEventsBackend : EventsBackend {
    val events = mutableMapOf<Long, FamilyEvent>()
    var nextId = 1L
    var down = false

    private fun check() {
        if (down) throw PicfortServerException.Unreachable("server")
    }

    override suspend fun upcoming(after: LocalDate): List<FamilyEvent> {
        check()
        return events.values.filter { !it.effectiveEndDate.isBefore(after) }.sortedBy { it.startDate }
    }

    override suspend fun past(before: LocalDate, limit: Int, offset: Int): List<FamilyEvent> {
        check()
        return events.values.filter { it.effectiveEndDate.isBefore(before) }
            .sortedWith(compareByDescending<FamilyEvent> { it.effectiveEndDate }.thenByDescending { it.id })
            .drop(offset).take(limit)
    }

    override suspend fun event(id: Long): FamilyEvent {
        check()
        return events[id] ?: throw PicfortServerException.Gone("This event no longer exists")
    }

    override suspend fun create(draft: EventDraft): FamilyEvent {
        check()
        return FamilyEvent(nextId++, draft.normalizedTitle, draft.startDate, draft.normalizedEndDate, createdBy = "me")
            .also { events[it.id] = it }
    }

    override suspend fun update(original: FamilyEvent, draft: EventDraft): FamilyEvent {
        check()
        if (original.id !in events) throw PicfortServerException.Gone("This event no longer exists")
        return original.copy(title = draft.normalizedTitle, startDate = draft.startDate, endDate = draft.normalizedEndDate)
            .also { events[it.id] = it }
    }

    override suspend fun delete(id: Long) {
        check()
        events.remove(id) ?: throw PicfortServerException.Gone("This event no longer exists")
    }

    fun add(start: LocalDate, end: LocalDate? = null) =
        FamilyEvent(nextId++, "e", start, end, createdBy = "other").also { events[it.id] = it }
}

class EventsRepositoryTest {
    private var today = LocalDate.of(2026, 11, 11)
    private val backend = FakeEventsBackend()
    private val repository = EventsRepository(backend) { today }

    @Test
    fun eventEndingTodayIsUpcomingAndMovesToPastAfterMidnight() = runBlocking {
        val endsToday = backend.add(today.minusDays(2), today)
        val oneDayTomorrow = backend.add(today.plusDays(1))

        repository.refreshActive()
        assertEquals(listOf(endsToday.id, oneDayTomorrow.id), repository.upcoming.value.map { it.id })

        today = today.plusDays(1)
        repository.rollOverMidnight()
        assertEquals(listOf(oneDayTomorrow.id), repository.upcoming.value.map { it.id })
        assertEquals(listOf(endsToday.id), repository.past.value.map { it.id })
        assertEquals(today, repository.today.value)

        repository.refreshActive()
        assertEquals(listOf(oneDayTomorrow.id), repository.upcoming.value.map { it.id })
        assertEquals(listOf(endsToday.id), repository.past.value.map { it.id })
    }

    @Test
    fun pastLoadsMoreInPages() = runBlocking {
        repeat(120) { backend.add(today.minusDays(it + 1L)) }
        repository.setSegment(EventsSegment.PAST)

        repository.refreshActive()
        assertEquals(PAST_PAGE_SIZE, repository.past.value.size)
        assertTrue(repository.hasMorePast.value)

        repository.loadMorePast()
        repository.loadMorePast()
        assertEquals(120, repository.past.value.size)
        assertFalse(repository.hasMorePast.value)
        assertEquals(today.minusDays(1), repository.past.value.first().startDate)
        assertEquals(120, repository.past.value.map { it.id }.toSet().size)
    }

    @Test
    fun saveAndDeleteReloadTheList() = runBlocking {
        repository.refreshActive()
        val created = repository.create(EventDraft("Trip", today.plusDays(3)))
        assertEquals(listOf(created.id), repository.upcoming.value.map { it.id })

        repository.update(created, EventDraft("Trip", today.minusDays(5)))
        assertTrue(repository.upcoming.value.isEmpty())
        assertEquals(listOf(created.id), repository.past.value.map { it.id })

        repository.delete(created.id)
        assertTrue(repository.past.value.isEmpty())
    }

    @Test
    fun offlineKeepsLoadedEventsAndReportsTheError() = runBlocking {
        backend.add(today.plusDays(2))
        repository.refreshActive()
        backend.down = true

        val error = repository.refreshActive()

        assertTrue(error is PicfortServerException.Unreachable)
        assertEquals(1, repository.upcoming.value.size)
    }

    @Test
    fun eventDeletedElsewhereIsGone() = runBlocking {
        val e = backend.add(today.plusDays(2))
        repository.refreshActive()
        backend.events.clear()

        val thrown = runCatching { repository.fetchEvent(e.id) }.exceptionOrNull()

        assertTrue(thrown is PicfortServerException.Gone)
    }
}
