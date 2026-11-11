package com.github.ljarka.picfort.core.network

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
