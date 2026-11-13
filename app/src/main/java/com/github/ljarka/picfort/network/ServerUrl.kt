package com.github.ljarka.picfort.network

import com.github.ljarka.picfort.core.network.defaultSchemeFor
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Turns what people type ("192.168.0.10:2283", "photos.example.com/",
 * "https://host/api") into a base URL. Without a scheme, LAN hosts get
 * http:// (Immich's default) and everything else https://.
 */
fun normalizeServerUrl(input: String): HttpUrl? {
    val hasScheme = "://" in input
    var text = input.trim().trimEnd('/')
    if (text.isEmpty()) return null
    text = text.removeSuffix("/api")
    if (!hasScheme) text = defaultSchemeFor(text) + text
    return text.toHttpUrlOrNull()?.takeIf { it.scheme == "http" || it.scheme == "https" }
}
