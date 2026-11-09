package com.github.ljarka.picfort.ui.timeline

import com.github.ljarka.picfort.db.AssetEntity
import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.db.ImagesDatabase
import com.github.ljarka.picfort.db.RemoteAssetEntity
import com.github.ljarka.picfort.sync.SyncEngine
import com.github.ljarka.picfort.ui.login.AccessTokenStore
import com.github.ljarka.picfort.ui.server.ServerUrlStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The timeline's Immich assets: a local copy kept up to date by the sync stream (ADR 0005).
 * Month keys are midnight UTC on the 1st. Servers older than v2 are not supported.
 */
@Singleton
class SyncTimelineSource @Inject constructor(
    private val engine: SyncEngine,
    database: ImagesDatabase,
    private val serverUrlStore: ServerUrlStore,
    private val accessTokenStore: AccessTokenStore,
) {
    private val dao = database.remoteAssetsDao()

    /**
     * Server and login session. Checkpoints live on the server per session; the token is
     * hashed so it isn't stored a second time.
     */
    private fun owner(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(accessTokenStore.accessToken.toByteArray())
        return serverUrlStore.serverUrl + "|" + digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Syncs with the server (throws on network errors; the copy stays usable offline).
     * Returns whether anything may have changed.
     */
    suspend fun refresh(): Boolean {
        val result = engine.sync(owner())
        return result.wasReset || result.lines > 1 // a stream with no changes is just SyncCompleteV1
    }

    private suspend fun userId() = dao.syncState()?.userId.orEmpty()

    suspend fun monthCounts(): Map<Long, Int> =
        dao.monthCounts(userId()).first().associate { it.month to it.count }

    /** Assets of [month], as rows for the `assets` table (index and order set by the caller). */
    suspend fun monthAssets(month: Long): List<AssetEntity> {
        val next = Instant.ofEpochMilli(month).atZone(ZoneOffset.UTC).plusMonths(1).toInstant().toEpochMilli()
        return dao.timelineAssets(userId(), month, next).map { it.toAssetEntity(month) }
    }
}

private fun RemoteAssetEntity.toAssetEntity(month: Long) = AssetEntity(
    timestamp = month,
    assetIndex = 0,
    assetId = id,
    ratio = if (width != null && height != null && width > 0 && height > 0) width.toDouble() / height else null,
    type = AssetType.REMOTE,
    dateTaken = localDateTime,
    isVideo = isVideo,
    // AssetV2.duration is an int; milliseconds assumed, not verified yet (T-015).
    durationMs = duration?.takeIf { isVideo && it > 0 },
    thumbhash = thumbhash,
)
