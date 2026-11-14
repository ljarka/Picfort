package com.github.ljarka.picfort.network

import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** Read and write timeout of a backup upload (T-006); every other call keeps OkHttp's 10 s. */
const val UPLOAD_TIMEOUT_MINUTES = 5

/**
 * A backup upload on a slow uplink: OkHttp has "written" the body once the phone's send buffer
 * took it, and megabytes may still have to leave before Immich stores the file and answers.
 * With the default 10 s read timeout such an upload failed and the backup never got past it.
 */
class UploadTimeoutInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isAssetUpload(request)) return chain.proceed(request)
        return chain
            .withReadTimeout(UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .withWriteTimeout(UPLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .proceed(request)
    }
}

/** `POST …/api/assets`: the backup's upload (`BackupService.upload`). */
fun isAssetUpload(request: Request): Boolean =
    request.method == "POST" && request.url.encodedPath.trimEnd('/').endsWith("/api/assets")
