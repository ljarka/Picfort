package com.github.ljarka.picfort.collections

import com.github.ljarka.picfort.ui.timeline.BucketResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumBucketTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `album bucket with a video parses, duration in milliseconds (Immich v3)`() {
        // Trimmed from a real v3.3 response: an album with a video and a photo.
        val bucket = json.decodeFromString<BucketResponse>(
            """{"duration":[2692,null],"id":["v","p"],"isImage":[false,true],
               "fileCreatedAt":["2026-11-14T08:02:29","2020-05-05T10:00:00"],"ratio":[0.5,1],
               "thumbhash":[null,null],"localOffsetHours":[0,0]}""",
        )
        assertEquals(2692L, parseSearchDuration(bucket.duration[0]))
        assertNull(parseSearchDuration(bucket.duration[1]))
    }

    @Test
    fun `older servers' text duration still parses`() {
        val bucket = json.decodeFromString<BucketResponse>(
            """{"duration":["0:00:03.000000"],"id":["v"],"isImage":[false],"fileCreatedAt":["x"],"ratio":[1.5]}""",
        )
        assertEquals(3000L, parseSearchDuration(bucket.duration[0]))
    }
}
