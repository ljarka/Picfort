package com.github.ljarka.picfort.ui.timeline

import android.net.Uri
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.database.RemoteAssetEntity
import com.github.ljarka.picfort.local.ImageData
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineIndexTest {

    private val warsaw = ZoneId.of("Europe/Warsaw")
    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun remote(id: String, wallClock: String, w: Int? = 4, h: Int? = 3) = RemoteAssetEntity(
        id = id, ownerId = "me", localDateTime = t(wallClock), fileCreatedAt = null, isVideo = false,
        visibility = "timeline", deletedAt = null, width = w, height = h, thumbhash = "th-$id",
        duration = null, livePhotoVideoId = null, stackId = null, isFavorite = false, checksum = "c",
    )

    private fun local(id: Long, instant: String, w: Int = 3, h: Int = 4) = ImageData(
        id = id,
        uri = Uri.EMPTY,
        fileName = "$id.jpg",
        dateTaken = t(instant),
        width = w,
        height = h,
    )

    @Test
    fun `remote and phone-only media are merged newest first and grouped by month`() {
        val index = buildTimelineIndex(
            remote = listOf(remote("r-oct", "2026-10-20T12:00:00Z"), remote("r-sep", "2026-09-05T08:00:00Z")),
            local = listOf(local(1, "2026-10-25T10:00:00Z"), local(2, "2026-09-30T10:00:00Z")),
            onServerLocalIds = emptySet(),
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
            onServerLocalIds = setOf(1L),
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
            onServerLocalIds = emptySet(),
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

    @Test
    fun `hiding non-photos skips screenshots, non-camera images and documents but keeps photos and videos`() {
        val camPhoto = remote("cam", "2026-10-20T12:00:00Z")
        val noExifPhoto = remote("no-exif", "2026-10-19T12:00:00Z")
        val receiptPhoto = remote("receipt", "2026-10-18T12:00:00Z")
        val screenshotPhoto = remote("shot", "2026-10-17T12:00:00Z").copy(originalFileName = "Screenshot_2026.png")
        val videoNoExif = remote("vid", "2026-10-16T12:00:00Z").copy(isVideo = true)
        val localCam = local(1, "2026-10-15T10:00:00Z").copy(bucketName = "Camera")
        val localShot = local(2, "2026-10-14T10:00:00Z").copy(bucketName = "Screenshots")
        val localWhatsapp = local(3, "2026-10-13T10:00:00Z").copy(bucketName = "WhatsApp Images")
        val localVideo = local(4, "2026-10-12T10:00:00Z").copy(isVideo = true, bucketName = "Screenshots")

        val filtered = buildTimelineIndex(
            remote = listOf(camPhoto, noExifPhoto, receiptPhoto, screenshotPhoto, videoNoExif),
            local = listOf(localCam, localShot, localWhatsapp, localVideo),
            onServerLocalIds = emptySet(),
            zone = warsaw,
            hideNonPhotos = true,
            nonCameraIds = setOf("no-exif", "vid"),
            documentIds = setOf("receipt"),
        )
        assertEquals(listOf("cam", "vid", "1", "4"), filtered.months.single().assets.map { it.id })

        val unfiltered = buildTimelineIndex(
            remote = listOf(camPhoto, noExifPhoto, receiptPhoto, screenshotPhoto, videoNoExif),
            local = listOf(localCam, localShot, localWhatsapp, localVideo),
            onServerLocalIds = emptySet(),
            zone = warsaw,
            hideNonPhotos = false,
            nonCameraIds = setOf("no-exif", "vid"),
            documentIds = setOf("receipt"),
        )
        assertEquals(9, unfiltered.size)
    }

    @Test
    fun `best of the month keeps Immich photos in the best set, newest first, between min and max`() {
        val photos = List(BEST_PER_MONTH + 3) { remote("b$it", "2026-10-%02dT12:00:00Z".format(25 - it)) }
        val video = remote("bv", "2026-10-28T12:00:00Z").copy(isVideo = true)
        val other = remote("plain", "2026-10-27T12:00:00Z")
        val index = buildTimelineIndex(
            remote = photos + video + other,
            local = listOf(local(9, "2026-10-26T10:00:00Z")),
            onServerLocalIds = emptySet(),
            zone = warsaw,
            bestIds = photos.map { it.id }.toSet() + "bv" + "9",
        )
        val month = index.months.single()
        val best = month.best.map { month.assets[it].id }
        assertEquals(photos.take(BEST_PER_MONTH).map { it.id }, best)
    }

    @Test
    fun `too few best photos show no strip, hidden photos don't count`() {
        val a = remote("a", "2026-10-20T12:00:00Z")
        val b = remote("b", "2026-10-19T12:00:00Z")
        val shot = remote("c", "2026-10-18T12:00:00Z").copy(originalFileName = "Screenshot_1.png")
        fun bestCount(hide: Boolean) = buildTimelineIndex(
            remote = listOf(a, b, shot),
            local = emptyList(),
            onServerLocalIds = emptySet(),
            zone = warsaw,
            hideNonPhotos = hide,
            bestIds = setOf("a", "b", "c"),
        ).months.single().best.size
        assertEquals(BEST_MIN, bestCount(hide = false))
        assertEquals(0, bestCount(hide = true))
    }

    @Test
    fun `the photo AI's answer beats the EXIF and OCR guesses, unanalyzed photos keep the guesses`() {
        val whatsapp = remote("whatsapp", "2026-10-20T12:00:00Z") // no camera EXIF, AI: photo
        val sign = remote("sign", "2026-10-19T12:00:00Z") // OCR hit, AI: photo
        val shot = remote("shot", "2026-10-18T12:00:00Z") // camera EXIF, AI: screenshot
        val guessed = remote("guessed", "2026-10-17T12:00:00Z") // no EXIF, not analyzed
        val ai = aiLabelsOf(
            listOf(
                com.github.ljarka.picfort.core.database.AiLabelEntity("whatsapp", "photo", false),
                com.github.ljarka.picfort.core.database.AiLabelEntity("sign", "photo", false),
                com.github.ljarka.picfort.core.database.AiLabelEntity("shot", "screenshot", true),
            ),
        )
        val index = buildTimelineIndex(
            remote = listOf(whatsapp, sign, shot, guessed),
            local = emptyList(),
            onServerLocalIds = emptySet(),
            zone = warsaw,
            hideNonPhotos = true,
            nonCameraIds = setOf("whatsapp", "guessed"),
            documentIds = setOf("sign"),
            ai = ai,
        )
        assertEquals(listOf("whatsapp", "sign"), index.months.single().assets.map { it.id })
        assertEquals(setOf("shot"), ai.sensitive)
        assertEquals(setOf("shot"), ai.nonPhotos)
    }
}
