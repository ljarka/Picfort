package com.github.ljarka.picfort.ui.schooljournal

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bodies shaped like school-server's `openapi.yaml` (v1.0.0) decode, including fields and enum
 * values the server may add later (it only ever adds within `/api/v1`).
 */
class SchoolJournalContractTest {

    private val grade = """
        {"id":"g_8812","childId":"101","subject":"Mathematics","value":"4+","numericValue":4.5,"weight":3,
         "category":"Test","semester":1,"kind":"regular","countsToAverage":true,"teacher":"A. Teacher",
         "comment":null,"issuedOn":"2026-11-12","firstSeenAt":"2026-11-12T14:05:00Z",
         "updatedAt":"2026-11-12T14:05:00Z","deletedAt":null,"futureField":{"a":1}}
    """.trimIndent()

    private val messageSummary = """
        {"id":"m_4471","childId":"101","sender":"Class teacher","subject":"Trip on Friday",
         "sentAt":"2026-11-16T07:30:00Z","hasAttachments":true,"read":false,"readAt":null,"deletedAt":null}
    """.trimIndent()

    @Test
    fun status() {
        val s = journalJson.decodeFromString<JournalStatus>(
            """
            {"auth":"ok","lastSync":{"startedAt":"2026-11-17T10:00:00Z","finishedAt":"2026-11-17T10:00:20Z",
             "status":"ok","errorCode":null,"itemsFetched":120,"changesDetected":2},
             "lastSuccessfulSyncAt":"2026-11-17T10:00:20Z","nextSyncAt":"2026-11-17T10:15:20Z",
             "lastEventId":340,"counts":{"grades":80,"messages":40,"unreadMessages":3},"version":"1.2.0",
             "somethingNew":true}
            """.trimIndent(),
        )
        assertEquals(340L, s.lastEventId)
        assertEquals(Instant.parse("2026-11-17T10:00:20Z"), instantOrNull(s.lastSuccessfulSyncAt))
        assertFalse(s.authFailed)
        assertFalse(s.lastSync!!.failed)
        assertEquals("1.2.0", s.version)
    }

    @Test
    fun statusBeforeAnySync() {
        val s = journalJson.decodeFromString<JournalStatus>(
            """{"auth":"unknown","lastSync":null,"lastSuccessfulSyncAt":null,"nextSyncAt":null,"lastEventId":0,
               "counts":{"grades":0,"messages":0,"unreadMessages":0},"version":"dev"}""",
        )
        assertNull(s.lastSync)
        assertNull(instantOrNull(s.lastSuccessfulSyncAt))
    }

    @Test
    fun childrenAndSubjects() {
        val children = journalJson.decodeFromString<ChildrenDto>(
            """{"children":[{"id":"101","login":"ola.k","name":"Ola"},{"id":"102","login":"kuba.k","name":"Kuba"}]}""",
        ).children
        assertEquals(listOf("Ola", "Kuba"), children.map { it.name })

        val subjects = journalJson.decodeFromString<SubjectsDto>(
            """{"items":[{"subject":"Mathematics","average":4.33,"grades":[$grade]},{"subject":"Art","average":null,"grades":[]}]}""",
        ).items
        assertEquals(4.33, subjects[0].average!!, 0.0)
        assertNull(subjects[1].average)
        assertEquals(4.5, subjects[0].grades.single().numericValue!!, 0.0)
        assertEquals(GradeKind.REGULAR, subjects[0].grades.single().gradeKind)
    }

    @Test
    fun unknownGradeKindIsKeptAsUnknown() {
        val g = journalJson.decodeFromString<Grade>(grade.replace("\"regular\"", "\"mid_term\""))
        assertEquals(GradeKind.UNKNOWN, g.gradeKind)
        assertEquals(GradeKind.PROPOSED_FINAL, GradeKind.of("proposed_final"))
    }

    @Test
    fun messagesPageAndMessage() {
        val page = journalJson.decodeFromString<MessagesPage>("""{"items":[$messageSummary],"nextCursor":"c2"}""")
        assertEquals("c2", page.nextCursor)
        assertTrue(page.items.single().hasAttachments)

        val last = journalJson.decodeFromString<MessagesPage>("""{"items":[],"nextCursor":null}""")
        assertNull(last.nextCursor)

        val m = journalJson.decodeFromString<Message>(
            messageSummary.dropLast(1) +
                ""","bodyText":"Hello","bodyHtml":"<p>Hello</p>","attachments":[{"name":"plan.pdf"}]}""",
        )
        assertEquals("<p>Hello</p>", m.bodyHtml)
        assertEquals("plan.pdf", m.attachments.single().name)
    }

    @Test
    fun changeEventsDecodeTheirEntityByType() {
        val page = journalJson.decodeFromString<ChangePage>(
            """
            {"items":[
              {"id":121,"childId":"101","entityType":"grade","entityId":"g_8812","changeType":"updated",
               "detectedAt":"2026-11-16T14:05:00Z","entity":$grade,"diff":{"value":["4","4+"],"numericValue":[4,4.5]}},
              {"id":122,"childId":"101","entityType":"message","entityId":"m_4471","changeType":"created",
               "detectedAt":"2026-11-16T14:06:00Z","entity":$messageSummary,"diff":null},
              {"id":123,"childId":"101","entityType":"homework","entityId":"h_1","changeType":"created",
               "detectedAt":"2026-11-16T14:07:00Z","entity":{"id":"h_1"},"diff":null}
            ],"nextCursor":123,"hasMore":true}
            """.trimIndent(),
        )
        val (gradeEvent, messageEvent, unknown) = page.items
        assertEquals("4+", gradeEvent.grade()!!.value)
        assertNull(gradeEvent.message())
        assertEquals(2, gradeEvent.diff!!["value"]!!.size)
        assertEquals("Trip on Friday", messageEvent.message()!!.subject)
        assertNull(unknown.grade())
        assertNull(unknown.message())
        assertTrue(page.hasMore)
        assertEquals(123L, page.nextCursor)
    }

    @Test
    fun errorBody() {
        val e = journalJson.decodeFromString<ErrorBody>("""{"error":{"code":"UNAUTHORIZED","message":"Missing key"}}""")
        assertEquals("UNAUTHORIZED", e.error!!.code)
    }
}
