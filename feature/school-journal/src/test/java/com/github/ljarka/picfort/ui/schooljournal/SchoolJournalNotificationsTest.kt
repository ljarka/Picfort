package com.github.ljarka.picfort.ui.schooljournal

import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Background notification filter and cursor handling (T-082, spec §4.7). */
class SchoolJournalNotificationsTest {
    private val readState = ReadState.startingAt(Instant.parse("2026-11-17T10:00:00Z"), lastEventId = 10)
    private val children = mapOf("101" to "Ola", "102" to "Kuba")

    private fun gradeEvent(id: Long, changeType: String, grade: Grade, diff: Map<String, JsonArray>? = null) =
        ChangeEvent(
            id = id,
            childId = grade.childId,
            entityType = "grade",
            entityId = grade.id,
            changeType = changeType,
            detectedAt = "2026-11-17T11:00:00Z",
            entity = journalJson.encodeToJsonElement(grade).jsonObject,
            diff = diff,
        )

    private fun messageEvent(id: Long, changeType: String, message: MessageSummary) = ChangeEvent(
        id = id,
        childId = message.childId,
        entityType = "message",
        entityId = message.id,
        changeType = changeType,
        detectedAt = "2026-11-17T11:00:00Z",
        entity = journalJson.encodeToJsonElement(message).jsonObject,
    )

