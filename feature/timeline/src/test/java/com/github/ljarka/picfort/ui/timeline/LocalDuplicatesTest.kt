package com.github.ljarka.picfort.ui.timeline

import android.net.Uri
import com.github.ljarka.picfort.core.database.RemoteAssetEntity
import com.github.ljarka.picfort.local.ImageData
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDuplicatesTest {

    private val warsaw = ZoneId.of("Europe/Warsaw")
    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun remote(id: String, name: String?, wallClock: String, checksum: String = "c-$id") = RemoteAssetEntity(
        id = id, ownerId = "me", localDateTime = t(wallClock), fileCreatedAt = null, isVideo = false,
        visibility = "timeline", deletedAt = null, width = 4, height = 3, thumbhash = null,
        duration = null, livePhotoVideoId = null, stackId = null, isFavorite = false, checksum = checksum,
        originalFileName = name,
    )

    private fun local(id: Long, name: String, instant: String) =
        ImageData(id = id, uri = Uri.EMPTY, fileName = name, dateTaken = t(instant), width = 3, height = 4)

    private fun onServer(
        remote: List<RemoteAssetEntity>,
        local: List<ImageData>,
        links: Map<Long, String> = emptyMap(),
        checksums: Map<Long, String> = emptyMap(),
    ) = localIdsOnServer(remote, local, links, checksums, warsaw)

    @Test
    fun `photo uploaded by another app matches by name and wall-clock time`() {
        // 14:00 in Warsaw (CEST) is 12:00 UTC.
        val ids = onServer(
            remote = listOf(remote("r", "IMG_20260714_140000.jpg", "2026-07-14T14:00:00Z")),
            local = listOf(
                local(1, "img_20260714_140000.JPG", "2026-07-14T12:00:00.250Z"),
                local(2, "IMG_20260714_150000.jpg", "2026-07-14T13:00:00Z"),
            ),
        )
        assertEquals(setOf(1L), ids)
    }

    @Test
    fun `taken in another time zone still matches, other times with the same name don't`() {
        val ids = onServer(
            remote = listOf(remote("r", "DSC_0001.JPG", "2026-07-14T09:00:00Z")),
            local = listOf(
                local(1, "DSC_0001.JPG", "2026-07-14T12:00:00Z"), // 14:00 in Warsaw: 5 h shift
                local(2, "DSC_0001.JPG", "2026-07-14T12:07:00Z"), // a different photo
                local(3, "DSC_0001.JPG", "2025-07-14T07:00:00Z"), // a year earlier
            ),
        )
        assertEquals(setOf(1L), ids)
    }

    @Test
    fun `matches by checksum or backup link whatever the name`() {
        val sha1Hex = "7be57ae44923b2efeaa5606fb4206f13ea81f110"
        val ids = onServer(
            remote = listOf(
                remote("a", null, "2026-07-14T09:00:00Z", checksum = hexToBase64(sha1Hex)!!),
                remote("b", null, "2026-07-14T09:00:00Z"),
            ),
            local = listOf(
                local(1, "x.jpg", "2026-01-01T00:00:00Z"),
                local(2, "y.jpg", "2026-01-01T00:00:00Z"),
                local(3, "z.jpg", "2026-01-01T00:00:00Z"),
            ),
            links = mapOf(2L to "b", 3L to "gone"),
            checksums = mapOf(1L to sha1Hex, 3L to "00"),
        )
        assertEquals(setOf(1L, 2L), ids)
    }

    @Test
    fun `hex checksums convert to Immich's base64`() {
        assertEquals("e+V65Ekjsu/qpWBvtCBvE+qB8RA=", hexToBase64("7be57ae44923b2efeaa5606fb4206f13ea81f110"))
        assertNull(hexToBase64("abc"))
        assertNull(hexToBase64("zz"))
    }
}
