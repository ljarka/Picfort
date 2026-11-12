package com.github.ljarka.picfort.network

import com.github.ljarka.picfort.core.network.AccessTokenStore
import com.github.ljarka.picfort.core.network.FrameSettingsStore
import com.github.ljarka.picfort.core.network.ServerUrlStore
import com.github.ljarka.picfort.core.network.normalizeFrameUrl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Provider

/** Whether this URL is [base] or below it (same scheme, host and port, path prefix). */
internal fun HttpUrl.isUnder(base: HttpUrl): Boolean {
    if (scheme != base.scheme || host != base.host || port != base.port) return false
    val prefix = base.encodedPath.trimEnd('/')
    return encodedPath == prefix || encodedPath.startsWith("$prefix/")
}

/**
 * Adds the Immich token to requests for the Immich server only, and returns to login when
 * Immich rejects it. Requests to other hosts (e.g. the Picfort server) go out untouched.
 */
class AuthInterceptor @Inject constructor(
    private val tokenProvider: Provider<AccessTokenStore>,
    private val sessionManager: Provider<SessionManager>,
    private val serverUrlStore: Provider<ServerUrlStore>,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val server = serverUrlStore.get().serverUrl.toHttpUrlOrNull()
        if (server == null || !originalRequest.url.isUnder(server)) {
            return chain.proceed(originalRequest)
        }
        if (originalRequest.url.encodedPath.contains("/auth/login")) {
            return chain.proceed(originalRequest)
        }
        val token = tokenProvider.get().accessToken
        if (token.isEmpty()) {
            return chain.proceed(originalRequest) // e.g. checking a server before login
        }
        val request = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        val response = chain.proceed(request)
        if (response.code == 401) {
            sessionManager.get().onUnauthorized()
        }
        return response
    }
}

/** Family features on the Picfort server; the same list as the server's AUTH_PREFIXES (T-018). */
val PICFORT_FAMILY_PATHS = listOf("wall", "lists", "receipts", "spending", "events", "school", "family")

/**
 * Adds the Immich token to family-feature requests on the Picfort server (`<server>/wall…`,
 * `/lists…`, …; ADR 0006, 0008). Never to the frame-control endpoints. A 401 from the Picfort server does not log
 * out: it may simply use another Immich server.
 */
class PicfortAuthInterceptor @Inject constructor(
    private val tokenProvider: Provider<AccessTokenStore>,
    private val frameSettings: Provider<FrameSettingsStore>,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = normalizeFrameUrl(frameSettings.get().frameUrl)
        val token = tokenProvider.get().accessToken
        val family = base != null && PICFORT_FAMILY_PATHS.any { request.url.isUnder(base.newBuilder().addPathSegment(it).build()) }
        if (!family || token.isEmpty()) {
            return chain.proceed(request)
        }
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }
}
