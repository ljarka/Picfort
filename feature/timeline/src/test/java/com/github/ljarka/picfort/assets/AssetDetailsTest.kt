package com.github.ljarka.picfort.assets

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssetDetailsTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `builds rows from exif`() {
        val info = AssetInfo(
            id = "x",
            originalFileName = "IMG_1234.HEIC",
            exifInfo = ExifInfo(
                make = "Apple", model = "iPhone 15 Pro", lensModel = "Main Camera",
                fNumber = 1.8, exposureTime = "1/120", focalLength = 6.86, iso = 100.0,
                exifImageWidth = 4032.0, exifImageHeight = 3024.0, fileSizeInByte = 2_621_440,
                dateTimeOriginal = "2025-07-14T16:05:00.000Z", timeZone = "Europe/Warsaw",
                city = "Kraków", state = "Lesser Poland", country = "Poland",
                latitude = 50.06, longitude = 19.94,
            ),
            people = listOf(PersonInfo("Ala"), PersonInfo("")),
        )
        val details = info.toDetails(utc)
        val rows = details.rows.associate { it.label to it.value }

        assertEquals("Mon, 14 Jul 2025, 18:05", rows["Taken"]) // shown in the photo's zone
        assertEquals("IMG_1234.HEIC", rows["File"])
        assertEquals("4032 × 3024 · 12.2 MP · 2.5 MB", rows["Size"])
        assertEquals("Apple iPhone 15 Pro", rows["Camera"])
        assertEquals("ƒ/1.8 · 1/120 s · 7 mm · ISO 100", rows["Exposure"])
        assertEquals("Kraków, Lesser Poland, Poland", rows["Place"])
        assertEquals("Ala", rows["People"])
        assertEquals(50.06 to 19.94, details.location)
    }

    @Test
    fun `handles missing exif`() {
        val details = AssetInfo(id = "x", fileCreatedAt = "2024-01-02T03:04:05Z").toDetails(utc)
        assertEquals(listOf(DetailRow("Taken", "Tue, 2 Jan 2024, 03:04")), details.rows)
        assertNull(details.location)
    }

    @Test
    fun `formats sizes and exposure`() {
        assertEquals("512 B", formatFileSize(512))
        assertEquals("3 KB", formatFileSize(3_000))
        assertEquals("ƒ/2 · ISO 50", formatExposure(ExifInfo(fNumber = 2.0, iso = 50.0)))
        assertEquals(
            "Apple iPhone",
            AssetInfo(id = "x", exifInfo = ExifInfo(make = "Apple", model = "Apple iPhone"))
                .toDetails(utc).rows.single().value,
        )
    }
}
