package com.github.ljarka.picfort.ui.timeline.layout

import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.ui.timeline.TimelineAsset
import com.github.ljarka.picfort.ui.timeline.TimelineIndex
import com.github.ljarka.picfort.ui.timeline.TimelineMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineRowsTest {

    private fun month(key: Long, start: Int, n: Int, ratio: Float = 1f) =
        TimelineMonth(key, start, List(n) { TimelineAsset("$key-$it", AssetType.REMOTE, key, ratio) })

    private val index = TimelineIndex(listOf(month(2L, 0, 10), month(1L, 10, 3, ratio = 1.5f)))

    private fun layout(width: Float = 1080f, height: Float = 2000f) = layoutTimeline(
        index, Viewport(width, height, spacing = 8f), titleOf = { "month $it" }, urlOf = { "url/${it.id}" },
    )

    @Test
    fun `each month starts with its header and markers point at it`() {
        val l = layout()
        l.months.forEach { assertEquals(MonthHeaderRow(it.month, "month ${it.month}"), l.rows[it.rowIndex]) }
        assertEquals(listOf(2L, 1L), l.months.map { it.month })
    }

    @Test
    fun `tiles carry their global index and url, in order`() {
        val tiles = layout().rows.filterIsInstance<TilesRow>().flatMap { it.tiles }
        assertEquals((0 until 13).toList(), tiles.map { it.index })
        assertEquals("url/2-0", tiles.first().url)
        assertEquals("url/1-2", tiles.last().url)
    }

    @Test
    fun `portrait shows about 4 squares per row, landscape about 7`() {
        val portrait = layout(width = 1080f, height = 2000f).rows.filterIsInstance<TilesRow>().first()
        val landscape = layout(width = 2000f, height = 1080f).rows.filterIsInstance<TilesRow>().first()
        assertEquals(4, portrait.tiles.size)
        assertEquals(7, landscape.tiles.size)
    }

    @Test
    fun `row keys are unique`() {
        val keys = layout().rows.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `no width, no layout`() {
        assertTrue(layout(width = 0f).rows.isEmpty())
    }
}
