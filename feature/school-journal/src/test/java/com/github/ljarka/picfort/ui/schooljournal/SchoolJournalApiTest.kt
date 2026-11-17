package com.github.ljarka.picfort.ui.schooljournal

import com.github.ljarka.picfort.core.network.CleartextPolicyInterceptor
import java.io.IOException
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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The client against a fake transport: what it sends and how answers map (spec §5, §4.8). */
class SchoolJournalApiTest {
    private val requests = mutableListOf<Request>()
    private var reply: (Request) -> Response = { ok(it, "{}") }

    private val api = SchoolJournalApi(
        OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                requests += chain.request()
                reply(chain.request())
            },
        ).build(),
    )
    private val c = Connection("http://192.168.1.10:5100/".toHttpUrl(), "secret")

    private fun ok(r: Request, body: String, code: Int = 200, headers: Map<String, String> = emptyMap()) =
        Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .body(body.toResponseBody()).build()

    private fun error(code: Int, errorCode: String, headers: Map<String, String> = emptyMap()) {
        reply = { ok(it, """{"error":{"code":"$errorCode","message":"server text"}}""", code, headers) }
    }

    private inline fun <reified E : SchoolJournalError> assertFails(block: () -> Unit): E {
        try {
            block()
        } catch (e: SchoolJournalError) {
            assertTrue("Expected ${E::class.simpleName}, got $e", e is E)
            return e as E
        }
        fail("Expected ${E::class.simpleName}")
        throw AssertionError()
    }

    @Test
    fun sendsKeyAndPathsUnderTheBaseAddress() = runBlocking<Unit> {
        reply = { ok(it, """{"items":[],"nextCursor":null}""") }
        api.messages(c, child = "101", since = "2026-11-01T00:00:00Z", limit = 50, cursor = "abc")
        val r = requests.single()
        assertEquals("secret", r.header("X-School-Key"))
        assertEquals("/api/v1/messages", r.url.encodedPath)
        assertEquals("101", r.url.queryParameter("child"))
        assertEquals("2026-11-01T00:00:00Z", r.url.queryParameter("since"))
        assertEquals("50", r.url.queryParameter("limit"))
        assertEquals("abc", r.url.queryParameter("cursor"))
        assertNull("includeDeleted only when asked", r.url.queryParameter("includeDeleted"))
    }

    @Test
    fun keepsASubPathOfAReverseProxy() = runBlocking<Unit> {
        reply = { ok(it, """{"children":[]}""") }
        api.children(Connection("https://example.com/school/".toHttpUrl(), "k"))
        assertEquals("/school/api/v1/children", requests.single().url.encodedPath)
    }

    @Test
    fun changesUseAfterAndType() = runBlocking<Unit> {
        reply = { ok(it, """{"items":[],"nextCursor":7,"hasMore":false}""") }
        val page = api.changes(c, after = 7, type = "grade", limit = 1)
        val url = requests.single().url
        assertEquals("7", url.queryParameter("after"))
        assertEquals("grade", url.queryParameter("type"))
        assertEquals("1", url.queryParameter("limit"))
        assertNull(url.queryParameter("since"))
        assertEquals(7L, page.nextCursor)
    }

    @Test
    fun syncIsAPostWithTheKey() = runBlocking<Unit> {
        reply = { ok(it, """{"status":"started"}""", code = 202) }
        assertEquals("started", api.sync(c).status)
        assertEquals("POST", requests.single().method)
        assertEquals("secret", requests.single().header("X-School-Key"))
    }

    @Test
    fun healthNeedsNoKeyAndChecksTheAnswer() = runBlocking<Unit> {
        reply = { ok(it, """{"status":"ok"}""") }
        api.health(c.base)
        assertNull(requests.single().header("X-School-Key"))

        reply = { ok(it, "<html>router</html>") }
        assertFails<SchoolJournalError.NotSchoolServer> { runBlocking { api.health(c.base) } }
        reply = { ok(it, "not found", code = 404) }
        assertFails<SchoolJournalError.NotSchoolServer> { runBlocking { api.health(c.base) } }
    }

    @Test
    fun mapsEveryErrorCode() {
        val cases: List<Triple<Int, String, (SchoolJournalError) -> Boolean>> = listOf(
            Triple(401, "UNAUTHORIZED", { it is SchoolJournalError.WrongKey }),
            Triple(400, "BAD_REQUEST", { it is SchoolJournalError.BadRequest }),
            Triple(404, "NOT_FOUND", { it is SchoolJournalError.NotFound }),
            Triple(404, "CHILD_NOT_FOUND", { it is SchoolJournalError.ChildNotFound }),
            Triple(409, "SYNC_IN_PROGRESS", { it is SchoolJournalError.SyncInProgress }),
            Triple(409, "AUTH_FAILED", { it is SchoolJournalError.AuthFailed }),
            Triple(503, "NO_DATA_YET", { it is SchoolJournalError.NoDataYet }),
            Triple(503, "STORAGE_UNAVAILABLE", { it is SchoolJournalError.StorageUnavailable }),
            Triple(500, "SOMETHING_NEW", { it is SchoolJournalError.Other }),
        )
        cases.forEach { (http, code, check) ->
            error(http, code)
            try {
                runBlocking { api.status(c) }
                fail("$code should fail")
            } catch (e: SchoolJournalError) {
                assertTrue("$code → $e", check(e))
            }
        }
    }

    @Test
    fun retryAfterComesWithTooManyRequests() {
        error(429, "TOO_MANY_WRONG_KEYS", mapOf("Retry-After" to "600"))
        val wrong = assertFails<SchoolJournalError.TooManyWrongKeys> { runBlocking { api.status(c) } }
        assertEquals(600L, wrong.retryAfterSeconds)
        assertEquals("Too many wrong keys. Try again in 10 min", wrong.userMessage())

        error(429, "SYNC_TOO_SOON", mapOf("Retry-After" to "61"))
        val soon = assertFails<SchoolJournalError.SyncTooSoon> { runBlocking { api.sync(c) } }
        assertEquals("Checked recently. Try again in 2 min", soon.userMessage())
    }

    @Test
    fun anUnreadableBodyIsAnErrorNotACrash() {
        reply = { ok(it, "garbage") }
        assertFails<SchoolJournalError.Other> { runBlocking { api.status(c) } }
        reply = { ok(it, "", code = 502) }
        assertFails<SchoolJournalError.Other> { runBlocking { api.status(c) } }
    }

    @Test
    fun noAnswerIsUnreachable() {
        reply = { throw IOException("connection refused") }
        val e = assertFails<SchoolJournalError.Unreachable> { runBlocking { api.status(c) } }
        assertEquals("192.168.1.10", e.host)
    }

    @Test
    fun plainHttpOutsideTheHomeNetworkIsRefused() {
        val real = SchoolJournalApi(OkHttpClient.Builder().addInterceptor(CleartextPolicyInterceptor()).build())
        assertFails<SchoolJournalError.CleartextNotAllowed> {
            runBlocking { real.status(Connection("http://school.example.com/".toHttpUrl(), "k")) }
        }
    }

    @Test
    fun normalizesAddresses() {
        assertEquals("http://192.168.1.10:5100/", normalizeJournalAddress(" 192.168.1.10:5100 ").toString())
        assertEquals("https://school.example.com/", normalizeJournalAddress("school.example.com").toString())
        assertEquals(
            "https://school.example.com/sub/",
            normalizeJournalAddress("https://school.example.com/sub/").toString(),
        )
        assertEquals("http://nas.local:5100/", normalizeJournalAddress("http://nas.local:5100?x=1").toString())
        assertNull(normalizeJournalAddress(""))
        assertNull(normalizeJournalAddress("http://"))
    }
}
