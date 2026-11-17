package com.github.ljarka.picfort.ui.schooljournal

import com.github.ljarka.picfort.core.network.CleartextNotAllowedException
import com.github.ljarka.picfort.core.network.CleartextPolicyInterceptor
import com.github.ljarka.picfort.core.network.defaultSchemeFor
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Where the school-server is and the key it wants (`X-School-Key`). */
data class Connection(val base: HttpUrl, val key: String)

/**
 * "192.168.1.10:5100", "school.example.com", "https://school.example.com/sub/" → a base URL;
 * no scheme → http on the home network, https elsewhere ([defaultSchemeFor]). Null if unusable.
 */
fun normalizeJournalAddress(text: String): HttpUrl? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else defaultSchemeFor(trimmed) + trimmed
    val url = withScheme.toHttpUrlOrNull() ?: return null
    if (url.host.isEmpty()) return null
    // Keep a sub-path (reverse proxy), always ending with "/" so paths are added below it.
    return url.newBuilder().query(null).fragment(null).build().let {
        if (it.encodedPath.endsWith("/")) it else it.newBuilder().addPathSegment("").build()
    }
}

/**
 * Every school-server endpoint Picfort uses (spec §4.8). Own [OkHttpClient]: never the app's,
 * which adds Immich and Picfort credentials (ADR 0017). Not used on purpose: `GET /grades`
 * (subjects hold every grade) and the server's shared read state (`…/read`, `read-all`).
 */
@Singleton
class SchoolJournalApi internal constructor(private val client: OkHttpClient) {
    @Inject constructor() : this(defaultClient())

    /** `GET /health`, no key. Throws [SchoolJournalError.NotSchoolServer] if it isn't one. */
    suspend fun health(base: HttpUrl) {
        val reply =
            runCatchingNotSchoolServer(base) {
                call(Connection(base, ""), get(base, "health"), serializer<HealthDto>())
            }
        if (reply.status != "ok") throw SchoolJournalError.NotSchoolServer(base.host)
    }

    suspend fun status(c: Connection): JournalStatus = call(c, get(c, "api/v1/status"), serializer())

    suspend fun children(c: Connection): List<ChildAccount> =
        call(c, get(c, "api/v1/children"), serializer<ChildrenDto>()).children

    suspend fun subjects(c: Connection, child: String? = null, semester: Int? = null): List<SubjectSummary> = call(
        c,
        get(c, "api/v1/grades/subjects", "child" to child, "semester" to semester?.toString()),
        serializer<SubjectsDto>(),
    ).items

    suspend fun messages(
        c: Connection,
        child: String? = null,
        since: String? = null,
        limit: Int? = null,
        cursor: String? = null,
        includeDeleted: Boolean = false,
        /** Null = messages and announcements. */
        kind: MessageKind? = null,
    ): MessagesPage = call(
        c,
        get(
            c,
            "api/v1/messages",
            "child" to child,
            "since" to since,
            "limit" to limit?.toString(),
            "cursor" to cursor,
            "includeDeleted" to includeDeleted.takeIf { it }?.toString(),
            "type" to kind?.apiValue,
        ),
        serializer(),
    )

    suspend fun message(c: Connection, id: String): Message = call(c, get(c, "api/v1/messages/$id"), serializer())

    /** `after` only: the API forbids combining it with `since`, which Picfort doesn't need. */
    suspend fun changes(
        c: Connection,
        after: Long,
        child: String? = null,
        type: String? = null,
        limit: Int? = null,
    ): ChangePage = call(
        c,
        get(
            c,
            "api/v1/changes",
            "after" to after.toString(),
            "child" to child,
            "type" to type,
            "limit" to limit?.toString(),
        ),
        serializer(),
    )

    /** `POST /sync`: 202 started; 409 / 429 come back as [SchoolJournalError]. */
    suspend fun sync(c: Connection): SyncStarted = call(
        c,
        Request.Builder().url(url(c.base, "api/v1/sync")).post(ByteArray(0).toRequestBody()).header(KEY_HEADER, c.key),
        serializer(),
    )

    private fun get(c: Connection, path: String, vararg query: Pair<String, String?>) =
        get(c.base, path, *query).header(KEY_HEADER, c.key)

    private fun get(base: HttpUrl, path: String, vararg query: Pair<String, String?>): Request.Builder {
        val url = url(base, path).newBuilder().apply {
            query.forEach { (k, v) ->
                if (v !=
                    null
                ) {
                    addQueryParameter(k, v)
                }
            }
        }.build()
        return Request.Builder().url(url).get()
    }

    private fun url(base: HttpUrl, path: String): HttpUrl = base.newBuilder().addPathSegments(path).build()

    private suspend fun <T> call(c: Connection, request: Request.Builder, serializer: KSerializer<T>): T =
        withContext(Dispatchers.IO) {
            val built = request.build()
            try {
                client.newCall(built).execute().use { response ->
                    val text = response.body.string()
                    if (response.isSuccessful) {
                        runCatching { journalJson.decodeFromString(serializer, text) }.getOrElse {
                            throw SchoolJournalError.Other(response.code, "UNREADABLE", it.message.orEmpty())
                        }
                    } else {
                        val body = runCatching { journalJson.decodeFromString<ErrorBody>(text) }.getOrNull()
                        throw SchoolJournalError.of(response.code, body, response.header("Retry-After"), c.base.host)
                    }
                }
            } catch (e: CleartextNotAllowedException) {
                throw SchoolJournalError.CleartextNotAllowed(built.url.host)
            } catch (e: IOException) {
                throw SchoolJournalError.Unreachable(built.url.host)
            }
        }

    /** `/health` on something that isn't a school-server: any non-network failure means "not one". */
    private inline fun <T> runCatchingNotSchoolServer(base: HttpUrl, block: () -> T): T = try {
        block()
    } catch (e: SchoolJournalError.Unreachable) {
        throw e
    } catch (e: SchoolJournalError.CleartextNotAllowed) {
        throw e
    } catch (e: SchoolJournalError) {
        throw SchoolJournalError.NotSchoolServer(base.host)
    }

    companion object {
        const val KEY_HEADER = "X-School-Key"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .addInterceptor(CleartextPolicyInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
