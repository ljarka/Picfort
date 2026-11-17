package com.github.ljarka.picfort.ui.schooljournal

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncLineAndReadStateTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val now = Instant.parse("2026-11-17T13:44:00Z") // 14:44 in Warsaw

    private fun status(last: String?, next: String? = null, lastRun: String = "ok") = JournalStatus(
        auth = "ok",
        lastSync = SyncRun(startedAt = "2026-11-17T13:30:00Z", status = lastRun),
        lastSuccessfulSyncAt = last,
        nextSyncAt = next,
    )

    private fun line(s: JournalStatus, is24Hour: Boolean = true) = syncLine(s, now, zone, Locale.ENGLISH, is24Hour)

    @Test
    fun freshSyncToday() {
        val l = line(status("2026-11-17T13:32:00Z", next = "2026-11-17T13:47:00Z"))
        assertEquals("Last successful sync: today 14:32 (12 min ago) · next 14:47", l.text)
        assertFalse(l.stale)
        assertFalse(l.failed)
    }

    @Test
    fun twelveHourClock() {
        val l = line(status("2026-11-17T13:32:00Z"), is24Hour = false)
        assertEquals("Last successful sync: today 2:32 PM (12 min ago)", l.text)
    }

    @Test
    fun olderThanAnHourIsStaleAndShowsTheDate() {
        val yesterday = line(status("2026-11-16T09:05:00Z"))
        assertEquals("Last successful sync: yesterday 10:05 (1 day ago)", yesterday.text)
        assertTrue(yesterday.stale)

        val older = line(status("2026-11-13T09:05:00Z"))
        assertEquals("Last successful sync: Fri 13 Nov, 10:05 (4 days ago)", older.text)

        assertTrue(line(status("2026-11-17T12:40:00Z")).stale) // 64 min
        assertFalse(line(status("2026-11-17T12:50:00Z")).stale) // 54 min
    }

    @Test
    fun aFailedLastRunIsFlagged() {
        assertTrue(line(status("2026-11-17T13:32:00Z", lastRun = "error")).failed)
        assertTrue(line(status("2026-11-17T13:32:00Z", lastRun = "auth_failed")).failed)
        assertFalse(line(status("2026-11-17T13:32:00Z", lastRun = "running")).failed)
    }

    @Test
    fun neverSynced() {
        val l = line(status(null, next = "2026-11-17T13:50:00Z", lastRun = "running"))
        assertEquals("No successful sync yet · next 14:50", l.text)
        assertTrue(l.stale)
    }

    @Test
    fun aPastNextSyncIsLeftOut() {
        assertEquals(
            "Last successful sync: today 14:32 (12 min ago)",
            line(status("2026-11-17T13:32:00Z", next = "2026-11-17T13:40:00Z")).text,
        )
    }

    @Test
    fun relativeAges() {
        assertEquals("just now", relative(Duration.ofSeconds(30)))
        assertEquals("59 min ago", relative(Duration.ofMinutes(59)))
        assertEquals("3 h ago", relative(Duration.ofMinutes(200)))
        assertEquals("just now", relative(Duration.ofMinutes(-5))) // phone clock behind the server
    }

    private fun msg(id: String, sent: String) = MessageSummary(id = id, sentAt = sent)

    @Test
    fun readStateAroundTheWatermark() {
        val start = ReadState.startingAt(Instant.parse("2026-11-17T10:00:00Z"), lastEventId = 340)
        assertEquals(340L, start.gradesSeenCursor)
        assertEquals(340L, start.notifyCursor)

        val old = msg("m1", "2026-11-17T09:00:00Z")
        val new = msg("m2", "2026-11-17T11:00:00Z")
        assertTrue("history counts as read", start.isRead(old))
        assertFalse("new mail is unread", start.isRead(new))

        val after = start.copy(readIds = setOf("m2"), unreadIds = setOf("m1"))
        assertTrue(after.isRead(new))
        assertFalse("marked unread on purpose", after.isRead(old))

        assertFalse("no date: unread", start.isRead(msg("m3", "")))
    }

    @Test
    fun shortHashIsStableAndShort() {
        assertEquals(shortHash("http://192.168.1.10:5100/"), shortHash("http://192.168.1.10:5100/"))
        assertEquals(12, shortHash("x").length)
        assertFalse(shortHash("a") == shortHash("b"))
    }
}
