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

/** The red dot rules (spec §6) against a fake school-server. */
class SchoolJournalNewsTest {
    private val requests = mutableListOf<Request>()

    /** Messages the server returns for `since`, and grade change events after any cursor. */
    private var messages: List<String> = emptyList()
    private var gradeEvents: List<Long> = emptyList()

    private val api = SchoolJournalApi(
        OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val r = chain.request()
                requests += r
                val body = when (r.url.encodedPath) {
                    "/api/v1/messages" -> """{"items":[${messages.joinToString(",")}],"nextCursor":null}"""

                    "/api/v1/changes" -> {
                        val after = r.url.queryParameter("after")!!.toLong()
                        val items = gradeEvents.filter { it > after }.map {
                            """{"id":$it,"childId":"101","entityType":"grade","entityId":"g_$it","changeType":"created","detectedAt":"2026-11-17T12:00:00Z"}"""
                        }
                        """{"items":[${items.joinToString(",")}],"nextCursor":${items.size},"hasMore":false}"""
                    }

                    else -> error("unexpected ${r.url}")
                }
                Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(body.toResponseBody()).build()
            },
        ).build(),
    )
    private val c = Connection("http://192.168.1.10:5100/".toHttpUrl(), "k")
    private val start = ReadState.startingAt(Instant.parse("2026-11-17T10:00:00Z"), lastEventId = 340)

    private fun message(id: String, sent: String, deleted: Boolean = false) =
        """{"id":"$id","childId":"101","sentAt":"$sent","deletedAt":${if (deleted) "\"2026-11-17T12:00:00Z\"" else "null"}}"""

    private fun news(state: ReadState, lastEventId: Long) =
        runBlocking { checkNews(api, c, state, JournalStatus(lastEventId = lastEventId)) }

    @Test
    fun nothingNew() {
        assertEquals(JournalNews(), news(start, lastEventId = 340))
        assertTrue(
            "no change request when lastEventId says nothing happened",
            requests.none {
                it.url.encodedPath ==
                    "/api/v1/changes"
            },
        )
        assertEquals("2026-11-17T10:00:00Z", requests.single().url.queryParameter("since"))
    }

    @Test
    fun aNewMessageOnly() {
        messages = listOf(message("m9", "2026-11-17T11:00:00Z"))
        assertEquals(JournalNews(unreadMessages = true), news(start, lastEventId = 340))
    }

    @Test
    fun aNewGradeOnly() {
        gradeEvents = listOf(341)
        assertEquals(JournalNews(unseenGrades = true), news(start, lastEventId = 341))
        assertEquals("340", requests.last().url.queryParameter("after"))
        assertEquals("grade", requests.last().url.queryParameter("type"))
    }

    @Test
    fun aNewMessageEventThatIsNotAGradeDoesNotLightGrades() {
        gradeEvents = emptyList() // the server filters by type=grade: only a message changed
        assertEquals(JournalNews(), news(start, lastEventId = 345))
    }

    @Test
    fun both() {
        messages = listOf(message("m9", "2026-11-17T11:00:00Z"))
        gradeEvents = listOf(341)
        assertTrue(news(start, lastEventId = 341).let { it.unreadMessages && it.unseenGrades })
    }

    @Test
    fun readOrRemovedMessagesDontCount() {
        messages = listOf(message("m9", "2026-11-17T11:00:00Z"), message("m10", "2026-11-17T11:30:00Z", deleted = true))
        assertFalse(news(start.copy(readIds = setOf("m9")), lastEventId = 340).unreadMessages)
    }

    @Test
    fun aMessageMarkedUnreadOnPurposeCountsWithoutAsking() {
        assertTrue(news(start.copy(unreadIds = setOf("m1")), lastEventId = 340).unreadMessages)
        assertTrue("short-circuit: no messages request", requests.none { it.url.encodedPath == "/api/v1/messages" })
    }

    @Test
    fun eachImmichUserHasTheirOwnDot() {
        messages = listOf(message("m9", "2026-11-17T11:00:00Z"))
        val parentA = start.copy(readIds = setOf("m9")) // A opened it on their phone
        val parentB = start
        assertFalse(news(parentA, lastEventId = 340).unreadMessages)
        assertTrue(news(parentB, lastEventId = 340).unreadMessages)
    }

    @Test
    fun seenGradesNeverMoveBackwards() {
        gradeEvents = listOf(341, 342)
        val seen = start.seenGradesUpTo(342)
        assertEquals(342L, seen.gradesSeenCursor)
        assertEquals(342L, seen.seenGradesUpTo(100).gradesSeenCursor)
        assertFalse(news(seen, lastEventId = 342).unseenGrades)
    }

    @Test
    fun aFreshStartHasNoHistory() {
        gradeEvents = (1L..340L).toList()
        messages = emptyList() // nothing sent after the watermark
        assertEquals(JournalNews(), news(start, lastEventId = 340))
        assertEquals(
            "2026-11-17T10:00:00Z",
            ReadState.startingAt(Instant.parse("2026-11-17T10:00:00.123456Z"), 0).watermark,
        )
    }
}
