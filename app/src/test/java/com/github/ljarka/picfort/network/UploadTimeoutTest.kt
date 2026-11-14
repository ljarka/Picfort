package com.github.ljarka.picfort.network

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** T-006: only the backup's upload waits longer than OkHttp's 10 s. */
class UploadTimeoutTest {
    private fun request(method: String, url: String) = Request.Builder().url(url)
        .method(method, if (method == "POST") "x".toRequestBody() else null)
        .build()

    @Test
    fun `the upload waits longer`() {
        assertTrue(isAssetUpload(request("POST", "https://photos.example.com/api/assets")))
        assertTrue(isAssetUpload(request("POST", "http://192.168.0.5:2283/api/assets/")))
    }

    @Test
    fun `nothing else does`() {
        assertFalse(isAssetUpload(request("GET", "https://photos.example.com/api/assets")))
        assertFalse(isAssetUpload(request("POST", "https://photos.example.com/api/assets/bulk-upload-check")))
        assertFalse(isAssetUpload(request("POST", "https://photos.example.com/api/search/metadata")))
    }
}