    @Test
    fun filtersGradeCreatedUpdatedWithValueAndUnreadMessageCreated() {
        val events = listOf(
            gradeEvent(
                11,
                "created",
                Grade(id = "g1", childId = "101", subject = "Mathematics", value = "5", category = "Test"),
            ),
            gradeEvent(
                12,
                "updated",
                Grade(id = "g2", childId = "101", subject = "English", value = "4+", category = "Quiz"),
                diff = mapOf("value" to JsonArray(listOf(JsonPrimitive("4"), JsonPrimitive("4+")))),
            ),
            // Updated without a value change: ignored.
            gradeEvent(
                13,
                "updated",
                Grade(id = "g3", childId = "101", subject = "History", value = "3", category = "Essay"),
                diff = mapOf("comment" to JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b")))),
            ),
            // Deleted grade: ignored.
            gradeEvent(
                14,
                "deleted",
                Grade(id = "g4", childId = "101", subject = "Art", value = "5"),
            ),
            // Unread message after watermark: notified.
            messageEvent(
                15,
                "created",
                MessageSummary(
                    id = "m1",
                    childId = "102",
                    sender = "Coach",
                    subject = "Match on Saturday",
                    sentAt = "2026-11-17T11:00:00Z",
                ),
            ),
            // Already read on this phone: skipped.
            messageEvent(
                16,
                "created",
                MessageSummary(
                    id = "m2",
                    childId = "102",
                    sender = "Coach",
                    subject = "Old read",
                    sentAt = "2026-11-17T11:00:00Z",
                ),
            ),
            // Deleted message: skipped.
            messageEvent(
                17,
                "deleted",
                MessageSummary(
                    id = "m3",
                    childId = "102",
                    sender = "Coach",
                    subject = "Deleted",
                    sentAt = "2026-11-17T11:00:00Z",
                ),
            ),
            // Announcement without sender: "Announcement · Kuba", "Notice board: Open day".
            messageEvent(
                18,
                "created",
                MessageSummary(
                    id = "a1",
                    childId = "102",
                    sender = "",
                    subject = "Open day",
                    sentAt = "2026-11-17T11:00:00Z",
                    type = MESSAGE_TYPE_ANNOUNCEMENT,
                ),
            ),
        )

        val notifications = planNotifications(events, readState.markRead("m2"), children)
        assertEquals(
            listOf(
                JournalNotification(
                    key = "grade:g1",
                    childId = "101",
                    title = "New grade · Ola",
                    text = "Mathematics 5 · Test",
                    target = "grades",
                ),
                JournalNotification(
                    key = "grade:g2",
                    childId = "101",
                    title = "Grade changed · Ola",
                    text = "English 4 → 4+",
                    target = "grades",
                ),
                JournalNotification(
                    key = "message:m1",
                    childId = "102",
                    title = "Message · Kuba",
                    text = "Coach: Match on Saturday",
                    target = "messages:m1",
                ),
                JournalNotification(
                    key = "message:a1",
                    childId = "102",
                    title = "Announcement · Kuba",
                    text = "Notice board: Open day",
                    target = "messages:a1",
                ),
            ),
            notifications,
        )
    }

    @Test
    fun collapsesMoreThanThreeForAChildIntoASummary() {
        val events = listOf(
            gradeEvent(1, "created", Grade(id = "g1", childId = "101", subject = "Math", value = "5")),
            gradeEvent(2, "created", Grade(id = "g2", childId = "101", subject = "Art", value = "6")),
            messageEvent(
                3,
                "created",
                MessageSummary(
                    id = "m1",
                    childId = "101",
                    sender = "S",
                    subject = "One",
                    sentAt = "2026-11-17T11:00:00Z",
                ),
            ),
            messageEvent(
                4,
                "created",
                MessageSummary(
                    id = "m2",
                    childId = "101",
                    sender = "S",
                    subject = "Two",
                    sentAt = "2026-11-17T11:00:00Z",
                ),
            ),
            // Second child has only 1 item -> stays separate.
            gradeEvent(5, "created", Grade(id = "g3", childId = "102", subject = "PE", value = "5")),
        )
        val notifications = planNotifications(events, readState, children)
        assertEquals(
            listOf(
                JournalNotification(
                    key = "summary:101",
                    childId = "101",
                    title = "School journal · Ola",
                    text = "Ola: 2 new grades, 2 messages",
                    target = "messages",
                ),
                JournalNotification(
                    key = "grade:g3",
                    childId = "102",
                    title = "New grade · Kuba",
                    text = "PE 5",
                    target = "grades",
                ),
            ),
            notifications,
        )
    }

    // --- checkNotifications cursor & per-Immich-user behavior ---

    private var responder: (Request) -> Pair<Int, String> = { 200 to "{}" }
    private val api = SchoolJournalApi(
        OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val r = chain.request()
                val (code, body) = responder(r)
                Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
                    .body(body.toResponseBody()).build()
            },
        ).build(),
    )
    private val c = Connection("http://192.168.1.10:5100/".toHttpUrl(), "k")

    @Test
    fun startsAtLastEventIdWithoutNotifyingAndSavesCursorOnlyAfterSuccess() = runBlocking {
        val dir = Files.createTempDirectory("journal-notify").toFile()
        var currentUser = "parent-a"
        val states = ReadStateStore(dir = { dir }, userId = { currentUser })

        // First run for parent-a: starts at status.lastEventId = 50, no notifications.
        responder = { r ->
            when (r.url.encodedPath) {
                "/api/v1/status" ->
                    200 to """{"auth":"ok","lastEventId":50,"counts":{"grades":1,"messages":1,"unreadMessages":0}}"""

                else -> error("unexpected ${r.url}")
            }
        }
        assertTrue(checkNotifications(api, c, states, Instant.parse("2026-11-17T10:00:00Z")).isEmpty())
        assertEquals(50L, states.load(c.base.toString())!!.notifyCursor)

        // Second run: two pages of changes (51, then 52); cursor advances to 52.
        responder = { r ->
            when (r.url.encodedPath) {
                "/api/v1/changes" -> when (r.url.queryParameter("after")) {
                    "50" ->
                        200 to
                            """{"items":[{"id":51,"childId":"101","entityType":"grade","entityId":"g1","changeType":"created","detectedAt":"2026-11-17T11:00:00Z","entity":{"id":"g1","childId":"101","subject":"Math","value":"5"}}],"nextCursor":51,"hasMore":true}"""

                    "51" ->
                        200 to
                            """{"items":[{"id":52,"childId":"101","entityType":"grade","entityId":"g2","changeType":"created","detectedAt":"2026-11-17T11:01:00Z","entity":{"id":"g2","childId":"101","subject":"Art","value":"6"}}],"nextCursor":52,"hasMore":false}"""

                    else -> error("unexpected after ${r.url}")
                }

                "/api/v1/children" -> 200 to """{"children":[{"id":"101","name":"Ola"},{"id":"102","name":"Kuba"}]}"""

                else -> error("unexpected ${r.url}")
            }
        }
        val items = checkNotifications(api, c, states)
        assertEquals(2, items.size)
        assertEquals(52L, states.load(c.base.toString())!!.notifyCursor)

        // Error on second page: cursor stays at 52.
        responder = { r ->
            when (r.url.queryParameter("after")) {
                "52" -> 200 to """{"items":[],"nextCursor":53,"hasMore":true}"""
                else -> 503 to """{"error":{"code":"STORAGE_UNAVAILABLE","message":"down"}}"""
            }
        }
        try {
            checkNotifications(api, c, states)
            fail("Expected SchoolJournalError")
        } catch (_: SchoolJournalError) {
        }
        assertEquals(52L, states.load(c.base.toString())!!.notifyCursor)

        // Another Immich user on the same phone gets their own cursor.
        currentUser = "parent-b"
        responder =
            { 200 to """{"auth":"ok","lastEventId":60,"counts":{"grades":1,"messages":1,"unreadMessages":0}}""" }
        assertTrue(checkNotifications(api, c, states, Instant.parse("2026-11-17T12:00:00Z")).isEmpty())
        assertEquals(60L, states.load(c.base.toString())!!.notifyCursor)
        currentUser = "parent-a"
        assertEquals(52L, states.load(c.base.toString())!!.notifyCursor)
    }
}
