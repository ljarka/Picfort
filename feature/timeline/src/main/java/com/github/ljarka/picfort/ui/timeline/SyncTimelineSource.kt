package com.github.ljarka.picfort.ui.timeline

import com.github.ljarka.picfort.core.database.ImagesDatabase
import com.github.ljarka.picfort.core.database.RemoteAssetEntity
import com.github.ljarka.picfort.core.network.AccessTokenStore
import com.github.ljarka.picfort.core.network.ServerUrlStore
import com.github.ljarka.picfort.sync.SyncEngine
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

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

    /** The timeline's Immich assets, newest first; follows every change to the copy. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun assets(): Flow<List<RemoteAssetEntity>> =
        dao.syncStateFlow().map { it?.userId.orEmpty() }.distinctUntilChanged()
            .flatMapLatest { userId -> dao.timelineAll(userId) }
}
