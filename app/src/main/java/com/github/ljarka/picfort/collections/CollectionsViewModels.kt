package com.github.ljarka.picfort.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.ljarka.picfort.Screen
import com.github.ljarka.picfort.UrlProvider
import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.network.CleartextNotAllowedException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

fun describeError(e: Exception): String = when (e) {
    is CleartextNotAllowedException -> e.message ?: "Use https://"
    is IOException -> "Can't reach the server"
    is HttpException -> "Server error (HTTP ${e.code()})"
    else -> "Something went wrong"
}

private suspend fun <T> load(block: suspend () -> T): LoadState<T> = try {
    LoadState.Loaded(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    LoadState.Error(describeError(e))
}

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val repository: CollectionsRepository,
    private val urlProvider: UrlProvider,
) : ViewModel() {
    private val _albums = MutableStateFlow<LoadState<List<AlbumDto>>>(LoadState.Loading)
    val albums: StateFlow<LoadState<List<AlbumDto>>> = _albums.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _albums.value = LoadState.Loading
        viewModelScope.launch { _albums.value = load { repository.albums() } }
    }

    fun thumbnail(album: AlbumDto): String? =
        album.albumThumbnailAssetId?.let { urlProvider.getThumbnail(it, AssetType.REMOTE) }
}

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val repository: CollectionsRepository,
    private val urlProvider: UrlProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<Screen.Album>()
    val name: String = route.name
    val source = "album:${route.id}"

    private val _assets = MutableStateFlow<LoadState<List<GridAsset>>>(LoadState.Loading)
    val assets: StateFlow<LoadState<List<GridAsset>>> = _assets.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _assets.value = LoadState.Loading
        viewModelScope.launch {
            _assets.value = load { repository.albumAssets(route.id).also { repository.putList(source, it) } }
        }
    }

    fun thumbnail(asset: GridAsset) = urlProvider.getThumbnail(asset.id, AssetType.REMOTE, asset.isVideo)
}

data class SearchUi(
    val query: String = "",
    val results: List<GridAsset> = emptyList(),
    val isLoading: Boolean = false,
    val nextPage: Int? = null,
    val error: String? = null,
    val searched: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: CollectionsRepository,
    private val urlProvider: UrlProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUi())
    val state: StateFlow<SearchUi> = _state.asStateFlow()
    private var job: Job? = null

    val source: String get() = "search:${_state.value.query}"

    fun search(query: String) {
        if (query.isBlank()) return
        job?.cancel()
        _state.value = SearchUi(query = query.trim(), isLoading = true, searched = true)
        fetch(page = 1)
    }

    /** Called when the user scrolls near the end. */
    fun loadMore() {
        val s = _state.value
        if (s.isLoading || s.nextPage == null) return
        _state.update { it.copy(isLoading = true) }
        fetch(page = s.nextPage)
    }

    private fun fetch(page: Int) {
        val query = _state.value.query
        job = viewModelScope.launch {
            try {
                val result = repository.search(query, page)
                _state.update {
                    val all = (if (page == 1) emptyList() else it.results) + result.assets
                    repository.putList("search:$query", all)
                    it.copy(results = all, nextPage = result.nextPage, isLoading = false, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = describeError(e)) }
            }
        }
    }

    fun thumbnail(asset: GridAsset) = urlProvider.getThumbnail(asset.id, AssetType.REMOTE, asset.isVideo)
}
