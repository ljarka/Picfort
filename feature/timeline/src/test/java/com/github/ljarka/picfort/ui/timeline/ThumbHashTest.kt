package com.github.ljarka.picfort.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ThumbHashTest {

    /** Hashes from Immich and the average colour of the real thumbnails they stand for. */
    @Test
    fun `decoded preview has the colours of the real thumbnail`() {
        assertAverageColor("Z1gBBwDsldtKx4ZpN7iHIDaHI3UA/GwK", r = 121, g = 205, b = 153) // green
        assertAverageColor("bboCBwL9pcla14ZYSKiHMDaHBEFZA7UH", r = 253, g = 160, b = 127) // orange
    }

    @Test
    fun `square image decodes to 32 by 32 and is opaque`() {
        val image = decodeThumbHash("Z1gBBwDsldtKx4ZpN7iHIDaHI3UA/GwK")!!
        assertEquals(32, image.width)
        assertEquals(32, image.height)
        assertTrue(image.argb.all { it ushr 24 == 0xFF })
    }

    @Test
    fun `bad input gives null`() {
        assertNull(decodeThumbHash("not base64!"))
        assertNull(decodeThumbHash("AAAA"))
        assertNotNull(decodeThumbHash("oxQCBQKuaO2ppWiGalVECaz8+vd3"))
    }

    private fun assertAverageColor(hash: String, r: Int, g: Int, b: Int) {
        val pixels = decodeThumbHash(hash)!!.argb
        val avg = listOf(16, 8, 0).map { shift -> pixels.sumOf { (it shr shift) and 0xFF } / pixels.size }
        listOf(r, g, b).zip(avg).forEach { (expected, actual) ->
            assertTrue("expected ~($r, $g, $b), got $avg", abs(expected - actual) <= 25)
        }
    }
}
