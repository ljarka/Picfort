package com.github.ljarka.picfort.ui.shopping

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ReceiptReading(val shop: String? = null, val date: String? = null, val totalMinor: Long? = null)

@Serializable
private data class RawReceiptReading(val shop: String? = null, val date: String? = null, val total: String? = null)

/**
 * Parses and validates the JSON returned by Gemini Nano. Model output is untrusted: invalid
 * totals or out-of-window dates are discarded as null.
 */
object ReceiptReadingParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val fenceRegex = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

    fun parse(rawText: String, today: LocalDate = LocalDate.now()): ReceiptReading? {
        val candidate = fenceRegex.find(rawText)?.groupValues?.getOrNull(1) ?: rawText
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val jsonSlice = candidate.substring(start, end + 1)
        val raw = runCatching { json.decodeFromString<RawReceiptReading>(jsonSlice) }.getOrNull() ?: return null
        val shop = raw.shop?.trim()?.takeIf { it.isNotEmpty() }?.take(100)
        val date = raw.date?.trim()?.let { validDateOrNull(it, today) }
        val totalMinor = raw.total?.let { parseTotalMinor(it) }
        return ReceiptReading(shop = shop, date = date, totalMinor = totalMinor)
    }

    private fun validDateOrNull(value: String, today: LocalDate): String? {
        val parsed = runCatching { LocalDate.parse(value) }.getOrNull() ?: return null
        val minDate = today.minusYears(1)
        val maxDate = today.plusDays(1)
        return if (!parsed.isBefore(minDate) && !parsed.isAfter(maxDate)) parsed.toString() else null
    }
}
