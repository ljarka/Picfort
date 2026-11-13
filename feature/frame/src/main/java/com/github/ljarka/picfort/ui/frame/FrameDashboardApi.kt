package com.github.ljarka.picfort.ui.frame

import com.github.ljarka.picfort.core.network.CleartextNotAllowedException
import com.github.ljarka.picfort.core.network.FrameSettingsStore
import com.github.ljarka.picfort.core.network.isPicfortKeyRejected
import com.github.ljarka.picfort.core.network.normalizeFrameUrl
import com.github.ljarka.picfort.core.network.readParsedBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Serializable
data class SlideshowState(
    val mode: String = "off",
    @SerialName("album_id") val albumId: String? = null,
    @SerialName("interval_minutes") val intervalMinutes: Int = 60,
    @SerialName("quiet_start") val quietStart: Int? = null,
    @SerialName("quiet_end") val quietEnd: Int? = null,
    /** Unix seconds. */
    @SerialName("next_change_at") val nextChangeAt: Double? = null,
    @SerialName("last_error") val lastError: String? = null,
    val intervals: List<Int> = listOf(15, 30, 60, 180, 360, 720, 1440),
)

@Serializable
data class FrameStatus(
    val status: String = "",
    val busy: Boolean = false,
    val currentAsset: String? = null,
    val lastError: String? = null,
    val hardware: Boolean = false,
    val slideshow: SlideshowState? = null,
)

@Serializable
data class FrameAlbum(val id: String, val name: String = "")

@Serializable
private data class ErrorBody(val message: String? = null)

/** A failure talking to the frame, with a message fit for the UI. */
class FrameException(message: String) : Exception(message)

/** Slideshow and status calls to the Picfort server (same no-Immich-token client as [FrameRepository]). */
@Singleton
class FrameDashboardApi @Inject constructor(
    @FrameHttpClient private val client: OkHttpClient,
    private val settings: FrameSettingsStore,
    private val json: Json,
) {
    val isConfigured: Boolean get() = normalizeFrameUrl(settings.frameUrl) != null
    val address: String get() = settings.frameUrl

    suspend fun status(): FrameStatus = json.decodeFromString(call("GET", "status"))

    suspend fun albums(): List<FrameAlbum> = json.decodeFromString(call("GET", "albums"))

    suspend fun next() {
        call("POST", "next", "{}")
    }

    /** Sends only the given fields; null values clear them (e.g. quiet hours off). */
    suspend fun updateSlideshow(fields: Map<String, Any?>): SlideshowState {
        val body = JsonObject(fields.mapValues { (_, v) -> v.toJson() })
        return json.decodeFromString(call("PUT", "slideshow", body.toString()))
    }

    /** PNG of what the panel shows (dithered preview), or null if nothing yet. */
    suspend fun currentImage(): ByteArray? = withContext(Dispatchers.IO) {
        val url = base().newBuilder().addPathSegment("current").build()
        execute(Request.Builder().url(url).build()) { response ->
            when (response.code) {
                200 -> response.body.bytes()
                404 -> null
                else -> throw FrameException("Frame returned HTTP ${response.code}")
            }
        }
    }

    private fun base() =
        normalizeFrameUrl(settings.frameUrl) ?: throw FrameException("Set the Picfort server address first")

    private suspend fun call(method: String, path: String, jsonBody: String? = null): String =
        withContext(Dispatchers.IO) {
            val url = base().newBuilder().addPathSegment(path).build()
            val body = jsonBody?.toRequestBody("application/json".toMediaType())
            execute(Request.Builder().url(url).method(method, body).build()) { response ->
                val payload = response.readParsedBody<ErrorBody>(json)
                if (response.code in 200..299) {
                    payload.text
                } else {
                    throw FrameException(payload.value?.message ?: "Frame returned HTTP ${response.code}")
                }
            }
        }

    private fun <T> execute(request: Request, map: (Response) -> T): T = try {
        client.newCall(request).execute().use { response ->
            if (response.isPicfortKeyRejected()) throw FrameException(WRONG_API_KEY)
            map(response)
        }
    } catch (e: CleartextNotAllowedException) {
        throw FrameException(e.message ?: "Use https:// for frames outside your network")
    } catch (e: IOException) {
        throw FrameException("Can't reach the Picfort server at ${request.url.host}")
    }
}

private fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    else -> error("Unsupported value $this")
}
