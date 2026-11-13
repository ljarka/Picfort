package com.github.ljarka.picfort.core.ui.layout

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JustifiedLayoutTest {

    private val width = 1080f
    private val target = 270f // about 4 square photos per row
    private val spacing = 8f

    private fun rowWidth(row: JustifiedRow) = row.widths.sum() + spacing * (row.count - 1)

    private fun mixedRatios(n: Int, seed: Int = 1): List<Float> {
        val shapes = listOf(4f / 3, 3f / 4, 1f, 16f / 9, 9f / 16, 3f, 0.3f)
        val rnd = Random(seed)
        return List(n) { shapes[rnd.nextInt(shapes.size)] }
    }

    @Test
    fun `every row but the last fills the width exactly`() {
        repeat(20) { seed ->
            val rows = justify(mixedRatios(137, seed), width, target, spacing)
            rows.dropLast(1).forEach { assertEquals(width, rowWidth(it), 0.01f) }
        }
    }

    @Test
    fun `all items are placed once, in order`() {
        val rows = justify(mixedRatios(137), width, target, spacing)
        assertEquals((0 until 137).toList(), rows.flatMap { row -> (row.start until row.start + row.count) })
    }

    @Test
    fun `items keep their clamped aspect ratio`() {
        val ratios = mixedRatios(60)
        justify(ratios, width, target, spacing).forEach { row ->
            row.widths.dropLast(1).forEachIndexed { k, w ->
                assertEquals(clampRatio(ratios[row.start + k]), w / row.height, 0.001f)
            }
        }
    }

    @Test
    fun `row heights stay near the target`() {
        repeat(20) { seed ->
            justify(mixedRatios(200, seed), width, target, spacing).dropLast(1).forEach {
                assertTrue("height ${it.height}", it.height in target * 0.6f..target * 1.7f)
            }
        }
    }

    @Test
    fun `the last row is not stretched`() {
        val rows = justify(listOf(1f, 1f, 1f, 1f, 1f, 1f), width, target, spacing)
        val last = rows.last()
        assertEquals(target, last.height, 0.001f)
        assertTrue(rowWidth(last) < width)
    }

    @Test
    fun `panoramas and tall screenshots are clamped`() {
        assertEquals(MAX_RATIO, clampRatio(10f))
        assertEquals(MIN_RATIO, clampRatio(0.1f))
        assertEquals(1f, clampRatio(null))
        assertEquals(1f, clampRatio(0f))
        assertEquals(1f, clampRatio(Float.NaN))
    }

    @Test
    fun `an item wider than the row gets a row of its own`() {
        val rows = justify(listOf(2.5f, 1f, 1f), width = 500f, targetHeight = 300f, spacing = spacing)
        assertEquals(1, rows.first().count)
        assertEquals(500f, rows.first().widths.single(), 0.01f)
        assertEquals(200f, rows.first().height, 0.01f)
    }

    @Test
    fun `nothing to lay out gives no rows`() {
        assertTrue(justify(emptyList(), width, target, spacing).isEmpty())
        assertTrue(justify(listOf(1f), 0f, target, spacing).isEmpty())
    }
}
