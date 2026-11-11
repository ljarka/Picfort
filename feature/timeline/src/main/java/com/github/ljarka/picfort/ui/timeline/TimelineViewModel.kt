package com.github.ljarka.picfort.ui.timeline

import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.util.Log
import com.github.ljarka.picfort.assets.AssetActions
import com.github.ljarka.picfort.assets.SelectedAsset
import com.github.ljarka.picfort.collections.CollectionsRepository
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.model.FrameResult
import com.github.ljarka.picfort.core.model.FrameSender
import com.github.ljarka.picfort.sync.SyncEngine
import com.github.ljarka.picfort.core.network.AccessTokenStore
import com.github.ljarka.picfort.core.network.ServerUrlStore
import kotlinx.coroutines.flow.map
import android.util.LruCache
import com.github.ljarka.picfort.core.network.CleartextNotAllowedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import retrofit2.HttpException
import java.io.IOException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import com.github.ljarka.picfort.ui.timeline.layout.layoutTimeline
import com.github.ljarka.picfort.ui.timeline.layout.Viewport
import kotlinx.coroutines.flow.flowOn
import java.util.Locale
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.time.Instant

private const val TAG = "TimelineViewModel"

sealed interface TimelineEvent {
    data class Launch(val intent: Intent) : TimelineEvent
    data class ConfirmLocalTrash(val request: IntentSender) : TimelineEvent
}

