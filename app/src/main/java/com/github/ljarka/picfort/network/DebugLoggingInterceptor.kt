package com.github.ljarka.picfort.network

import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response

/** Bodies bigger than this are not logged. */
private const val MAX_LOGGED_BODY = 64 * 1024L

/** Responses that can be large: files, thumbnails, video, the sync stream (tens of MB at first). */
private val LARGE_RESPONSE = Regex("/(original|thumbnail|video/playback|sync/stream)$")

/**
 * Debug logging: whole bodies for API calls, headers only for uploads and large downloads.
 * Logging a body reads all of it into memory first, so a 200 MB video ran the app out of memory
 * mid-backup (T-064).
 */
class DebugLoggingInterceptor(private val bodies: Interceptor, private val headers: Interceptor) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        (if (logsBody(chain.request())) bodies else headers).intercept(chain)
}

/** False for uploads, bodies over 64 KB (or of unknown size) and large downloads. */
fun logsBody(request: Request): Boolean {
    if (LARGE_RESPONSE.containsMatchIn(request.url.encodedPath)) return false
    val body = request.body ?: return true
    if (body is MultipartBody) return false
    val length = body.contentLength()
    return length in 0..MAX_LOGGED_BODY
}
