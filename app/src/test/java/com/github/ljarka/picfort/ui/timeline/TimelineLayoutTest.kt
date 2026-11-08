package com.github.ljarka.picfort.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TimelineLayoutTest {

    @Test
    fun `span follows aspect ratio`() {
        assertEquals(listOf(1, 2, 3, 4), listOf(0.75, 1.0, 1.4, 1.78).map(::calculateSpan))
    }

    @Test
    fun `rows are filled exactly`() {
        assertEquals(listOf(3, 1, 2, 2, 4), adjustSpans(listOf(3, 3, 2, 2, 4)))
        assertEquals(listOf(2, 2, 1, 3), adjustSpans(listOf(2, 4, 1, 4)))
        assertEquals(listOf(1, 1, 1, 1, 1), adjustSpans(listOf(1, 1, 1, 1, 1)))
    }

    @Test
    fun `every row except the last sums to the grid width`() {
        val random = java.util.Random(42)
        repeat(200) {
            val spans = adjustSpans(List(random.nextInt(40)) { 1 + random.nextInt(4) })
            var row = 0
            spans.forEachIndexed { i, s ->
                row += s
                assertTrue("row overflow in $spans", row <= GRID_COLUMNS)
                if (row == GRID_COLUMNS) row = 0
                else if (i == spans.lastIndex) assertTrue(row in 1 until GRID_COLUMNS)
            }
            assertEquals((spans.sum() + 3) / 4, rowCount(spans))
        }
    }

    @Test
    fun `parses all bucket date formats to the same instant`() {
        val expected = Instant.parse("2025-07-01T00:00:00Z")
        assertEquals(expected, parseBucketDate("2025-07-01T00:00:00.000Z"))
        assertEquals(expected, parseBucketDate("2025-07-01T00:00:00"))
        assertEquals(expected, parseBucketDate("2025-07-01"))
    }

    @Test
    fun `merges phone and immich months newest first`() {
        val local = mapOf(300L to 2, 200L to 0, 100L to 1)
        val remote = mapOf(300L to 5, 250L to 3, 50L to 0)
        assertEquals(listOf(300L to 7, 250L to 3, 100L to 1), mergeMonthCounts(local, remote))
    }
}
