package com.github.ljarka.picfort.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

private const val ME = "me"
private const val PARTNER_SHOWN = "partner-shown"
private const val PARTNER_HIDDEN = "partner-hidden"

/** The timeline filter of ADR 0005 (rule 6), in real SQLite. */
@RunWith(AndroidJUnit4::class)
class RemoteAssetsDaoTest {

    private lateinit var db: ImagesDatabase
    private lateinit var dao: RemoteAssetsDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, ImagesDatabase::class.java,
        ).build()
        dao = db.remoteAssetsDao()
    }

    @After
    fun tearDown() = db.close()

    private fun t(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun asset(
        id: String,
        at: String = "2026-10-15T12:00:00Z",
        owner: String = ME,
        visibility: String = "timeline",
        deletedAt: Long? = null,
        stackId: String? = null,
        hasDate: Boolean = true,
    ) = RemoteAssetEntity(
        id = id, ownerId = owner, localDateTime = if (hasDate) t(at) else null, fileCreatedAt = null,
        isVideo = false, visibility = visibility, deletedAt = deletedAt, width = 4, height = 3,
        thumbhash = null, duration = null, livePhotoVideoId = null, stackId = stackId,
        isFavorite = false, checksum = "c-$id",
    )

    @Test
    fun timelineShowsOnlyWhatTheUserShouldSee() = runBlocking {
        dao.upsertPartners(listOf(PartnerEntity(PARTNER_SHOWN, true), PartnerEntity(PARTNER_HIDDEN, false)))
        dao.upsertStacks(listOf(RemoteStackEntity("stack", primaryAssetId = "stack-primary", ownerId = ME)))
        dao.upsertAssets(
            listOf(
                asset("mine"),
                asset("archived", visibility = "archive"),
                asset("hidden-live-photo-video", visibility = "hidden"),
                asset("locked", visibility = "locked"),
                asset("trashed", deletedAt = t("2026-11-01T00:00:00Z")),
                asset("partner-in-timeline", owner = PARTNER_SHOWN),
                asset("partner-not-in-timeline", owner = PARTNER_HIDDEN),
                asset("stranger", owner = "someone-else"),
                asset("stack-primary", stackId = "stack"),
                asset("stack-member", stackId = "stack"),
                asset("stack-unknown", stackId = "stack-not-synced-yet"),
                asset("no-date", hasDate = false),
            )
        )

        val ids = dao.timelineAssets(ME, t("2026-10-01T00:00:00Z"), t("2026-11-01T00:00:00Z")).map { it.id }

        assertEquals(
            setOf("mine", "partner-in-timeline", "stack-primary", "stack-unknown"),
            ids.toSet(),
        )
    }

    @Test
    fun monthsAreCountedInUtcNewestFirst() = runBlocking {
        dao.upsertAssets(
            listOf(
                asset("oct-first-second", at = "2026-10-01T00:00:00Z"),
                asset("oct-last-second", at = "2026-10-31T23:59:59Z"),
                asset("sep", at = "2026-09-15T08:00:00Z"),
                asset("archived-sep", at = "2026-09-16T08:00:00Z", visibility = "archive"),
                asset("2024", at = "2024-01-26T21:10:32Z"),
            )
        )

        val months = dao.monthCounts(ME).first()

        assertEquals(
            listOf(
                RemoteMonthCount(t("2026-10-01T00:00:00Z"), 2),
                RemoteMonthCount(t("2026-09-01T00:00:00Z"), 1),
                RemoteMonthCount(t("2024-01-01T00:00:00Z"), 1),
            ),
            months,
        )
    }

    @Test
    fun monthAssetsAreNewestFirstAndBoundedByTheMonth() = runBlocking {
        dao.upsertAssets(
            listOf(
                asset("a", at = "2026-10-02T10:00:00Z"),
                asset("b", at = "2026-10-20T10:00:00Z"),
                asset("next-month", at = "2026-11-01T00:00:00Z"),
                asset("prev-month", at = "2026-09-30T23:59:59Z"),
            )
        )

        val ids = dao.timelineAssets(ME, t("2026-10-01T00:00:00Z"), t("2026-11-01T00:00:00Z")).map { it.id }

        assertEquals(listOf("b", "a"), ids)
    }

    @Test
    fun deletingUnknownIdsIsHarmless() = runBlocking {
        dao.upsertAssets(listOf(asset("kept")))
        dao.deleteAssets(listOf("never-synced", "also-unknown"))
        assertEquals(1, dao.countAll())
    }

    @Test
    fun upsertReplacesTheRow() = runBlocking {
        dao.upsertAssets(listOf(asset("x")))
        dao.upsertAssets(listOf(asset("x", visibility = "archive")))
        assertEquals(1, dao.countAll())
        assertEquals(0, dao.timelineAssets(ME, 0, Long.MAX_VALUE).size)
    }
}
