package com.github.ljarka.picfort.ui.schooljournal

import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Read state per Immich user (spec §4.6) and the unread list built from it. */
class SchoolJournalMessagesTest {
    private val start = ReadState.startingAt(Instant.parse("2026-11-17T10:00:00Z"), lastEventId = 0)
    private val old = MessageSummary(id = "m1", childId = "101", sentAt = "2026-11-17T09:00:00Z")
    private val new = MessageSummary(id = "m2", childId = "101", sentAt = "2026-11-17T11:00:00Z")

    @Test
    fun markReadAndUnreadAroundTheWatermark() {
        assertFalse(start.isRead(new))
        val read = start.markRead(new.id)
        assertTrue(read.isRead(new))

        val newUnread = read.markUnread(new)
        assertFalse(newUnread.isRead(new))
        assertTrue("after the watermark: no need to remember", new.id !in newUnread.unreadIds)

        val oldUnread = start.markUnread(old)
        assertFalse(oldUnread.isRead(old))
        assertTrue(old.id in oldUnread.unreadIds)
        assertTrue(oldUnread.markRead(old.id).isRead(old))
    }

    @Test
    fun markAllReadForEveryChildMovesTheWatermark() {
        val messy = start.markUnread(old).markRead("m9")
        val all = messy.markAllRead(Instant.parse("2026-11-17T12:00:00.500Z"))
        assertEquals("2026-11-17T12:00:00Z", all.watermark)
        assertTrue(all.readIds.isEmpty() && all.unreadIds.isEmpty())
        assertTrue(all.isRead(old) && all.isRead(new))
    }

    @Test
    fun markAllReadForOneChildOnlyTouchesTheirMessages() {
        val other = MessageSummary(id = "m3", childId = "102", sentAt = "2026-11-17T11:30:00Z")
        val state = start.markUnread(old).markRead(listOf(old, new))
        assertTrue(state.isRead(old) && state.isRead(new))
        assertFalse(state.isRead(other))
    }

    @Test
    fun forgetDropsAGoneMessage() {
        val state = start.markUnread(old).markRead("m2").forget("m1").forget("m2")
        assertTrue(state.readIds.isEmpty() && state.unreadIds.isEmpty())
    }

    // --- loadUnread against a fake server ---

    private val requests = mutableListOf<Request>()

    /** Pages of `/messages?since=…` by cursor ("" = first). */
    private var pages: Map<String, Pair<List<String>, String?>> = mapOf("" to (emptyList<String>() to null))
    private var single: Map<String, String> = emptyMap()

    private val api = SchoolJournalApi(
        OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val r = chain.request()
                requests += r
                val path = r.url.encodedPath
                val (code, body) = when {
                    path == "/api/v1/messages" -> {
                        val (items, next) = pages.getValue(r.url.queryParameter("cursor").orEmpty())
                        200 to
                            """{"items":[${items.joinToString(",")}],"nextCursor":${next?.let {
                                "\"$it\""
                            } ?: "null"}}"""
                    }

                    path.startsWith("/api/v1/messages/") -> single[path.substringAfterLast('/')]?.let { 200 to it }
                        ?: (404 to """{"error":{"code":"NOT_FOUND","message":"x"}}""")

                    else -> error("unexpected $path")
                }
                Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
                    .body(body.toResponseBody()).build()
            },
        ).build(),
    )
    private val c = Connection("http://192.168.1.10:5100/".toHttpUrl(), "k")

    private fun json(id: String, sent: String, child: String = "101", deleted: Boolean = false) =
        """{"id":"$id","childId":"$child","sender":"S","subject":"$id","sentAt":"$sent","deletedAt":${if (deleted) "\"2026-11-17T12:00:00Z\"" else "null"}}"""

    private fun unread(state: ReadState, child: String? = null, includeDeleted: Boolean = false) =
        runBlocking { loadUnread(api, c, state, child, includeDeleted) }

    @Test
    fun unreadIsEverythingAfterTheWatermarkNotReadNewestFirst() {
        pages = mapOf(
            "" to (listOf(json("m2", "2026-11-17T11:00:00Z"), json("m3", "2026-11-17T11:30:00Z")) to "p2"),
            "p2" to (listOf(json("m4", "2026-11-17T10:30:00Z")) to null),
        )
        val result = unread(start.markRead("m3"))
        assertEquals(listOf("m2", "m4"), result.items.map { it.id })
        assertEquals("2026-11-17T10:00:00Z", requests.first().url.queryParameter("since"))
        assertEquals(2, requests.size)
    }

    @Test
    fun pagingStopsAtTheLimit() {
        pages = (0 until 20).associate { i -> (if (i == 0) "" else "p$i") to (emptyList<String>() to "p${i + 1}") }
        unread(start)
        assertEquals(UNREAD_MAX_PAGES, requests.size)
    }

    @Test
    fun messagesMarkedUnreadOnPurposeAreFetchedOneByOne() {
        single = mapOf("m1" to json("m1", "2026-11-17T09:00:00Z"))
        val state = start.markUnread(old).markUnread(MessageSummary(id = "m0", sentAt = "2026-11-16T09:00:00Z"))
        val result = unread(state)
        assertEquals(listOf("m1"), result.items.map { it.id })
        assertEquals(setOf("m0"), result.gone)
    }

    @Test
    fun childAndRemovedFilters() {
        pages = mapOf("" to (listOf(json("m5", "2026-11-17T11:00:00Z", deleted = true)) to null))
        single = mapOf("m1" to json("m1", "2026-11-17T09:00:00Z", child = "102"))
        val state = start.markUnread(old)
        assertEquals(emptyList<String>(), unread(state, child = "101").items.map { it.id })
        assertEquals("101", requests.first().url.queryParameter("child"))
        assertEquals(listOf("m5"), unread(start, includeDeleted = true).items.map { it.id })
        assertEquals("true", requests.last().url.queryParameter("includeDeleted"))
    }

    @Test
    fun shortDates() {
        val now = Instant.parse("2026-11-17T13:44:00Z")
        val zone = java.time.ZoneId.of("Europe/Warsaw")
        val en = java.util.Locale.ENGLISH
        assertEquals("14:32", shortWhen(Instant.parse("2026-11-17T13:32:00Z"), now, zone, en, true))
        assertEquals("16 Nov", shortWhen(Instant.parse("2026-11-16T13:32:00Z"), now, zone, en, true))
        assertEquals("16 Nov 2025", shortWhen(Instant.parse("2025-11-16T13:32:00Z"), now, zone, en, true))
    }
}
