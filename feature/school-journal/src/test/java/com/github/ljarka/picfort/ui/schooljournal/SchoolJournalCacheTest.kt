package com.github.ljarka.picfort.ui.schooljournal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The offline copy (spec §7) survives a round trip and keeps views apart. */
class SchoolJournalCacheTest {
    @Test
    fun roundTrip() {
        val copy = JournalCache(
            savedAt = "2026-11-17T13:00:00Z",
            status = JournalStatus(auth = "ok", lastSuccessfulSyncAt = "2026-11-17T12:50:00Z", lastEventId = 7),
            children = listOf(ChildAccount("101", "ola.k", "Ola")),
            messages = mapOf(
                messagesKey(null, false) to
                    listOf(MessageSummary("m1", "101", "Teacher", "Trip", "2026-11-16T07:30:00Z")),
            ),
            grades = mapOf(
                gradesKey("101", 1) to CachedGrades(
                    1,
                    listOf(
                        CachedSection(
                            "101",
                            null,
                            listOf(SubjectSummary("Mathematics", 4.5, listOf(Grade(id = "g1", value = "5")))),
                        ),
                    ),
                ),
            ),
        )
        val text = journalJson.encodeToString(JournalCache.serializer(), copy)
        assertEquals(copy, journalJson.decodeFromString(JournalCache.serializer(), text))
    }

    @Test
    fun anOldOrPartialFileStillReads() {
        val copy = journalJson.decodeFromString(
            JournalCache.serializer(),
            """{"savedAt":"x","status":null,"later":1}""",
        )
        assertEquals(emptyList<ChildAccount>(), copy.children)
    }

    @Test
    fun keysKeepViewsApart() {
        assertNotEquals(messagesKey(null, false), messagesKey(null, true))
        assertNotEquals(messagesKey("101", false), messagesKey(null, false))
        assertNotEquals(gradesKey("101", null), gradesKey("101", 1)) // whole year vs semester 1
        assertNotEquals(gradesKey("101", 1), gradesKey("101", 2))
        assertNotEquals(gradesKey("101", 1), gradesKey(null, 1))
        assertEquals("all|year", gradesKey(null, null))
        assertEquals("all|last", lastGradesKey(null))
    }

    @Test
    fun sectionsConvert() {
        val section = GradeSection("101", "Ola", listOf(SubjectSummary("Art", null)))
        assertEquals(section, section.toCached().toSection())
    }
}
