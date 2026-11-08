package com.github.ljarka.immich.android.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class CleartextNotAllowedException(host: String) :
    IOException("Unencrypted http:// is only allowed on your local network ($host). Use https://.")

/**
 * Self-hosted servers often live on http://192.168.x.x, so cleartext can't be
 * disabled globally (and Android's network security config can't express IP
 * ranges). Instead, plain HTTP is refused for anything that isn't a LAN,
 * loopback or VPN address, so passwords and tokens never cross the internet
 * unencrypted.
 */
class CleartextPolicyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!url.isHttps && !isLocalNetworkHost(url.host)) {
            throw CleartextNotAllowedException(url.host)
        }
        return chain.proceed(chain.request())
    }
}

private val LOCAL_SUFFIXES = listOf(
    ".local", ".lan", ".home", ".home.arpa", ".internal", ".localdomain",
    ".ts.net", // Tailscale MagicDNS: traffic is already encrypted by WireGuard
)

/** True for hosts that can only be reached on a local network or VPN. Never does DNS. */
fun isLocalNetworkHost(host: String): Boolean {
    val h = host.lowercase().trimEnd('.').removePrefix("[").removeSuffix("]")
    if (h.isEmpty()) return false
    if (h == "localhost") return true

    parseIpv4(h)?.let { (a, b) ->
        return a == 10 ||
            a == 127 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127) // CGNAT range, used by Tailscale
    }

    if (':' in h) { // IPv6 literal
        return h == "::1" ||
            h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb") ||
            h.startsWith("fc") || h.startsWith("fd")
    }

    if ('.' !in h) return true // single-label name, resolved by the local router
    return LOCAL_SUFFIXES.any { h.endsWith(it) }
}

private fun parseIpv4(host: String): Pair<Int, Int>? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    val octets = parts.map { p -> p.toIntOrNull()?.takeIf { it in 0..255 && p.isNotEmpty() } ?: return null }
    return octets[0] to octets[1]
}
