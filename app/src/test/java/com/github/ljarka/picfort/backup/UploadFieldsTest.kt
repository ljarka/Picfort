package com.github.ljarka.picfort.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UploadFieldsTest {

    private val video = LocalMedia(
        id = 7,
        fileName = "VID_1.mp4",
        mimeType = "video/mp4",
        size = 1234,
        dateTaken = 1_700_000_000_000,
        dateModified = 1_700_000_100_000,
        isVideo = true,
        durationMs = 75_500,
    )

    @Test
    fun `v1 sends device fields and legacy duration`() {
        val f = uploadFields(video, serverMajor = 1, deviceId = "dev")
        assertEquals("2023-11-14T22:13:20Z", f["fileCreatedAt"])
        assertEquals("2023-11-14T22:15:00Z", f["fileModifiedAt"])
        assertEquals("VID_1.mp4-1234", f["deviceAssetId"])
        assertEquals("dev", f["deviceId"])
        assertEquals("0:01:15.500000", f["duration"])
        assertFalse("metadata" in f)
    }

    @Test
    fun `v2 also sends metadata`() {
        assertEquals("[]", uploadFields(video, serverMajor = 2, deviceId = "dev")["metadata"])
    }

    @Test
    fun `v3 drops device fields and uses milliseconds`() {
        val f = uploadFields(video, serverMajor = 3, deviceId = "dev")
        assertEquals(setOf("fileCreatedAt", "fileModifiedAt", "filename", "duration"), f.keys)
        assertEquals("75500", f["duration"])
    }

    @Test
    fun `photos have no duration and fall back to modified date`() {
        val photo = video.copy(isVideo = false, durationMs = null, dateTaken = 0)
        val f = uploadFields(photo, serverMajor = 3, deviceId = "dev")
        assertFalse("duration" in f)
        assertEquals("2023-11-14T22:15:00Z", f["fileCreatedAt"])
    }

    @Test
    fun `checksum is sha1 hex`() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", sha1Hex("abc".byteInputStream()))
    }
}
