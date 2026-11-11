package com.github.ljarka.picfort.network

import com.github.ljarka.picfort.ui.frame.FrameSettingsStore
import com.github.ljarka.picfort.ui.frame.WRONG_API_KEY
import com.github.ljarka.picfort.ui.frame.normalizeFrameUrl
import com.github.ljarka.picfort.ui.login.AccessTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PicfortHttpClient

/** Why a call to the Picfort server failed, as the UI tells it. */
sealed class PicfortServerException(message: String) : Exception(message) {
    class NotConfigured : PicfortServerException("Connect your Picfort server first")
    class Unreachable(host: String) : PicfortServerException("Can't reach the Picfort server at $host")
    /** 401: the server didn't accept our Immich login (e.g. it uses another Immich). */
    class NotAccepted : PicfortServerException("The Picfort server didn't accept your Immich login")
    /** 404 on `/wall`: an ImageFrameServer without the wall. */
    class NoWall : PicfortServerException("This Picfort server has no Family Wall yet, update ImageFrameServer")
    /** 404 on a feature's root (`/lists`, …): a server older than that feature. */
    class Outdated(feature: String) : PicfortServerException("This Picfort server has no $feature yet, update it")
    /** 404 on one object: someone else deleted it. */
    class Gone(message: String) : PicfortServerException(message)
    /** 502: the server can't ask Immich who we are. */
    class ImmichUnreachable : PicfortServerException("The Picfort server can't reach Immich")
    class Forbidden(message: String = "Only the author can change this post") : PicfortServerException(message)
    /** 401 from the server's own PICFORT_API_KEY check, not from Immich. */
    class WrongApiKey : PicfortServerException(WRONG_API_KEY)
    class Other(message: String) : PicfortServerException(message)
}

@Serializable
private data class ErrorBody(val message: String? = null)

/**
 * Calls to the family features of the Picfort server (ADR 0006, 0008): the Immich token, the
 * Picfort key and the error mapping in one place. The address can change at runtime, so this
 * is plain OkHttp rather than Retrofit.
 */
@Singleton
class PicfortServerClient @Inject constructor(
    @PicfortHttpClient private val client: OkHttpClient,
    private val settings: FrameSettingsStore,
    private val tokens: AccessTokenStore,
    private val json: Json,
) {
    data class Reply(val code: Int, val body: String, val etag: String?)

    val isConfigured: Boolean get() = normalizeFrameUrl(settings.frameUrl) != null

    fun base(address: String = settings.frameUrl): HttpUrl =
        normalizeFrameUrl(address) ?: throw PicfortServerException.NotConfigured()

    /** 2xx and 304 come back as a [Reply]; everything else throws. [notFound] maps a 404. */
    suspend fun execute(unsigned: Request, notFound: () -> PicfortServerException): Reply = withContext(Dispatchers.IO) {
        // Signed here too (not only by PicfortAuthInterceptor), so an address that isn't saved
        // yet can be tested. Only ever for family features (ADR 0006).
        val request = unsigned.newBuilder().header("Authorization", "Bearer ${tokens.accessToken}").build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (response.isPicfortKeyRejected()) throw PicfortServerException.WrongApiKey()
                when (response.code) {
                    in 200..299, 304 -> Reply(response.code, text, response.header("ETag"))
                    401 -> throw PicfortServerException.NotAccepted()
                    403 -> throw PicfortServerException.Forbidden(errorMessage(text) ?: "Not allowed")
                    404 -> throw notFound()
                    502 -> throw PicfortServerException.ImmichUnreachable()
                    else -> throw PicfortServerException.Other(
                        errorMessage(text) ?: "The Picfort server returned HTTP ${response.code}"
                    )
                }
            }
        } catch (e: CleartextNotAllowedException) {
            throw PicfortServerException.Other(e.message ?: "Use https:// for servers outside your network")
        } catch (e: IOException) {
            throw PicfortServerException.Unreachable(request.url.host)
        }
    }

    private fun errorMessage(text: String) = runCatching { json.decodeFromString<ErrorBody>(text).message }.getOrNull()
}
