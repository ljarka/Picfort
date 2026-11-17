package com.github.ljarka.picfort.ui.schooljournal

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SchoolJournalGradesTest {
    private fun g(
        id: String,
        value: String,
        issued: String,
        semester: Int = 1,
        kind: String = "regular",
        numeric: Double? = null,
    ) = Grade(id = id, value = value, issuedOn = issued, semester = semester, kind = kind, numericValue = numeric)

    private fun subject(name: String, vararg grades: Grade, average: Double? = null) =
        SubjectSummary(name, average, grades.toList())

    @Test
    fun defaultSemesterIsTheOneOfTheNewestGrade() {
        val sections = listOf(
            GradeSection(
                "101",
                "Ola",
                listOf(subject("Mathematics", g("g1", "5", "2026-01-10", 1), g("g2", "4", "2026-03-02", 2))),
            ),
            GradeSection("102", "Kuba", listOf(subject("Art", g("g3", "6", "2026-02-20", 2)))),
        )
        assertEquals(2, defaultSemester(sections))
        assertEquals(
            1,
            defaultSemester(listOf(GradeSection(null, null, listOf(subject("Art", g("g4", "5", "2026-11-12", 1)))))),
        )
        assertEquals("no grades: semester 1", 1, defaultSemester(emptyList()))
    }

    @Test
    fun subjectsWithANewGradeComeFirstOthersKeepTheirOrder() {
        val subjects =
            listOf(
                subject("Art", g("a", "5", "2026-11-01")),
                subject("English", g("e", "4", "2026-11-01")),
                subject("History", g("h", "3", "2026-11-01")),
            )
        assertEquals(listOf("History", "Art", "English"), orderSubjects(subjects, setOf("h")).map { it.subject })
        assertEquals(listOf("Art", "English", "History"), orderSubjects(subjects, emptySet()).map { it.subject })
    }

    @Test
    fun regularGradesOldestFirstOtherKindsLabelled() {
        val s = subject(
            "Mathematics",
            g("g2", "4", "2026-11-12"),
            g("g1", "5", "2026-10-01"),
            g("p", "4", "2026-12-01", kind = "proposed_semester"),
            g("f", "5", "2026-06-20", kind = "final"),
            g("x", "+", "2026-11-13", kind = "something_new"),
        )
        assertEquals(listOf("g1", "g2", "x"), s.regularGrades().map { it.id })
        assertEquals(
            listOf("Proposed for the semester" to "p", "Final" to "f"),
            s.labelledGrades().map {
                it.first to
                    it.second.id
            },
        )
    }

    @Test
    fun averagesAndBands() {
        assertEquals("—", averageText(null))
        assertEquals("4.33", averageText(4.333))
        assertEquals(GradeBand.EXCELLENT, gradeBand(5.0))
        assertEquals(GradeBand.GOOD, gradeBand(4.5))
        assertEquals(GradeBand.FAIR, gradeBand(3.0))
        assertEquals(GradeBand.WEAK, gradeBand(2.75))
        assertEquals(GradeBand.FAILING, gradeBand(1.0))
        assertNull(gradeBand(null))
    }

    // --- New ids from the change feed ---

    private val requests = mutableListOf<Request>()

    /** Events (id, entityId, changeType); pages of [pageSize] after any cursor. */
    private var events: List<Triple<Long, String, String>> = emptyList()
    private val pageSize = 2

    private val api = SchoolJournalApi(
        OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val r = chain.request()
                requests += r
                val after = r.url.queryParameter("after")!!.toLong()
                val rest = events.filter { it.first > after }
                val page = rest.take(pageSize)
                val items = page.joinToString(",") { (id, entity, change) ->
                    """{"id":$id,"childId":"101","entityType":"grade","entityId":"$entity","changeType":"$change","detectedAt":"2026-11-17T12:00:00Z"}"""
                }
                val next = page.lastOrNull()?.first ?: after
                val body = """{"items":[$items],"nextCursor":$next,"hasMore":${rest.size > pageSize}}"""
                Response.Builder().request(
                    r,
                ).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
            },
        ).build(),
    )
    private val c = Connection("http://192.168.1.10:5100/".toHttpUrl(), "k")

    @Test
    fun newIdsFollowTheFeedAcrossPagesAndSkipRemovals() {
        events = listOf(
            Triple(5L, "g_old", "created"),
            Triple(11L, "g_a", "created"),
            Triple(12L, "g_b", "updated"),
            Triple(13L, "g_c", "deleted"),
            Triple(14L, "g_d", "created"),
        )
        val ids = runBlocking { loadNewGradeIds(api, c, after = 10) }
        assertEquals(setOf("g_a", "g_b", "g_d"), ids)
        assertEquals(listOf("10", "12", "14").take(requests.size), requests.map { it.url.queryParameter("after") })
        assertEquals("grade", requests.first().url.queryParameter("type"))
    }

    @Test
    fun nothingAfterTheCursorMeansNoNewGrades() {
        events = listOf(Triple(5L, "g_old", "created"))
        assertEquals(emptySet<String>(), runBlocking { loadNewGradeIds(api, c, after = 10) })
        assertEquals(1, requests.size)
    }
}
