package com.github.ljarka.immich.android.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class MonthRangeTest {

    private val warsaw = ZoneId.of("Europe/Warsaw")

    private fun millis(dateTime: String, zone: ZoneId = warsaw) =
        LocalDateTime.parse(dateTime).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `includes the whole first and last day`() {
        val range = monthRangeMillis(YearMonth.of(2025, 3), warsaw)
        assertTrue(millis("2025-03-01T00:00:00") in range)
        assertTrue(millis("2025-03-01T00:05:00") in range)
        assertTrue(millis("2025-03-31T23:59:59") in range)
    }

    @Test
    fun `excludes neighbouring months`() {
        val range = monthRangeMillis(YearMonth.of(2025, 3), warsaw)
        assertFalse(millis("2025-02-28T23:59:59") in range)
        assertFalse(millis("2025-04-01T00:00:00") in range)
    }

    @Test
    fun `consecutive months touch without overlap`() {
        val march = monthRangeMillis(YearMonth.of(2025, 3), warsaw)
        val april = monthRangeMillis(YearMonth.of(2025, 4), warsaw)
        assertEquals(march.last + 1, april.first)
    }

    @Test
    fun `handles leap February and DST change`() {
        val feb = monthRangeMillis(YearMonth.of(2024, 2), warsaw)
        assertTrue(millis("2024-02-29T12:00:00") in feb)
        // March 2025 has 31 days minus one hour (clocks go forward on the 30th).
        val march = monthRangeMillis(YearMonth.of(2025, 3), warsaw)
        assertEquals((31L * 24 - 1) * 3_600_000, march.last + 1 - march.first)
    }
}
