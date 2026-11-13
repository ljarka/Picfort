package com.github.ljarka.picfort.ui.frame

import android.content.Context
import com.github.ljarka.picfort.core.model.FrameResult
import com.github.ljarka.picfort.core.model.FrameSender
import com.github.ljarka.picfort.core.model.localImageUri
import com.github.ljarka.picfort.core.network.CleartextNotAllowedException
import com.github.ljarka.picfort.core.network.FrameSettingsStore
import com.github.ljarka.picfort.core.network.isPicfortKeyRejected
import com.github.ljarka.picfort.core.network.normalizeFrameUrl
import com.github.ljarka.picfort.core.network.picfortKey
import com.github.ljarka.picfort.core.network.readParsedBody
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FrameHttpClient

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
    @ApplicationContext private val context: Context,
) : FrameSender {

    override suspend fun send(assetId: String): FrameResult {
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

    /**
     * Sends a phone-only (LOCAL) photo directly to the frame via multipart POST.
     * Does not require the photo to be on Immich.
     */
    override suspend fun sendLocal(assetId: String): FrameResult {
        val base = normalizeFrameUrl(settings.frameUrl) ?: return FrameResult.NotConfigured
        val url = base.newBuilder().addPathSegment("run-local").build()
        val uri = localImageUri(assetId.toLong())
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: return FrameResult.Error("Could not read photo from storage")
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("photo", "photo.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            .build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).post(body).build()).execute().use { response ->
                    val payload = response.readParsedBody<FrameResponse>(json)
                    when (response.code) {
                        200, 202 -> FrameResult.Sent
                        409 -> FrameResult.Busy
                        429 -> FrameResult.TooSoon(payload.value?.retryAfter)
                        else -> FrameResult.Error(payload.value?.message ?: "Frame returned HTTP ${response.code}")
                    }
                }
            } catch (e: CleartextNotAllowedException) {
                FrameResult.Error(e.message ?: "Use https:// for frames outside your network")
            } catch (e: IOException) {
                FrameResult.Error("Can't reach the frame at ${url.host}")
            }
        }
    }

    /** Checks that [address] points at a running frame server that accepts [apiKey]. */
    suspend fun testConnection(address: String, apiKey: String): FrameResult {
        val base = normalizeFrameUrl(address) ?: return FrameResult.Error("Invalid address")
        val url = base.newBuilder().addPathSegment("status").build()
        return request(url, apiKey) { code, body ->
            if (code == 200 && body?.status == "ok") {
                FrameResult.Sent
            } else {
                FrameResult.Error("Not a Picfort server (HTTP $code)")
            }
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
                val payload = response.readParsedBody<FrameResponse>(json)
                map(response.code, payload.value)
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
