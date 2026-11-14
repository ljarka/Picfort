package com.github.ljarka.picfort.network

import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugLoggingTest {
    private fun request(path: String, body: okhttp3.RequestBody? = null) =
        Request.Builder().url("https://photos.example.com$path").method(
            if (body ==
                null
            ) {
                "GET"
            } else {
                "POST"
            },
            body,
        ).build()

    @Test
    fun `API calls are logged in full`() {
        assertTrue(logsBody(request("/api/albums")))
        assertTrue(logsBody(request("/api/search/smart", """{"query":"dog"}""".toRequestBody())))
    }

    @Test
    fun `uploads, big bodies and big downloads only by headers`() {
        val upload = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("assetData", "v.mp4", ByteArray(10).toRequestBody()).build()
        assertFalse(logsBody(request("/api/assets", upload)))
        assertFalse(logsBody(request("/api/x", ByteArray(100_000).toRequestBody())))
        assertFalse(logsBody(request("/api/assets/1/original")))
        assertFalse(logsBody(request("/api/assets/1/thumbnail")))
        assertFalse(logsBody(request("/api/assets/1/video/playback")))
        assertFalse(logsBody(request("/api/sync/stream", "{}".toRequestBody())))
    }
}
