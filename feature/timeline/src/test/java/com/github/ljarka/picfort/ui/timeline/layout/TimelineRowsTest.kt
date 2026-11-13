package com.github.ljarka.picfort.ui.timeline.layout

import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.ui.timeline.TimelineAsset
import com.github.ljarka.picfort.ui.timeline.TimelineIndex
import com.github.ljarka.picfort.ui.timeline.TimelineMonth
import com.github.ljarka.picfort.ui.timeline.monthKey
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `portrait rows are about 2,5 squares tall, landscape keeps the tile size`() {
        val portrait = layout(width = 1080f, height = 2000f).rows.filterIsInstance<TilesRow>().first()
        val landscape = layout(width = 2000f, height = 1080f).rows.filterIsInstance<TilesRow>().first()
        assertEquals((1080f - 8f * 1.5f) / 2.5f, portrait.height, portrait.height * 0.35f)
        assertTrue(portrait.tiles.size in 2..3)
        assertTrue(landscape.tiles.size in 4..5)
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

    // T-056: jump to a day. Three square photos a day: 20, 16 and 10 Nov 2025, then 5 Oct 2025.
    private fun at(day: String, hour: Int) =
        LocalDate.parse(day).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private val days = run {
        fun asset(day: String, k: Int) = TimelineAsset("$day-$k", AssetType.REMOTE, at(day, 20 - k), 1f)
        val nov = listOf("2025-11-20", "2025-11-16", "2025-11-10").flatMap { d -> List(3) { asset(d, it) } }
        val oct = List(3) { asset("2025-10-05", it) }
        layoutTimeline(
            TimelineIndex(listOf(TimelineMonth(monthKey(nov[0].dateTaken), 0, nov), TimelineMonth(monthKey(oct[0].dateTaken), 9, oct))),
            Viewport(1080f, 2000f, spacing = 8f), titleOf = { "month $it" }, urlOf = { it.id },
        )
    }

    private fun firstIdAt(row: Int) = (days.rows.drop(row).first { it is TilesRow } as TilesRow).tiles.first().asset.id

    @Test
    fun `a day scrolls to the row with its newest photo`() {
        val row = days.rowIndexOf(LocalDate.parse("2025-11-16"))!!
        assertTrue(days.rows[row] is TilesRow)
        assertTrue(days.rows[row].let { r -> (r as TilesRow).tiles.any { it.asset.id == "2025-11-16-0" } })
    }

    @Test
    fun `a day in the first row of its month scrolls to the month header`() {
        assertEquals(0, days.rowIndexOf(LocalDate.parse("2025-11-20")))
        val oct = days.rowIndexOf(LocalDate.parse("2025-10-05"))!!
        assertTrue(days.rows[oct] is MonthHeaderRow)
        assertEquals("2025-10-05-0", firstIdAt(oct))
    }

    @Test
    fun `a day without photos lands on the next older photos, past the end on the oldest`() {
        assertEquals(days.rowIndexOf(LocalDate.parse("2025-11-10")), days.rowIndexOf(LocalDate.parse("2025-11-12")))
        assertEquals(days.rowIndexOf(LocalDate.parse("2025-10-05")), days.rowIndexOf(LocalDate.parse("2020-01-01")))
        assertEquals(0, days.rowIndexOf(LocalDate.parse("2026-01-01")))
        assertNull(TimelineLayout.EMPTY.rowIndexOf(LocalDate.parse("2025-11-16")))
    }
}
