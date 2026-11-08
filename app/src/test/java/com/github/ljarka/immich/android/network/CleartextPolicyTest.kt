package com.github.ljarka.immich.android.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextPolicyTest {

    @Test
    fun `private and loopback addresses are local`() {
        listOf(
            "192.168.0.105", "10.0.0.2", "172.16.4.1", "172.31.255.255", "127.0.0.1",
            "169.254.10.10", "100.101.102.103", "localhost", "[::1]", "fe80::1", "fd12:3456::1",
        ).forEach { assertTrue(it, isLocalNetworkHost(it)) }
    }

    @Test
    fun `lan and vpn names are local`() {
        listOf("immich", "frame.local", "nas.lan", "pi.home.arpa", "immich.tail1234.ts.net")
            .forEach { assertTrue(it, isLocalNetworkHost(it)) }
    }

    @Test
    fun `public hosts are not local`() {
        listOf(
            "photos.example.com", "8.8.8.8", "172.32.0.1", "192.169.0.1", "100.128.0.1",
            "2001:4860::8888", "local.example.com", "evil-lan.com", "1.2.3", "300.1.1.1",
        ).forEach { assertFalse(it, isLocalNetworkHost(it)) }
    }
}
