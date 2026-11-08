package com.github.ljarka.immich.android.network

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
    fun `rejects garbage`() {
        assertNull(norm(""))
        assertNull(norm("ftp://host"))
        assertNull(norm("http://"))
    }
}
