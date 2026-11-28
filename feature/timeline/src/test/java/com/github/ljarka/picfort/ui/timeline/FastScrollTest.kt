package com.github.ljarka.picfort.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Test

class FastScrollTest {
    @Test
    fun `the pill picks the nearest month and never leaves the track`() {
        assertEquals(0, fastScrollIndex(0f, 10f, 5))
        assertEquals(1, fastScrollIndex(14f, 10f, 5))
        assertEquals(2, fastScrollIndex(16f, 10f, 5))
        assertEquals(4, fastScrollIndex(1_000f, 10f, 5))
        assertEquals(0, fastScrollIndex(-50f, 10f, 5))
    }

    @Test
    fun `no months or no track is month zero`() {
        assertEquals(0, fastScrollIndex(30f, 10f, 0))
        assertEquals(0, fastScrollIndex(30f, 0f, 3))
    }
}
