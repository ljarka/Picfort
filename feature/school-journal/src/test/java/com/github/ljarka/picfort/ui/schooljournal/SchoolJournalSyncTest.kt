package com.github.ljarka.picfort.ui.schooljournal

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Sync now" (spec §4.5) against a scripted server, without real waiting. */
class SchoolJournalSyncTest {
    /** Answer to `POST /sync`: (code, error code, Retry-After). */
    private var syncAnswer: Triple<Int, String?, String?> = Triple(202, null, null)

    /** Successive `/status` answers: (startedAt, status, changesDetected). */
    private var statuses: ArrayDeque<Triple<String, String, Int>> = ArrayDeque()
    private var statusCalls = 0
    private var slept = 0L

    private val api = SchoolJournalApi(
        OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val r = chain.request()
                val (code, body, headers) = when (r.url.encodedPath) {
                    "/api/v1/sync" -> {
                        val (code, error, retry) = syncAnswer
                        val body = if (error ==
                            null
                        ) {
                            """{"status":"started"}"""
                        } else {
                            """{"error":{"code":"$error","message":"x"}}"""
                        }
                        Triple(code, body, listOfNotNull(retry?.let { "Retry-After" to it }))
                    }

                    "/api/v1/status" -> {
                        statusCalls++
                        val (started, status, changes) = if (statuses.size >
                            1
                        ) {
                            statuses.removeFirst()
                        } else {
                            statuses.first()
                        }
                        Triple(
                            200,
                            """{"auth":"ok","lastEventId":5,"lastSuccessfulSyncAt":"2026-11-17T12:00:00Z",
                               "lastSync":{"startedAt":"$started","status":"$status","itemsFetched":10,"changesDetected":$changes}}""",
                            emptyList(),
                        )
                    }

                    else -> error("unexpected ${r.url}")
                }
                Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .body(body.toResponseBody()).build()
            },
        ).build(),
    )
    private val c = Connection("http://192.168.1.10:5100/".toHttpUrl(), "k")
    private val before = JournalStatus(lastSync = SyncRun(startedAt = "2026-11-17T11:00:00Z", status = "ok"))

    private fun sync() = runBlocking { syncNow(api, c, before) { slept += it } }

    @Test
    fun startedThenRunningThenDone() {
        statuses = ArrayDeque(
            listOf(
                Triple("2026-11-17T11:00:00Z", "ok", 0), // the previous run, not started yet
                Triple("2026-11-17T12:30:00Z", "running", 0),
                Triple("2026-11-17T12:30:00Z", "ok", 3),
            ),
        )
        val outcome = sync()
        assertTrue(outcome is SyncOutcome.Done)
        assertEquals(3, statusCalls)
        assertEquals(15_000L, slept)
        assertEquals("Checked the e-journal: 3 changes", outcome.message())
    }

    @Test
    fun alreadyRunningIsWaitedFor() {
        syncAnswer = Triple(409, "SYNC_IN_PROGRESS", null)
        statuses =
            ArrayDeque(listOf(Triple("2026-11-17T12:29:00Z", "running", 0), Triple("2026-11-17T12:29:00Z", "ok", 0)))
        val outcome = sync()
        assertTrue(outcome is SyncOutcome.Done)
        assertEquals("Checked the e-journal: nothing new", outcome.message())
    }

    @Test
    fun aFailedRunIsDoneButSaysSo() {
        statuses = ArrayDeque(listOf(Triple("2026-11-17T12:30:00Z", "error", 0)))
        assertEquals("The check of the e-journal failed", sync().message())
    }

    @Test
    fun tooSoonTellsWhenToTryAgain() {
        syncAnswer = Triple(429, "SYNC_TOO_SOON", "240")
        val outcome = sync()
        assertTrue(outcome is SyncOutcome.TooSoon)
        assertEquals("Checked recently. Try again in 4 min", outcome.message())
        assertEquals(0, statusCalls)
    }

    @Test
    fun authFailedLeavesItToTheBanner() {
        syncAnswer = Triple(409, "AUTH_FAILED", null)
        val outcome = sync()
        assertEquals(SyncOutcome.AuthFailed, outcome)
        assertEquals(null, outcome.message())
    }

    @Test
    fun givesUpAfterTwoMinutes() {
        statuses = ArrayDeque(listOf(Triple("2026-11-17T12:30:00Z", "running", 0)))
        assertEquals(SyncOutcome.TimedOut, sync())
        assertEquals(SYNC_MAX_WAIT_MILLIS, slept)
        assertEquals((SYNC_MAX_WAIT_MILLIS / SYNC_POLL_MILLIS).toInt(), statusCalls)
    }

    @Test
    fun aWrongKeyFails() {
        syncAnswer = Triple(401, "UNAUTHORIZED", null)
        val outcome = sync()
        assertTrue(outcome is SyncOutcome.Failed && outcome.error is SchoolJournalError.WrongKey)
    }
}
