package com.github.ljarka.picfort.cleanup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.UrlProvider
import com.github.ljarka.picfort.assets.AssetActions
import com.github.ljarka.picfort.collections.CollectionsRepository
import com.github.ljarka.picfort.collections.GridAsset
import com.github.ljarka.picfort.core.database.AssetType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "CleanupViewModel"
internal const val CLEANUP_VIEWER_SOURCE = "cleanup"

data class CleanupUi(
    val filter: CleanupFilter = CleanupFilter.All,
    val items: List<CleanupCandidateDto> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class CleanupViewModel @Inject constructor(
    private val insights: PhotoInsightsClient,
    private val assetActions: AssetActions,
    private val collections: CollectionsRepository,
    private val urlProvider: UrlProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(CleanupUi())
    val state: StateFlow<CleanupUi> = _state.asStateFlow()

    init {
        load(CleanupFilter.All)
    }

    fun thumbnail(assetId: String): String = urlProvider.getThumbnail(assetId, AssetType.REMOTE, isVideo = false)

    fun setFilter(filter: CleanupFilter) {
        if (filter == _state.value.filter && !_state.value.loading) return
        load(filter)
    }

    fun refresh() = load(_state.value.filter)

    private fun load(filter: CleanupFilter) {
        _state.update { it.copy(filter = filter, loading = true, error = null, selectedIds = emptySet()) }
        viewModelScope.launch {
            try {
                val items = insights.cleanup(filter)
                publishViewerList(items)
                _state.update { it.copy(items = items, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load cleanup candidates", e)
                _state.update { it.copy(loading = false, error = e.message ?: "Could not load cleanup list") }
            }
        }
    }

    fun toggleSelect(assetId: String) {
        _state.update { s ->
            val next = if (assetId in s.selectedIds) s.selectedIds - assetId else s.selectedIds + assetId
            s.copy(selectedIds = next)
        }
    }

    fun clearSelection() = _state.update { it.copy(selectedIds = emptySet()) }

    fun keep(assetId: String) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                insights.dismiss(assetId)
                removeItems(setOf(assetId), "Kept — won't show again")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Dismiss failed", e)
                _state.update { it.copy(busy = false, message = e.message ?: "Couldn't keep item") }
            }
        }
    }

    fun trash(assetIds: List<String>) {
        if (assetIds.isEmpty() || _state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                assetActions.deleteRemote(assetIds)
                assetIds.forEach { id -> runCatching { insights.deleteRecord(id) } }
                val label = if (assetIds.size ==
                    1
                ) {
                    "Moved to Immich trash"
                } else {
                    "Moved ${assetIds.size} to Immich trash"
                }
                removeItems(assetIds.toSet(), label)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Trash failed", e)
                _state.update { it.copy(busy = false, message = e.message ?: "Couldn't move to trash") }
            }
        }
    }

    fun trashSelected() = trash(_state.value.selectedIds.toList())

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun removeItems(ids: Set<String>, message: String) {
        val remaining = _state.value.items.filterNot { it.assetId in ids }
        publishViewerList(remaining)
        _state.update {
            it.copy(
                items = remaining,
                selectedIds = it.selectedIds - ids,
                busy = false,
                message = message,
            )
        }
    }

    private fun publishViewerList(items: List<CleanupCandidateDto>) {
        collections.putList(
            CLEANUP_VIEWER_SOURCE,
            items.map { GridAsset(id = it.assetId, isVideo = false, durationMs = null) },
        )
    }
}
