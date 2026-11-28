package com.github.ljarka.picfort.memories

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.UrlProvider
import com.github.ljarka.picfort.collections.CollectionsRepository
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.ui.timeline.SyncTimelineSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "MemoriesViewModel"

data class MemoriesUi(
    val sections: List<MemorySection> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/** Immich memories for the Memories screen opened from the Family Wall (T-096). */
@HiltViewModel
class MemoriesViewModel @Inject constructor(
    private val collections: CollectionsRepository,
    private val urlProvider: UrlProvider,
    private val timeline: SyncTimelineSource,
) : ViewModel() {
    private val _state = MutableStateFlow(MemoriesUi())
    val state: StateFlow<MemoriesUi> = _state.asStateFlow()

    init {
        refresh()
    }

    fun thumbnail(assetId: String, isVideo: Boolean): String =
        urlProvider.getThumbnail(assetId, AssetType.REMOTE, isVideo)

    fun preview(assetId: String, isVideo: Boolean): String = urlProvider.getPreview(assetId, AssetType.REMOTE, isVideo)

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                // Opened from the shared Family Wall: photos the AI marked private never show (T-098).
                val sensitive = timeline.aiLabels().first().sensitive
                val memories = withoutSensitive(collections.memories(), sensitive)
                val sections = memorySections(memories, LocalDate.now(), Locale.getDefault())
                // The viewer pages through one memory's photos.
                sections.flatMap { it.memories }.forEach { collections.putList(it.source, it.assets) }
                _state.update { MemoriesUi(sections = sections, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Loading memories failed", e)
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load memories from Immich") }
            }
        }
    }
}
