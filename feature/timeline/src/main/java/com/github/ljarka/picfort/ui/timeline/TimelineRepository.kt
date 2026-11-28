package com.github.ljarka.picfort.ui.timeline

import android.content.Context
import com.github.ljarka.picfort.UrlProvider
import com.github.ljarka.picfort.core.database.BackupDao
import com.github.ljarka.picfort.core.network.AppScope
import com.github.ljarka.picfort.local.ImageData
import com.github.ljarka.picfort.local.LocalImagesProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import retrofit2.HttpException

private const val TIMELINE_PREFS = "timeline_prefs"
private const val KEY_HIDE_NON_PHOTOS = "hide_non_photos"
private const val KEY_SHOW_BEST = "show_best_of_month"

private class Merged(val index: TimelineIndex, val onServer: Set<Long>)

private data class NonPhotoFilter(
    val nonCameraIds: Set<String>,
    val documentIds: Set<String>,
    val ai: AiLabels,
    val enabled: Boolean,
)

/** The phone's media and what Picfort's backup knows of it: Immich ids and SHA-1s (hex). */
private class LocalSnapshot(
    val media: List<ImageData>,
    val backupLinks: Map<Long, String>,
    val checksums: Map<Long, String>,
)

/**
 * The whole timeline as one in-memory [TimelineIndex]: the synced Immich copy (Room) merged
 * with the phone's media (MediaStore). It is rebuilt when either changes; nothing is fetched
 * while scrolling.
 */
@Singleton
class TimelineRepository @Inject constructor(
    @AppScope coroutineScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val remote: SyncTimelineSource,
    private val urlProvider: UrlProvider,
    private val backupDao: BackupDao,
) {
    private val prefs = context.getSharedPreferences(TIMELINE_PREFS, Context.MODE_PRIVATE)
    private val _hideNonPhotos = MutableStateFlow(prefs.getBoolean(KEY_HIDE_NON_PHOTOS, false))

    /** When true, screenshots, non-camera images and OCR-detected documents are hidden (T-088). */
    val hideNonPhotos: StateFlow<Boolean> = _hideNonPhotos.asStateFlow()

    fun setHideNonPhotos(enabled: Boolean) {
        _hideNonPhotos.value = enabled
        prefs.edit().putBoolean(KEY_HIDE_NON_PHOTOS, enabled).apply()
    }

    private val _showBest = MutableStateFlow(prefs.getBoolean(KEY_SHOW_BEST, true))

    /** "Best of the month" strips at the top of each month (T-094). */
    val showBest: StateFlow<Boolean> = _showBest.asStateFlow()

    fun setShowBest(enabled: Boolean) {
        _showBest.value = enabled
        prefs.edit().putBoolean(KEY_SHOW_BEST, enabled).apply()
    }

    /** Null until the phone's media has been read once. */
    private val local = MutableStateFlow<LocalSnapshot?>(null)

    private val merged: StateFlow<Merged?> = combine(
        remote.assets(),
        local.filterNotNull(),
        combine(
            remote.nonCameraAssetIds(),
            remote.documentAssetIds(),
            remote.aiLabels(),
            _hideNonPhotos,
            ::NonPhotoFilter,
        ),
        combine(remote.bestAssetIds(), _showBest) { ids, show -> if (show) ids else emptySet() },
    ) { assets, snapshot, filter, bestIds ->
        val zone = ZoneId.systemDefault()
        val onServer = localIdsOnServer(assets, snapshot.media, snapshot.backupLinks, snapshot.checksums, zone)
        Merged(
            index = buildTimelineIndex(
                remote = assets,
                local = snapshot.media,
                onServerLocalIds = onServer,
                zone = zone,
                hideNonPhotos = filter.enabled,
                nonCameraIds = filter.nonCameraIds,
                documentIds = filter.documentIds,
                ai = filter.ai,
                bestIds = bestIds,
            ),
            onServer = onServer,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(coroutineScope, SharingStarted.Eagerly, null)

    /** Null until the first build; then the current timeline. */
    val index: StateFlow<TimelineIndex?> =
        merged.map { it?.index }.stateIn(coroutineScope, SharingStarted.Eagerly, null)

    /**
     * Phone media already in Immich, however it got there (see [localIdsOnServer]); null until the
     * phone's media has been read. Settings counts backed-up items with it.
     */
    val phoneOnServer: Flow<Set<Long>?> = merged.map { it?.onServer }

    /** Reads the phone's media if nothing has yet (e.g. Settings opened before Photos). No network. */
    suspend fun loadPhoneMediaIfNeeded() {
        if (local.value == null) reloadLocal()
    }

    fun getAssetsCount(): Int = index.value?.size ?: 0

    /** For the full-screen viewer, which counts assets across the whole timeline. */
    fun getAsset(position: Int): AssetUi? = index.value?.get(position)?.toAssetUi()

    fun thumbnailUrl(asset: TimelineAsset): String = urlProvider.getThumbnail(asset.id, asset.type, asset.isVideo)

    /**
     * Syncs the Immich copy, then re-reads the phone's media. Offline, the phone's media is
     * still re-read (and the copy shown) before the network error is thrown.
     */
    suspend fun refresh() {
        val syncError = try {
            remote.refresh()
            null
        } catch (e: IOException) {
            e
        } catch (e: HttpException) {
            e
        }
        reloadLocal()
        syncError?.let { throw it }
    }

    private suspend fun reloadLocal() {
        val media = LocalImagesProvider().getAll(context)
        val rows = backupDao.getAll()
        local.value = LocalSnapshot(
            media = media,
            backupLinks = rows.mapNotNull { row -> row.remoteId?.let { row.localId to it } }.toMap(),
            checksums = rows.associate { it.localId to it.checksum },
        )
    }

    private fun TimelineAsset.toAssetUi() = AssetUi(
        url = thumbnailUrl(this),
        id = id,
        type = type,
        isVideo = isVideo,
        durationMs = durationMs,
    )
}
