package com.github.ljarka.picfort.ui.frame

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FrameDashboardTest {

    @Test
    fun `interval labels`() {
        assertEquals(listOf("15 min", "1 hour", "6 hours", "1 day"), listOf(15, 60, 360, 1440).map(::intervalLabel))
    }

    @Test
    fun `next change label uses local time and hides when off`() {
        val zone = ZoneId.of("Europe/Warsaw")
        val at = LocalDateTime.of(2025, 7, 15, 14, 30).atZone(zone).toEpochSecond().toDouble()
        assertEquals("Next photo Tue 14:30", nextChangeLabel(SlideshowState(mode = "album", nextChangeAt = at), zone))
        assertNull(nextChangeLabel(SlideshowState(mode = "off", nextChangeAt = at), zone))
    }

    @Test
    fun `parses frame status`() {
        val status = Json { ignoreUnknownKeys = true }.decodeFromString<FrameStatus>(
            """{"status":"ok","busy":true,"currentAsset":"a","lastError":null,"hardware":false,"width":1200,
               "slideshow":{"mode":"memories","album_id":null,"interval_minutes":360,"quiet_start":22,"quiet_end":7,
               "next_change_at":1752582600.5,"last_error":null,"modes":["off"],"intervals":[15,60]}}"""
        )
        assertEquals(true, status.busy)
        assertEquals("memories", status.slideshow?.mode)
        assertEquals(22 to 7, status.slideshow?.quietStart to status.slideshow?.quietEnd)
        assertEquals(listOf(15, 60), status.slideshow?.intervals)
    }
}
