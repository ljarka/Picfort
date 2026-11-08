package com.github.ljarka.picfort.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationTest {

    @Test
    fun `parses immich durations`() {
        assertEquals(12_345L, parseImmichDuration("0:00:12.345000"))
        assertEquals(3_725_000L, parseImmichDuration("1:02:05.000000"))
    }

    @Test
    fun `images and garbage have no duration`() {
        assertNull(parseImmichDuration(null))
        assertNull(parseImmichDuration("0:00:00.00000"))
        assertNull(parseImmichDuration("abc"))
    }

    @Test
    fun `formats like a video player`() {
        assertEquals("0:07", formatDuration(7_900))
        assertEquals("1:15", formatDuration(75_000))
        assertEquals("1:02:05", formatDuration(3_725_000))
    }
}