@HiltViewModel
class TimelineViewModel @Inject constructor(
    val timelineRepository: TimelineRepository,
    private val assetActions: AssetActions,
    private val frameRepository: FrameSender,
    private val collections: CollectionsRepository,
    private val accessTokenStore: AccessTokenStore,
    private val serverUrlStore: ServerUrlStore,
    syncEngine: SyncEngine,
) : ViewModel(), TimelineGridModel {
    /** Lines applied by a running sync, for progress on a still-empty timeline; null when idle. */
    val syncLines: StateFlow<Int?> = syncEngine.linesApplied

    /** Shown on an empty timeline. Read once: logging out leaves this screen. */
    val account = TimelineAccount(accessTokenStore.userEmail, serverUrlStore.serverUrl)

    private val loadingState = MutableStateFlow(LoadingState.DEFAULT)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()
    private val viewport = MutableStateFlow<Viewport?>(null)

    /** The whole timeline laid out for the current width, off the main thread. */
    private val layout = combine(timelineRepository.index, viewport) { index, viewport ->
        if (index == null || viewport == null) null
        else layoutTimeline(index, viewport, ::formatMonth, timelineRepository::thumbnailUrl)
    }.flowOn(Dispatchers.Default)

    val state = combine(layout, loadingState) { layout, loadingState ->
        TimelineState(loadingState = loadingState, layout = layout)
    }.stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(5_000), TimelineState())

    override fun setViewport(viewport: Viewport) {
        this.viewport.value = viewport
    }

    private val _selection = MutableStateFlow<Map<String, SelectedAsset>>(emptyMap())
    val selection: StateFlow<Map<String, SelectedAsset>> = _selection.asStateFlow()
    override val selectedAssets: StateFlow<Set<String>> = _selection
        .map { it.keys }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _events = Channel<TimelineEvent>(Channel.BUFFERED)
    val events: Flow<TimelineEvent> = _events.receiveAsFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    override fun selectAsset(asset: TimelineAsset, month: Long) {
        _selection.update { it + (asset.id to SelectedAsset(asset.id, asset.type, month, asset.isVideo)) }
    }

    override fun deselectAsset(assetId: String) {
        _selection.update { it - assetId }
    }

    fun clearSelection() {
        _selection.value = emptyMap()
    }

    // ---------------------------------------------------------------- actions

    private fun runAction(block: suspend (List<SelectedAsset>) -> Unit) {
        val selected = _selection.value.values.toList()
        if (selected.isEmpty() || _isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                block(selected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Action failed", e)
                _messages.send(describe(e))
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun share() = runAction { selected ->
        _events.send(TimelineEvent.Launch(assetActions.shareIntent(selected)))
    }

    fun download() = runAction { selected ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _messages.send("Saving to the phone needs Android 10 or newer")
            return@runAction
        }
        if (selected.none { it.type == AssetType.REMOTE }) {
            _messages.send("Already on your phone")
            return@runAction
        }
        val saved = assetActions.downloadToPhone(selected)
        clearSelection()
        _messages.send("Saved $saved to Pictures/Picfort")
    }

    fun sendToFrame() = runAction { selected ->
        val asset = selected.singleOrNull()
        if (asset == null || asset.type != AssetType.REMOTE) {
            _messages.send("Select one photo from Immich to show on the frame")
            return@runAction
        }
        when (val result = frameRepository.send(asset.id)) {
            FrameResult.Sent -> _messages.send("Sent to frame")
            FrameResult.NotConfigured -> _messages.send("Set up the frame first: open a photo and tap ⚙")
            FrameResult.Busy -> _messages.send("The frame is still refreshing, try again shortly")
            is FrameResult.TooSoon -> _messages.send(
                "Wait ${result.retryAfterSeconds ?: 30}s before sending another photo"
            )
            is FrameResult.Error -> _messages.send(result.message)
        }
    }

    /** Adds the selected Immich items to [albumId], or to a new album called [newAlbumName]. */
    fun addToAlbum(albumId: String?, albumName: String, newAlbumName: String? = null) = runAction { selected ->
        val ids = selected.filter { it.type == AssetType.REMOTE }.map { it.id }
        val skipped = selected.size - ids.size
        if (ids.isEmpty()) {
            _messages.send("Back these up first: only items in Immich can go into albums")
            return@runAction
        }
        val message = if (newAlbumName != null) {
            collections.createAlbum(newAlbumName, ids)
            "Created \"${newAlbumName.trim()}\" with ${ids.size}"
        } else {
            val added = collections.addToAlbum(albumId!!, ids)
            if (added == 0) "Already in \"$albumName\"" else "Added $added to \"$albumName\""
        }
        clearSelection()
        _messages.send(if (skipped > 0) "$message ($skipped only on this phone were skipped)" else message)
    }

    /** Immich assets go to Immich's trash; phone photos go through the system trash dialog. */
    fun delete() = runAction { selected ->
        val (remote, local) = selected.partition { it.type == AssetType.REMOTE }
        if (remote.isNotEmpty()) {
            assetActions.deleteRemote(remote.map { it.id })
            remote.forEach { deselectAsset(it.id) }
            refreshQuietly()
            _messages.send("Moved ${remote.size} to Immich trash")
        }
        if (local.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pendingLocalDelete = local
                _events.send(TimelineEvent.ConfirmLocalTrash(assetActions.localTrashRequest(local)))
            } else {
                _messages.send("Deleting phone photos needs Android 11 or newer")
            }
        }
    }

    private var pendingLocalDelete: List<SelectedAsset> = emptyList()

    /** Result of the system trash dialog for phone photos. */
    fun onLocalTrashResult(confirmed: Boolean) {
        val local = pendingLocalDelete
        pendingLocalDelete = emptyList()
        if (!confirmed || local.isEmpty()) return
        viewModelScope.launch {
            local.forEach { deselectAsset(it.id) }
            refreshQuietly()
            _messages.send("Moved ${local.size} to the phone's trash")
        }
    }

    /** After our own change: sync so it shows; failing (offline) only means it shows later. */
    private suspend fun refreshQuietly() {
        try {
            timelineRepository.refresh()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Refresh after a change failed", e)
        }
    }

    init {
        refresh()
    }

    private var refreshAgain = false
    private var lastRefreshAt = 0L

    /** The app came back to the foreground: pick up changes made elsewhere, at most once a minute. */
    fun onForeground() {
        if (System.currentTimeMillis() - lastRefreshAt > 60_000) refresh()
    }

    override fun refresh() {
        lastRefreshAt = System.currentTimeMillis()
        if (loadingState.value == LoadingState.REFRESHING) {
            refreshAgain = true // e.g. media permission granted mid-sync
            return
        }
        viewModelScope.launch {
            loadingState.value = LoadingState.REFRESHING
            try {
                timelineRepository.refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Refresh failed", e)
                _messages.send(
                    if (e is IOException && e !is CleartextNotAllowedException) {
                        "Can't reach the server, showing saved photos"
                    } else {
                        describe(e)
                    }
                )
            } finally {
                loadingState.value = LoadingState.LOADED
                if (refreshAgain) {
                    refreshAgain = false
                    refresh()
                }
            }
        }
    }

    private fun describe(e: Exception) = when (e) {
        is CleartextNotAllowedException -> e.message ?: "Use https://"
        is IOException -> "Can't reach the server"
        is HttpException -> "Server error (HTTP ${e.code()})"
        else -> "Something went wrong: ${e.message}"
    }
}
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM, yyyy", Locale.ENGLISH)

/** Month keys are midnight UTC on the 1st; format them in UTC so no zone shifts the month. */
private fun formatMonth(key: Long): String = Instant.ofEpochMilli(key).atZone(ZoneOffset.UTC).format(MONTH_FORMAT)
