package com.github.ljarka.picfort.ui.school

import com.github.ljarka.picfort.core.network.PicfortServerException
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An in-memory school server; the "raw JSON" is the children's names, enough for the copy. */
private class FakeSchoolBackend : SchoolBackend {
    val children = mutableMapOf<Long, Child>()
    var down = false
    var nextId = 1L

    private fun check() {
        if (down) throw PicfortServerException.Unreachable("server")
    }

    private fun child(id: Long) = children[id] ?: throw PicfortServerException.Gone("gone")

    override suspend fun children(): Pair<List<Child>, String> {
        check()
        val list = children.values.sortedBy { it.name }
        return list to list.joinToString(",") { "${it.id}:${it.name}" }
    }

    override fun decode(raw: String): List<Child> = raw.split(",").filter { it.isNotEmpty() }.map {
        val (id, name) = it.split(":")
        Child(id.toLong(), name, "pink")
    }

    override suspend fun addChild(name: String, color: String, memberId: String?): Child {
        check()
        return Child(nextId++, name, color, memberId = memberId).also { children[it.id] = it }
    }

    override suspend fun deleteChild(id: Long) {
        check()
        child(id)
        children.remove(id)
    }

    override suspend fun setPeriods(childId: Long, periods: List<Period>): Child {
        check()
        return child(childId).copy(periods = periods).also { children[childId] = it }
    }

    override suspend fun copyPeriods(childId: Long, fromChild: Long) = setPeriods(childId, child(fromChild).periods)

    override suspend fun addEntry(childId: Long, draft: EntryDraft) {
        check()
        val c = child(childId)
        val entry = SchoolEntry(nextId++, draft.kind, draft.weekday, draft.start!!, draft.end!!, draft.title)
        children[childId] = c.copy(entries = c.entries + entry)
    }

    override suspend fun updateEntry(childId: Long, entryId: Long, draft: EntryDraft) {
        check()
        val c = child(childId)
        if (c.entries.none { it.id == entryId }) throw PicfortServerException.Gone("gone")
        children[childId] =
            c.copy(entries = c.entries.map { if (it.id == entryId) it.copy(title = draft.title) else it })
    }

    override suspend fun deleteEntry(childId: Long, entryId: Long) {
        check()
        val c = child(childId)
        children[childId] = c.copy(entries = c.entries.filter { it.id != entryId })
    }

    override suspend fun copyDay(childId: Long, from: DayOfWeek, to: DayOfWeek): Child {
        check()
        val c = child(childId)
        val copies = c.on(from).filter { it.isLesson }.map { it.copy(id = nextId++, weekday = to) }
        return c.copy(entries = c.entries.filter { it.weekday != to || !it.isLesson } + copies)
            .also { children[childId] = it }
    }
}

private class MemoryCopy : SchoolOfflineCopy {
    var saved: Pair<Long, String>? = null
    override fun save(raw: String) {
        saved = 1_000L to raw
    }

    override fun load() = saved
}

class SchoolRepositoryTest {
    private val backend = FakeSchoolBackend()
    private val copy = MemoryCopy()
    private val repository = SchoolRepository(backend, copy)

    private fun draft(title: String, day: DayOfWeek = DayOfWeek.MONDAY) =
        EntryDraft(EntryKind.LESSON, day, title, start = LocalTime.of(8, 0), end = LocalTime.of(8, 45))

    @Test
    fun loadsSavesTheCopyAndFallsBackToItOffline() = runBlocking {
        backend.addChild("Staś", "blue", null)
        backend.addChild("Ola", "pink", null)
        assertNull(repository.refresh())
        assertEquals(listOf("Ola", "Staś"), repository.children.value.map { it.name })
        assertNull(repository.offlineSince.value)

        backend.down = true
        val error = repository.refresh()
        assertTrue(error is PicfortServerException.Unreachable)
        assertEquals(1_000L, repository.offlineSince.value)
        assertEquals(listOf("Ola", "Staś"), repository.children.value.map { it.name })

        backend.down = false
        repository.refresh()
        assertNull(repository.offlineSince.value)
    }

    @Test
    fun offlineAtStartShowsTheCopyAfterARestart() = runBlocking {
        copy.save("1:Ola")
        backend.down = true
        val fresh = SchoolRepository(backend, copy)
        fresh.refresh()
        assertEquals(listOf("Ola"), fresh.children.value.map { it.name })
        assertTrue(fresh.hasData)
        assertNotNull(fresh.offlineSince.value)
    }

    @Test
    fun changesReplaceTheChildAndEntriesReload() = runBlocking {
        val ola = repository.addChild(" Ola ", "pink")
        assertEquals("Ola", repository.children.value.single().name)
        repository.setPeriods(ola.id, fillBells(LocalTime.of(8, 0), 45, 10, 2))
        assertEquals(2, repository.children.value.single().periods.size)

        repository.addEntry(ola.id, draft("Maths"))
        repository.copyDay(ola.id, DayOfWeek.MONDAY, listOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY))
        assertEquals(3, repository.children.value.single().entries.size)
    }

    @Test
    fun aChangeReachesTheOfflineCopy() = runBlocking {
        repository.addChild("Ola", "pink")
        repository.addChild("Staś", "blue") // answered with the child, then reloaded: the copy has both
        backend.down = true
        val restarted = SchoolRepository(backend, copy)
        restarted.refresh()
        assertEquals(listOf("Ola", "Staś"), restarted.children.value.map { it.name })
    }

    @Test
    fun somethingDeletedElsewhereReloadsQuietly() = runBlocking {
        val ola = repository.addChild("Ola", "pink")
        repository.addChild("Staś", "blue")
        backend.children.remove(ola.id) // another phone deleted Ola
        repository.addEntry(ola.id, draft("Maths")) // no exception
        assertEquals(listOf("Staś"), repository.children.value.map { it.name })
    }

    @Test(expected = PicfortServerException.Unreachable::class)
    fun otherErrorsReachTheCaller(): Unit = runBlocking {
        val ola = repository.addChild("Ola", "pink")
        backend.down = true
        repository.setPeriods(ola.id, emptyList())
    }
}
