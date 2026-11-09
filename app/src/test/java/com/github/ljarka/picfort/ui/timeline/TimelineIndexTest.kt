package com.github.ljarka.picfort.ui.timeline

import android.net.Uri
import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.db.RemoteAssetEntity
import com.github.ljarka.picfort.local.ImageData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class TimelineIndexTest {

    private val warsaw = ZoneId.of("Europe/Warsaw")
    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun remote(id: String, wallClock: String, w: Int? = 4, h: Int? = 3) = RemoteAssetEntity(
        id = id, ownerId = "me", localDateTime = t(wallClock), fileCreatedAt = null, isVideo = false,
        visibility = "timeline", deletedAt = null, width = w, height = h, thumbhash = "th-$id",
        duration = null, livePhotoVideoId = null, stackId = null, isFavorite = false, checksum = "c",
    )

    private fun local(id: Long, instant: String, w: Int = 3, h: Int = 4) = ImageData(
        id = id, uri = Uri.EMPTY, fileName = "$id.jpg", dateTaken = t(instant), width = w, height = h,
    )

    @Test
    fun `remote and phone-only media are merged newest first and grouped by month`() {
        val index = buildTimelineIndex(
            remote = listOf(remote("r-oct", "2026-10-20T12:00:00Z"), remote("r-sep", "2026-09-05T08:00:00Z")),
            local = listOf(local(1, "2026-10-25T10:00:00Z"), local(2, "2026-09-30T10:00:00Z")),
            backedUpLocalIds = emptySet(),
            zone = warsaw,
        )
        assertEquals(listOf(t("2026-10-01T00:00:00Z"), t("2026-09-01T00:00:00Z")), index.months.map { it.key })
        assertEquals(listOf("1", "r-oct"), index.months[0].assets.map { it.id })
        assertEquals(listOf("2", "r-sep"), index.months[1].assets.map { it.id })
        assertEquals(listOf(0, 2), index.months.map { it.start })
        assertEquals(4, index.size)
    }

    @Test
    fun `phone photos already backed up show once, as the Immich copy`() {
        val index = buildTimelineIndex(
            remote = listOf(remote("r", "2026-10-20T12:00:00Z")),
            local = listOf(local(1, "2026-10-20T10:00:00Z"), local(2, "2026-10-21T10:00:00Z")),
            backedUpLocalIds = setOf(1L),
            zone = warsaw,
        )
        assertEquals(listOf("2", "r"), index.months.single().assets.map { it.id })
    }

    @Test
    fun `phone dates become wall-clock time, so late evening stays in its month`() {
        // 23:30 in Warsaw on 31 October is 22:30 UTC: still October on the wall clock.
        val index = buildTimelineIndex(emptyList(), listOf(local(1, "2026-10-31T22:30:00Z")), emptySet(), warsaw)
        assertEquals(t("2026-10-01T00:00:00Z"), index.months.single().key)
        assertEquals(t("2026-10-31T23:30:00Z"), index[0]!!.dateTaken)
    }

    @Test
    fun `ratios, types and thumbhash are carried over`() {
        val index = buildTimelineIndex(
            remote = listOf(remote("r", "2026-10-20T12:00:00Z"), remote("unknown", "2026-10-19T12:00:00Z", null, null)),
            local = listOf(local(1, "2026-10-18T10:00:00Z", w = 3, h = 4)),
            backedUpLocalIds = emptySet(),
            zone = warsaw,
        )
        val (r, unknown, phone) = index.months.single().assets
        assertEquals(4f / 3, r.ratio!!, 0.0001f)
        assertEquals("th-r", r.thumbhash)
        assertEquals(AssetType.REMOTE, r.type)
        assertNull(unknown.ratio)
        assertEquals(0.75f, phone.ratio!!, 0.0001f)
        assertEquals(AssetType.LOCAL, phone.type)
    }

    @Test
    fun `global positions map to the right month`() {
        val remote = (1..10).map { remote("r$it", "2026-%02d-15T12:00:00Z".format(it)) } +
            remote("r10b", "2026-10-16T12:00:00Z")
        val index = buildTimelineIndex(remote, emptyList(), emptySet(), warsaw)
        assertEquals(listOf("r10b", "r10", "r9"), (0..2).map { index[it]!!.id })
        assertEquals("r1", index[index.size - 1]!!.id)
        assertNull(index[index.size])
        assertNull(index[-1])
    }
}
