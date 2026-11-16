package com.github.ljarka.picfort.ui.shopping

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingReceiptTest {
    @Test
    fun parseTotalMinorAcceptsValidInputs() {
        assertEquals(1250L, parseTotalMinor("12,50"))
        assertEquals(1250L, parseTotalMinor("12.5"))
        assertEquals(1200L, parseTotalMinor("12"))
        assertEquals(123456L, parseTotalMinor(" 1 234,56 "))
        assertEquals(1L, parseTotalMinor("0.01"))
    }

    @Test
    fun parseTotalMinorRejectsInvalidInputs() {
        assertNull(parseTotalMinor(""))
        assertNull(parseTotalMinor("   "))
        assertNull(parseTotalMinor("0"))
        assertNull(parseTotalMinor("0.00"))
        assertNull(parseTotalMinor("-5.00"))
        assertNull(parseTotalMinor("12,555"))
        assertNull(parseTotalMinor("abc"))
        assertNull(parseTotalMinor("12.34 zł"))
        assertNull(parseTotalMinor("1000001"))
    }

    @Test
    fun formatTotalMinorUsesCurrency() {
        val formatted = formatTotalMinor(8410L, "PLN", Locale.US)
        assertEquals(true, formatted.contains("84.10"))
    }

    @Test
    fun receiptReadingParserParsesValidAndFencedJson() {
        val today = LocalDate.of(2026, 11, 16)
        val raw = """
            ```json
            {"shop": "  Biedronka  ", "date": "2026-11-15", "total": "1 284,50"}
            ```
        """.trimIndent()
        val parsed = ReceiptReadingParser.parse(raw, today)
        assertEquals(ReceiptReading(shop = "Biedronka", date = "2026-11-15", totalMinor = 128450L), parsed)
    }

    @Test
    fun receiptReadingParserRejectsOutOfWindowDateAndBadTotal() {
        val today = LocalDate.of(2026, 11, 16)
        val raw = """{"shop": "${"x".repeat(120)}", "date": "2020-01-01", "total": "45.2 zł"}"""
        val parsed = ReceiptReadingParser.parse(raw, today)
        assertEquals(100, parsed?.shop?.length)
        assertNull(parsed?.date)
        assertNull(parsed?.totalMinor)
    }

    @Test
    fun receiptReadingParserReturnsNullForGarbage() {
        assertNull(ReceiptReadingParser.parse("not json at all"))
    }
}
