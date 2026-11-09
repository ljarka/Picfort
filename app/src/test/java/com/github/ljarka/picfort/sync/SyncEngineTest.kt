package com.github.ljarka.picfort.sync

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response
import java.time.Instant

// Real lines from Immich v3.3.5 (T-015 live check); the user's name and email are replaced.
private const val USER = """{"type":"AuthUserV2","data":{"id":"6fedb4d3-2637-4654-817e-bd4d56a6ed73","name":"Test","email":"test@example.com","avatarColor":null,"deletedAt":null,"profileChangedAt":"2026-11-09T17:51:36.681Z","isAdmin":false,"pinCode":null,"oauthId":null,"storageLabel":null,"quotaSizeInBytes":1073741824,"quotaUsageInBytes":5028373,"hasProfileImage":false},"ack":"AuthUserV2|01a1c184-b9bb-7be2-ad12-714222c76a81"}"""
private const val ASSET = """{"type":"AssetV2","data":{"id":"d5f2a01e-252f-490e-882e-52fe509f58ec","ownerId":"6fedb4d3-2637-4654-817e-bd4d56a6ed73","originalFileName":"sample_2.jpg","fileCreatedAt":"2024-01-26T21:10:32.000Z","fileModifiedAt":"2026-11-05T10:00:00.000Z","createdAt":"2026-11-09T17:59:15.275Z","localDateTime":"2024-01-26T21:10:32.000Z","type":"IMAGE","deletedAt":null,"isFavorite":false,"visibility":"timeline","duration":null,"livePhotoVideoId":null,"stackId":null,"libraryId":null,"width":1600,"height":1600,"isEdited":false,"checksum":"e+V65Ekjsu/qpWBvtCBvE+qB8RA=","thumbhash":"rLUBDwDspepayIZXSLiHMDeGlZ+4xu0P"},"ack":"AssetV2|01a1c176-df3a-7ea9-a2f2-11c5b77dca1d"}"""
private const val TRASHED = """{"type":"AssetV2","data":{"id":"f99767c1-09e5-4300-915e-bda95b6dc66e","ownerId":"6fedb4d3-2637-4654-817e-bd4d56a6ed73","originalFileName":"gen_0000.jpg","fileCreatedAt":"2026-11-01T00:00:00.000Z","fileModifiedAt":"2026-11-01T00:00:00.000Z","createdAt":"2026-11-09T18:13:52.524Z","localDateTime":"2026-11-01T00:00:00.000Z","type":"IMAGE","deletedAt":"2026-11-09T20:02:13.811Z","isFavorite":false,"visibility":"timeline","duration":null,"livePhotoVideoId":null,"stackId":null,"libraryId":null,"width":480,"height":360,"isEdited":false,"checksum":"naSgzXOPQAeAgncytKmJZu89XJM=","thumbhash":"KtYCDYJPi38fXFvEp7mVuI9/+Pd2"},"ack":"AssetV2|01a1c1e7-74b3-7b40-a596-1b72d572185f"}"""
private const val ARCHIVED = """{"type":"AssetV2","data":{"id":"7f4bdf1c-1cd4-49f7-9b76-a650864a38c7","ownerId":"6fedb4d3-2637-4654-817e-bd4d56a6ed73","originalFileName":"gen_0001.jpg","fileCreatedAt":"2026-10-27T16:00:00.000Z","fileModifiedAt":"2026-10-27T16:00:00.000Z","createdAt":"2026-11-09T18:13:52.655Z","localDateTime":"2026-10-27T16:00:00.000Z","type":"IMAGE","deletedAt":null,"isFavorite":false,"visibility":"archive","duration":null,"livePhotoVideoId":null,"stackId":null,"libraryId":null,"width":360,"height":480,"isEdited":false,"checksum":"RzT1ICn/F27mEGDGH4WDSF6j4PM=","thumbhash":"53YBPQyXeEe2mPaJm4h3iJ+vCQu9"},"ack":"AssetV2|01a1c1e7-7509-734e-88ef-0c8e3db0d44d"}"""
private const val DELETE = """{"type":"AssetDeleteV1","data":{"assetId":"f99767c1-09e5-4300-915e-bda95b6dc66e"},"ack":"AssetDeleteV1|01a1c1e7-ab71-7159-8c5b-e00b3bc131d7"}"""
private const val COMPLETE = """{"type":"SyncCompleteV1","data":{},"ack":"SyncCompleteV1|01a1c1e7-0a19-7c05-82e5-01b0ef803aec"}"""
private const val OWNER = "https://photos.example.com|session-1"
private const val RESET = """{"type":"SyncResetV1","data":{},"ack":"SyncResetV1|reset"}"""

class SyncEngineTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** Records every call in order, so tests can check "apply before ack". */
    private class Recorder {
        val events = mutableListOf<String>()
    }

    private class FakeApi(private val recorder: Recorder, vararg streams: String) : SyncApi {
        private val queue = ArrayDeque(streams.toList())
        val requests = mutableListOf<SyncStreamRequest>()
        val acks = mutableListOf<List<String>>()
        var failAcks = false

        override suspend fun stream(body: SyncStreamRequest): ResponseBody {
            requests += body
            recorder.events += "stream(reset=${body.reset})"
            return queue.removeFirst().toResponseBody()
        }

        override suspend fun ack(body: SyncAckRequest): Response<Unit> {
            recorder.events += "ack${body.acks}"
            acks += body.acks
            return if (failAcks) Response.error(500, "".toResponseBody()) else Response.success(Unit)
        }
    }

    private class FakeStore(private val recorder: Recorder, var owner: String? = OWNER) : SyncStore {
        val applied = mutableListOf<List<SyncChange>>()
        var failNextApply = false

        override suspend fun owner() = owner

        override suspend fun setOwner(owner: String) {
            recorder.events += "setOwner"
            this.owner = owner
        }

        override suspend fun apply(changes: List<SyncChange>) {
            if (failNextApply) throw IllegalStateException("disk full")
            recorder.events += "apply(${changes.size})"
            applied += changes
        }

        override suspend fun clear() {
            recorder.events += "clear"
            owner = null
        }
    }

    private fun lines(vararg l: String) = l.joinToString("\n", postfix = "\n")

    private fun assetLine(i: Int) = ASSET
        .replace("d5f2a01e-252f-490e-882e-52fe509f58ec", "00000000-0000-4000-8000-%012d".format(i))
        .replace("AssetV2|01a1c176-df3a-7ea9-a2f2-11c5b77dca1d", "AssetV2|ack-%05d".format(i))

    @Test
    fun `real asset line maps to an entity`() {
        val change = json.decodeFromString<SyncLine>(ASSET).toChange(json) as SyncChange.UpsertAsset
        with(change.asset) {
            assertEquals("d5f2a01e-252f-490e-882e-52fe509f58ec", id)
            assertEquals(Instant.parse("2024-01-26T21:10:32Z").toEpochMilli(), localDateTime)
            assertEquals("timeline", visibility)
            assertNull(deletedAt)
            assertEquals(1600, width)
            assertFalse(isVideo)
            assertEquals("rLUBDwDspepayIZXSLiHMDeGlZ+4xu0P", thumbhash)
        }
    }

    @Test
    fun `trash, archive and permanent delete map as seen live`() {
        val trashed = json.decodeFromString<SyncLine>(TRASHED).toChange(json) as SyncChange.UpsertAsset
        assertEquals(Instant.parse("2026-11-09T20:02:13.811Z").toEpochMilli(), trashed.asset.deletedAt)
        val archived = json.decodeFromString<SyncLine>(ARCHIVED).toChange(json) as SyncChange.UpsertAsset
        assertEquals("archive", archived.asset.visibility)
        assertEquals(
            SyncChange.DeleteAsset("f99767c1-09e5-4300-915e-bda95b6dc66e"),
            json.decodeFromString<SyncLine>(DELETE).toChange(json),
        )
    }

    @Test
    fun `a stream is applied, then the latest ack of each type is sent`() = runBlocking {
        val recorder = Recorder()
        val api = FakeApi(recorder, lines(USER, ASSET, TRASHED, ARCHIVED, DELETE, COMPLETE))
        val store = FakeStore(recorder)

        val result = SyncEngine(api, store, json).sync(OWNER)

        assertEquals(6, result.lines)
        assertFalse(result.wasReset)
        assertEquals(listOf("stream(reset=false)", "apply(5)"), recorder.events.take(2))
        assertEquals(
            listOf(
                "AuthUserV2|01a1c184-b9bb-7be2-ad12-714222c76a81",
                "AssetV2|01a1c1e7-7509-734e-88ef-0c8e3db0d44d", // the last AssetV2, not the first
                "AssetDeleteV1|01a1c1e7-ab71-7159-8c5b-e00b3bc131d7",
                "SyncCompleteV1|01a1c1e7-0a19-7c05-82e5-01b0ef803aec",
            ),
            api.acks.single(),
        )
        assertEquals(TIMELINE_SYNC_TYPES, api.requests.single().types)
    }

    @Test
    fun `large streams are applied and acked batch by batch`() = runBlocking {
        val recorder = Recorder()
        val count = SYNC_BATCH * 2 + 7
        val api = FakeApi(recorder, lines(*Array(count) { assetLine(it) }, COMPLETE))
        val store = FakeStore(recorder)

        SyncEngine(api, store, json).sync(OWNER)

        assertEquals(listOf(SYNC_BATCH, SYNC_BATCH, 7), store.applied.map { it.size })
        // Each batch's ack is its own last line, sent right after that batch was stored.
        assertEquals(
            listOf(
                "apply($SYNC_BATCH)", "ack[AssetV2|ack-%05d]".format(SYNC_BATCH - 1),
                "apply($SYNC_BATCH)", "ack[AssetV2|ack-%05d]".format(2 * SYNC_BATCH - 1),
                "apply(7)", "ack[AssetV2|ack-%05d, ${COMPLETE.substringAfter("\"ack\":\"").substringBefore('"')}]".format(count - 1),
            ),
            recorder.events.drop(1),
        )
    }

    @Test
    fun `nothing is acked when storing fails`() = runBlocking {
        val recorder = Recorder()
        val api = FakeApi(recorder, lines(USER, ASSET, COMPLETE))
        val store = FakeStore(recorder).apply { failNextApply = true }

        try {
            SyncEngine(api, store, json).sync(OWNER)
            fail("expected the store's error")
        } catch (e: IllegalStateException) {
            assertEquals("disk full", e.message)
        }
        assertTrue(api.acks.isEmpty())
    }

    @Test
    fun `a failed ack is an error`() = runBlocking {
        val recorder = Recorder()
        val api = FakeApi(recorder, lines(ASSET, COMPLETE)).apply { failAcks = true }
        try {
            SyncEngine(api, FakeStore(recorder), json).sync(OWNER)
            fail("expected an HttpException")
        } catch (e: retrofit2.HttpException) {
            assertEquals(500, e.code())
        }
    }

    @Test
    fun `a reset from the server clears the copy, acks the reset and syncs again`() = runBlocking {
        val recorder = Recorder()
        val api = FakeApi(recorder, lines(RESET), lines(USER, ASSET, COMPLETE))
        val store = FakeStore(recorder)

        val result = SyncEngine(api, store, json).sync(OWNER)

        assertTrue(result.wasReset)
        assertEquals(3, result.lines)
        assertEquals(
            listOf("stream(reset=false)", "clear", "ack[SyncResetV1|reset]", "setOwner", "stream(reset=false)", "apply(2)"),
            recorder.events.take(6),
        )
        assertEquals(OWNER, store.owner)
    }

    @Test
    fun `a client-requested reset clears the copy first`() = runBlocking {
        val recorder = Recorder()
        val api = FakeApi(recorder, lines(USER, ASSET, COMPLETE))

        val result = SyncEngine(api, FakeStore(recorder), json).sync(OWNER, reset = true)

        assertEquals(listOf("clear", "setOwner", "stream(reset=true)"), recorder.events.take(3))
        assertTrue(api.requests.single().reset)
        assertTrue(result.wasReset)
    }

    @Test
    fun `an empty or someone else's copy is rebuilt from scratch`() = runBlocking {
        for (stored in listOf(null, "https://photos.example.com|session-0")) {
            val recorder = Recorder()
            val api = FakeApi(recorder, lines(USER, ASSET, COMPLETE))
            val store = FakeStore(recorder, owner = stored)

            SyncEngine(api, store, json).sync(OWNER)

            assertEquals(listOf("clear", "setOwner", "stream(reset=true)"), recorder.events.take(3))
            assertEquals(OWNER, store.owner)
        }
    }

    @Test
    fun `the same owner gets a delta sync`() = runBlocking {
        val recorder = Recorder()
        val api = FakeApi(recorder, lines(COMPLETE))

        val result = SyncEngine(api, FakeStore(recorder), json).sync(OWNER)

        assertEquals(listOf("stream(reset=false)"), recorder.events.take(1))
        assertFalse(result.wasReset)
    }

    @Test
    fun `unknown and ack-only lines are acked but not stored`() = runBlocking {
        val recorder = Recorder()
        val future = """{"type":"SomethingNewV9","data":{"x":1},"ack":"SomethingNewV9|abc"}"""
        val backfillDone = """{"type":"SyncAckV1","data":{},"ack":"PartnerAssetBackfillV2|id|complete"}"""
        val api = FakeApi(recorder, lines(future, backfillDone, COMPLETE))
        val store = FakeStore(recorder)

        SyncEngine(api, store, json).sync(OWNER)

        assertTrue(store.applied.isEmpty())
        assertEquals(
            listOf(
                "SomethingNewV9|abc",
                "PartnerAssetBackfillV2|id|complete",
                "SyncCompleteV1|01a1c1e7-0a19-7c05-82e5-01b0ef803aec",
            ),
            api.acks.single(),
        )
    }
}
