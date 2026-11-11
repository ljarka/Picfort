package com.github.ljarka.picfort.ui.frame

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.github.ljarka.picfort.network.CleartextNotAllowedException
import com.github.ljarka.picfort.network.isPicfortKeyRejected
import com.github.ljarka.picfort.network.picfortKey
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FrameHttpClient

sealed interface FrameResult {
    data object Sent : FrameResult
    data object NotConfigured : FrameResult
    data object Busy : FrameResult
    data class TooSoon(val retryAfterSeconds: Int?) : FrameResult
    data class Error(val message: String) : FrameResult
}

@Serializable
private data class FrameResponse(
    val status: String = "",
    val message: String? = null,
    @SerialName("retry_after") val retryAfter: Int? = null,
)

/**
 * Talks to the ImageFrameServer on the local network.
 *
 * Uses its own OkHttp client on purpose: the shared client attaches the Immich
 * access token to every request, and that token must never be sent to the frame.
 */
@Singleton
class FrameRepository @Inject constructor(
    @FrameHttpClient private val client: OkHttpClient,
    private val settings: FrameSettingsStore,
    private val json: Json,
) {

    suspend fun send(assetId: String): FrameResult {
        val base = normalizeFrameUrl(settings.frameUrl) ?: return FrameResult.NotConfigured
        val url = base.newBuilder()
            .addPathSegment("run")
            .addQueryParameter("assetId", assetId)
            .build()

        return request(url) { code, body ->
            when (code) {
                200, 202 -> FrameResult.Sent
                409 -> FrameResult.Busy
                429 -> FrameResult.TooSoon(body?.retryAfter)
                else -> FrameResult.Error(body?.message ?: "Frame returned HTTP $code")
            }
        }
    }

    /** Checks that [address] points at a running frame server that accepts [apiKey]. */
    suspend fun testConnection(address: String, apiKey: String): FrameResult {
        val base = normalizeFrameUrl(address) ?: return FrameResult.Error("Invalid address")
        val url = base.newBuilder().addPathSegment("status").build()
        return request(url, apiKey) { code, body ->
            if (code == 200 && body?.status == "ok") FrameResult.Sent
            else FrameResult.Error("Not a Picfort server (HTTP $code)")
        }
    }

    private suspend fun request(
        url: HttpUrl,
        apiKey: String? = null,
        map: (code: Int, body: FrameResponse?) -> FrameResult,
    ): FrameResult = withContext(Dispatchers.IO) {
        // null: the saved key, added by PicfortKeyInterceptor.
        val request = Request.Builder().url(url).apply { apiKey?.let { picfortKey(it) } }.build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isPicfortKeyRejected()) {
                    return@use FrameResult.Error(WRONG_API_KEY)
                }
                val body = response.body?.string()?.let {
                    runCatching { json.decodeFromString<FrameResponse>(it) }.getOrNull()
                }
                map(response.code, body)
            }
        } catch (e: CleartextNotAllowedException) {
            FrameResult.Error(e.message ?: "Use https:// for frames outside your network")
        } catch (e: IOException) {
            FrameResult.Error("Can't reach the Picfort server at ${url.host}")
        }
    }
}

/** What the UI says when the Picfort server rejects (or wants) its API key. */
const val WRONG_API_KEY = "The Picfort server needs an API key, or the key is wrong"
