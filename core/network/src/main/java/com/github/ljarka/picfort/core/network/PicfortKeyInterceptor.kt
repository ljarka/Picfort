package com.github.ljarka.picfort.core.network

import javax.inject.Inject
import javax.inject.Provider
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** Header carrying the Picfort server's own API key (PICFORT_API_KEY on the server). */
const val PICFORT_KEY_HEADER = "X-Picfort-Key"

/** The server's 401 for a missing or wrong API key, as opposed to a rejected Immich token on /wall. */
fun Response.isPicfortKeyRejected(): Boolean =
    code == 401 && header("WWW-Authenticate").orEmpty().startsWith("Picfort-Key")

/** Adds [key] (if any) to a request, for testing an address and key before they're saved. */
fun Request.Builder.picfortKey(key: String): Request.Builder =
    apply { key.trim().takeIf { it.isNotEmpty() }?.let { header(PICFORT_KEY_HEADER, it) } }

fun HttpUrl.isUnder(base: HttpUrl): Boolean =
    scheme == base.scheme && host == base.host && port == base.port && encodedPath.startsWith(base.encodedPath)

/**
 * Adds the saved API key to every request for the saved Picfort server: frame control and the
 * Family Wall (including wall photos loaded by Coil). Never to any other host. A key set on the
 * request already (testing an unsaved address) wins.
 */
class PicfortKeyInterceptor @Inject constructor(private val frameSettings: Provider<FrameSettingsStore>) :
    Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val settings = frameSettings.get()
        val key = picfortKeyFor(request, settings.frameUrl, settings.apiKey)
            ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(PICFORT_KEY_HEADER, key).build())
    }
}

/** The key to add to [request], or null: no key saved, another host, or a key already set. */
fun picfortKeyFor(request: Request, savedServer: String, savedKey: String): String? {
    val base = normalizeFrameUrl(savedServer) ?: return null
    if (savedKey.isEmpty() || request.header(PICFORT_KEY_HEADER) != null || !request.url.isUnder(base)) return null
    return savedKey
}
