package com.github.ljarka.picfort.core.network

import kotlinx.serialization.json.Json
import okhttp3.Response

/**
 * Raw body text and its best-effort JSON decode.
 * The body stream is consumed exactly once.
 */
data class ParsedResponseBody<T>(
    val text: String,
    val value: T?,
)

inline fun <reified T> Response.readParsedBody(json: Json): ParsedResponseBody<T> {
    val text = body.string()
    val value = if (text.isBlank()) null else runCatching { json.decodeFromString<T>(text) }.getOrNull()
    return ParsedResponseBody(text = text, value = value)
}
