package com.github.ljarka.picfort.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Immich token goes only to Immich, and to the Picfort server only under /wall (ADR 0006). */
class AuthScopeTest {

    @Test
    fun `same origin and path prefix`() {
        val immich = "https://photos.example.com".toHttpUrl()
        assertTrue("https://photos.example.com/api/assets".toHttpUrl().isUnder(immich))
        assertFalse("http://photos.example.com/api/assets".toHttpUrl().isUnder(immich))
        assertFalse("https://photos.example.com:8443/api".toHttpUrl().isUnder(immich))
        assertFalse("http://192.168.0.105:5000/wall".toHttpUrl().isUnder(immich))
    }

    @Test
    fun `wall but not frame control`() {
        val wall = "http://192.168.0.105:5000/wall".toHttpUrl()
        assertTrue("http://192.168.0.105:5000/wall".toHttpUrl().isUnder(wall))
        assertTrue("http://192.168.0.105:5000/wall/photo/abc.jpg".toHttpUrl().isUnder(wall))
        assertTrue("http://192.168.0.105:5000/wall?limit=1".toHttpUrl().isUnder(wall))
        assertFalse("http://192.168.0.105:5000/wallpaper".toHttpUrl().isUnder(wall))
        assertFalse("http://192.168.0.105:5000/run?assetId=x".toHttpUrl().isUnder(wall))
        assertFalse("http://192.168.0.105:5000/status".toHttpUrl().isUnder(wall))
    }

    @Test
    fun `immich under a path`() {
        val immich = "https://example.com/immich".toHttpUrl()
        assertTrue("https://example.com/immich/api/users/me".toHttpUrl().isUnder(immich))
        assertFalse("https://example.com/other/api".toHttpUrl().isUnder(immich))
    }
}
