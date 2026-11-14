package com.github.ljarka.picfort.contract

import com.github.ljarka.picfort.assets.AssetInfo
import com.github.ljarka.picfort.collections.AlbumDto
import com.github.ljarka.picfort.collections.BulkIdResult
import com.github.ljarka.picfort.collections.PeopleResponse
import com.github.ljarka.picfort.collections.SearchResponse
import com.github.ljarka.picfort.collections.parseSearchDuration
import com.github.ljarka.picfort.sync.SyncChange
import com.github.ljarka.picfort.sync.SyncLine
import com.github.ljarka.picfort.sync.toChange
import com.github.ljarka.picfort.ui.timeline.BucketResponse
import com.github.ljarka.picfort.ui.timeline.TimeBucket
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real responses of Immich 3.3 (test account, personal data replaced) read with the app's own
 * classes and its Json settings. A server change that breaks parsing shows up here, not as
 * "Something went wrong" on a phone (T-061, T-062). Refresh the files when moving to a new major.
 */
class ImmichV3ContractTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun read(name: String): String =
        checkNotNull(javaClass.getResource("/immich-v3/$name")) { "missing fixture $name" }.readText()

    @Test
    fun albums() {
        val albums = json.decodeFromString<List<AlbumDto>>(read("albums.json"))
        assertTrue(albums.single().assetCount >= 2)
        assertEquals("T062 test", json.decodeFromString<AlbumDto>(read("album_create.json")).albumName)
        assertTrue(json.decodeFromString<List<BulkIdResult>>(read("album_add.json")).single().success)
    }

    @Test
    fun `timeline buckets of an album with a video`() {
        assertTrue(json.decodeFromString<List<TimeBucket>>(read("buckets.json")).isNotEmpty())
        val bucket = json.decodeFromString<BucketResponse>(read("bucket_album.json"))
        val video = bucket.isImage.indexOf(false)
        assertEquals(2692L, parseSearchDuration(bucket.duration[video]))
        assertEquals(bucket.id.size, bucket.ratio.size)
    }

    @Test
    fun `people and search`() {
        assertEquals("Osoba 01", json.decodeFromString<PeopleResponse>(read("people.json")).people.first().name)
        val videos = json.decodeFromString<SearchResponse>(read("search_meta_video.json")).assets.items
        assertEquals("VIDEO", videos.first().type)
        assertEquals(2692L, parseSearchDuration(videos.first().duration))
        assertTrue(json.decodeFromString<SearchResponse>(read("search_smart.json")).assets.items.isNotEmpty())
    }

    @Test
    fun `asset details`() {
        val photo = json.decodeFromString<AssetInfo>(read("asset_photo.json"))
        assertEquals(listOf("Osoba 01", "Osoba 02"), photo.people.map { it.name }.sorted())
        val video = json.decodeFromString<AssetInfo>(read("asset_video.json"))
        assertEquals("VIDEO", video.type)
        assertEquals(320.0, video.exifInfo?.exifImageWidth)
    }

    @Test
    fun `sync stream lines, video duration in milliseconds (T-015)`() {
        val changes = read("sync.jsonl").lines().filter { it.isNotBlank() }
            .map { json.decodeFromString<SyncLine>(it).toChange(json) }
        val video = changes.filterIsInstance<SyncChange.UpsertAsset>().single().asset
        assertTrue(video.isVideo)
        assertEquals(2692L, video.duration) // a 2.7 s clip
        assertEquals("T062_video.mp4", video.originalFileName)
        assertTrue(changes.any { it is SyncChange.SetUser })
        assertTrue(changes.any { it is SyncChange.UpsertUser })
        assertTrue(changes.any { it is SyncChange.DeleteAsset })
    }
}
