package com.github.ljarka.picfort.network

import com.github.ljarka.picfort.core.network.normalizeFrameUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    private fun norm(s: String) = normalizeServerUrl(s)?.toString()

    @Test
    fun `adds http for lan hosts and https otherwise`() {
        assertEquals("http://192.168.0.10:2283/", norm("192.168.0.10:2283"))
        assertEquals("http://immich.local:2283/", norm(" immich.local:2283/ "))
        assertEquals("https://photos.example.com/", norm("photos.example.com"))
    }

    @Test
    fun `keeps explicit scheme and strips trailing api`() {
        assertEquals("http://photos.example.com/", norm("http://photos.example.com"))
        assertEquals("https://photos.example.com/", norm("https://photos.example.com/api/"))
    }

    @Test
    fun `picfort server address gets https unless it is on the local network`() {
        fun frame(s: String) = normalizeFrameUrl(s)?.toString()
        assertEquals("https://picfort.example.com/", frame("picfort.example.com"))
        assertEquals("https://picfort.example.com:8443/", frame("picfort.example.com:8443/"))
        assertEquals("https://8.8.8.8:5000/", frame("8.8.8.8:5000"))
        assertEquals("http://192.168.0.105:5000/", frame("192.168.0.105:5000"))
        assertEquals("http://frame.local:5000/", frame("frame.local:5000"))
        assertEquals("http://nas:5000/", frame("nas:5000"))
        assertEquals("http://[fd00::5]:5000/", frame("[fd00::5]:5000"))
        assertEquals("http://picfort.example.com/", frame("http://picfort.example.com"))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(norm(""))
        assertNull(norm("ftp://host"))
        assertNull(norm("http://"))
    }
}
