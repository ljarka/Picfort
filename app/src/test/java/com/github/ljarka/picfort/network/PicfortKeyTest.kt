package com.github.ljarka.picfort.network

import com.github.ljarka.picfort.core.network.PICFORT_KEY_HEADER
import com.github.ljarka.picfort.core.network.picfortKey
import com.github.ljarka.picfort.core.network.picfortKeyFor
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Picfort server's API key goes only to the saved Picfort server, frame and wall alike. */
class PicfortKeyTest {

    private fun get(url: String, key: String? = null) =
        Request.Builder().url(url).apply { key?.let { picfortKey(it) } }.build()

    @Test
    fun `added to every call on the saved server`() {
        val server = "https://picfort.example.com"
        listOf("/status", "/run?assetId=x", "/current", "/wall?limit=1", "/wall/photo/a.jpg").forEach {
            assertEquals(it, "k", picfortKeyFor(get(server + it), server, "k"))
        }
        assertEquals("k", picfortKeyFor(get("http://192.168.0.105:5050/status"), "192.168.0.105:5050", "k"))
    }

    @Test
    fun `never to other hosts or without a key`() {
        val server = "https://picfort.example.com"
        assertNull(picfortKeyFor(get("https://photos.example.com/api/assets"), server, "k"))
        assertNull(picfortKeyFor(get("http://picfort.example.com/status"), server, "k"))
        assertNull(picfortKeyFor(get("$server/status"), server, ""))
        assertNull(picfortKeyFor(get("$server/status"), "", "k"))
    }

    @Test
    fun `key being tested wins over the saved one`() {
        val server = "https://picfort.example.com"
        val request = get("$server/status", key = " new ")
        assertNull(picfortKeyFor(request, server, "old"))
        assertEquals("new", request.header(PICFORT_KEY_HEADER))
        assertNull(get("$server/status", key = "  ").header(PICFORT_KEY_HEADER))
    }
}
