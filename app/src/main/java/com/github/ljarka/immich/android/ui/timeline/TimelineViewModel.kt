package com.github.ljarka.immich.android.ui.timeline

import android.util.Log
import android.util.LruCache
import com.github.ljarka.immich.android.network.CleartextNotAllowedException
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

@HiltViewModel
class TimelineViewModel @Inject constructor(
    val timelineRepository: TimelineRepository,
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
    private val _selectedAssets = MutableStateFlow<Set<String>>(emptySet())
    val selectedAssets: StateFlow<Set<String>> = _selectedAssets.asStateFlow()

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

    fun selectAsset(assetId: String) {
        _selectedAssets.update {
            it.toMutableSet().apply {
                add(assetId)
            }
        }
    }

    fun deselectAsset(assetId: String) {
        _selectedAssets.update {
            it.toMutableSet().apply {
                remove(assetId)
            }
        }
    }

    fun clearSelection() {
        _selectedAssets.value = emptySet()
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
                _messages.send(describe(e))
            } finally {
                loadingState.value = LoadingState.LOADED
            }
        }
    }

    private fun describe(e: Exception) = when (e) {
        is CleartextNotAllowedException -> e.message ?: "Use https://"
        is IOException -> "Can't reach the server, showing saved photos"
        is HttpException -> "Server error (HTTP ${e.code()})"
        else -> "Couldn't refresh: ${e.message}"
    }
}