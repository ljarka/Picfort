package com.github.ljarka.picfort.ui.timeline

import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.util.Log
import com.github.ljarka.picfort.assets.AssetActions
import com.github.ljarka.picfort.assets.SelectedAsset
import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.ui.frame.FrameRepository
import com.github.ljarka.picfort.ui.frame.FrameResult
import kotlinx.coroutines.flow.map
import android.util.LruCache
import com.github.ljarka.picfort.network.CleartextNotAllowedException
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

enum class AssetLoadingState {
    LOADING, LOADED, DEFAULT,

    /** Couldn't load (offline); not retried automatically until the next refresh. */
    FAILED,
}

private const val TAG = "TimelineViewModel"

sealed interface TimelineEvent {
    data class Launch(val intent: Intent) : TimelineEvent
    data class ConfirmLocalTrash(val request: IntentSender) : TimelineEvent
}

@HiltViewModel
class TimelineViewModel @Inject constructor(
    val timelineRepository: TimelineRepository,
    private val assetActions: AssetActions,
    private val frameRepository: FrameRepository,
) : ViewModel() {
    private val loadingState = MutableStateFlow(LoadingState.DEFAULT)
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()
    val state = combine(timelineRepository.getTimeBuckets(), loadingState) { items, loadingState ->
        BucketsState(
            loadingState = loadingState,
            items = items,
        )
    }.stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(5_000), BucketsState())
    private val fixedSpans = mutableSetOf<Long>()
    private val _assetState = ConcurrentHashMap<Long, MutableStateFlow<AssetLoadingState>>()
    private val assetFetchingJobs = object : LruCache<Long, Job>(5) {
        override fun entryRemoved(evicted: Boolean, key: Long?, oldValue: Job?, newValue: Job?) {
            oldValue?.cancel()
        }
    }
    private val _selection = MutableStateFlow<Map<String, SelectedAsset>>(emptyMap())
    val selection: StateFlow<Map<String, SelectedAsset>> = _selection.asStateFlow()
    val selectedAssets: StateFlow<Set<String>> = _selection
        .map { it.keys }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _events = Channel<TimelineEvent>(Channel.BUFFERED)
    val events: Flow<TimelineEvent> = _events.receiveAsFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun getAssetLoadingState(bucket: Long): StateFlow<AssetLoadingState> {
        return _assetState.getOrPut(bucket) { MutableStateFlow(AssetLoadingState.DEFAULT) }
    }

    fun isFixedSpan(bucket: Long): Boolean = fixedSpans.contains(bucket)

    fun setFixedSpan(bucket: Long) {
        fixedSpans.add(bucket)
    }

    fun getAsset(bucket: Long, position: Int): AssetUi? {
        return timelineRepository.getAsset(bucket, position)
    }

    @Synchronized
    fun fetchAssets(bucket: Long) {
        val job = assetFetchingJobs[bucket]

        if (job == null) {
            assetFetchingJobs.put(bucket, viewModelScope.launch(Dispatchers.IO) {
                val loadingState = _assetState[bucket]
                loadingState?.value = AssetLoadingState.LOADING
                try {
                    val fetched = timelineRepository.fetchAssets(bucket)
                    loadingState?.value =
                        if (fetched) AssetLoadingState.LOADED else AssetLoadingState.DEFAULT
                } catch (e: CancellationException) {
                    loadingState?.value = AssetLoadingState.DEFAULT
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't load bucket $bucket", e)
                    loadingState?.value = AssetLoadingState.FAILED
                } finally {
                    assetFetchingJobs.remove(bucket)
                }
            })
        }
    }

    fun selectAsset(asset: AssetUi, bucket: Long) {
        _selection.update { it + (asset.id to SelectedAsset(asset.id, asset.type, bucket)) }
    }

    fun deselectAsset(assetId: String) {
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

    /** Immich assets go to Immich's trash; phone photos go through the system trash dialog. */
    fun delete() = runAction { selected ->
        val (remote, local) = selected.partition { it.type == AssetType.REMOTE }
        if (remote.isNotEmpty()) {
            assetActions.deleteRemote(remote.map { it.id })
            remote.forEach { deselectAsset(it.id) }
            timelineRepository.reloadBuckets(remote.map { it.bucket }.toSet())
            _messages.send("Moved ${remote.size} to Immich trash")
        }
        if (local.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pendingLocalDelete = local
                _events.send(TimelineEvent.ConfirmLocalTrash(assetActions.localTrashRequest(local.map { it.id })))
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
            try {
                timelineRepository.reloadBuckets(local.map { it.bucket }.toSet())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Reload after local delete failed", e)
            }
            _messages.send("Moved ${local.size} to the phone's trash")
        }
    }

    init {
        refresh()
    }

    fun refresh() {
        if (loadingState.value == LoadingState.REFRESHING) return
        viewModelScope.launch {
            loadingState.value = LoadingState.REFRESHING
            try {
                timelineRepository.refresh()
                // Let months that failed while offline try again.
                _assetState.values.forEach {
                    it.compareAndSet(AssetLoadingState.FAILED, AssetLoadingState.DEFAULT)
                }
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