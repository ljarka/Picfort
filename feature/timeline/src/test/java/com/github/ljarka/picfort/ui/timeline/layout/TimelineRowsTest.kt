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
        index,
        Viewport(width, height, spacing = 8f),
        titleOf = { "month $it" },
        urlOf = { "url/${it.id}" },
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
            TimelineIndex(
                listOf(
                    TimelineMonth(monthKey(nov[0].dateTaken), 0, nov),
                    TimelineMonth(monthKey(oct[0].dateTaken), 9, oct),
                ),
            ),
            Viewport(1080f, 2000f, spacing = 8f),
            titleOf = { "month $it" },
            urlOf = { it.id },
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

    @Test
    fun `best tiles keep the photo's shape, extremes are cropped`() {
        val ratios = listOf(3f / 4, 4f / 3, 3f, 0.3f)
        val month = TimelineMonth(
            1L,
            0,
            ratios.mapIndexed { i, r -> TimelineAsset("a$i", AssetType.REMOTE, 1L, r) },
            best = ratios.indices.toList(),
        )
        val best = layoutTimeline(
            TimelineIndex(listOf(month)),
            Viewport(1080f, 2000f, spacing = 8f),
            titleOf = { "" },
            urlOf = { it.id },
        ).rows.filterIsInstance<BestRow>().single()
        val h = best.height
        // Panorama: cropped to 1.8, then capped at 85% of the width so the next photo peeks in.
        assertEquals(listOf(h * 3f / 4, h * 4f / 3, 1080f * 0.85f, h * 0.5f), best.tiles.map { it.width })
    }

    @Test
    fun `a best strip sits under its month header and the day jump still lands on the header`() {
        val nov = (days.rows.first() as MonthHeaderRow).month
        val withBest = layoutTimeline(
            TimelineIndex(
                listOf(
                    TimelineMonth(
                        nov,
                        0,
                        List(3) { TimelineAsset("2025-11-20-$it", AssetType.REMOTE, at("2025-11-20", 20 - it), 1f) },
                        best = listOf(0, 2),
                    ),
                ),
            ),
            Viewport(1080f, 2000f, spacing = 8f),
            titleOf = { "month $it" },
            urlOf = { it.id },
        )
        assertTrue(withBest.rows[0] is MonthHeaderRow)
        val best = withBest.rows[1] as BestRow
        assertEquals(listOf(0, 2), best.tiles.map { it.index })
        assertEquals(1080f * 0.5f, best.height, 0.5f)
        assertTrue(best.tiles.all { it.width == best.height }) // square photos stay square
        assertEquals(0, withBest.rowIndexOf(LocalDate.parse("2025-11-20")))
        assertEquals(withBest.rows.size, withBest.rows.map { it.key }.toSet().size)
    }

    @Test
    fun `fast scroll markers still point at month headers when months have best strips`() {
        fun m(key: Long, start: Int, n: Int, best: List<Int>) = TimelineMonth(
            key,
            start,
            List(n) { TimelineAsset("$key-$it", AssetType.REMOTE, key, 1f) },
            best = best,
        )
        val l = layoutTimeline(
            TimelineIndex(
                listOf(m(3L, 0, 9, listOf(0, 1, 2)), m(2L, 9, 4, emptyList()), m(1L, 13, 6, listOf(1, 3, 5))),
            ),
            Viewport(1080f, 2000f, spacing = 8f),
            titleOf = { "month $it" },
            urlOf = { it.id },
        )
        l.months.forEach { assertEquals(MonthHeaderRow(it.month, "month ${it.month}"), l.rows[it.rowIndex]) }
        // Every row after a marker, up to the next one, belongs to that month (the pill's label while scrolling).
        l.months.forEachIndexed { i, marker ->
            val end = l.months.getOrNull(i + 1)?.rowIndex ?: l.rows.size
            l.rows.subList(marker.rowIndex, end).forEach { row ->
                val month = when (row) {
                    is MonthHeaderRow -> row.month
                    is BestRow -> row.month
                    is TilesRow -> row.month
                }
                assertEquals(marker.month, month)
            }
        }
        assertEquals(listOf(true, false, true), l.months.map { l.rows[it.rowIndex + 1] is BestRow })
    }

    @Test
    fun `the backdrop is the first Best photo of the month at the top, none without a strip`() {
        fun m(key: Long, start: Int, best: List<Int>) = TimelineMonth(
            key,
            start,
            List(6) { TimelineAsset("$key-$it", AssetType.REMOTE, key, 1f) },
            best = best,
        )
        val l = layoutTimeline(
            TimelineIndex(listOf(m(3L, 0, listOf(2, 4, 5)), m(2L, 6, emptyList()), m(1L, 12, listOf(1, 3, 4)))),
            Viewport(1080f, 2000f, spacing = 8f),
            titleOf = { "month $it" },
            urlOf = { "url/${it.id}" },
        )
        val (march, feb, jan) = l.months
        assertEquals("url/3-2", l.backdropUrlAt(march.rowIndex))
        assertEquals("url/3-2", l.backdropUrlAt(feb.rowIndex - 1)) // still March's rows
        assertNull(l.backdropUrlAt(feb.rowIndex)) // February has no strip
        assertEquals("url/1-1", l.backdropUrlAt(jan.rowIndex + 2))
        assertNull(TimelineLayout.EMPTY.backdropUrlAt(0))
    }
}
