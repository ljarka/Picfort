package com.github.ljarka.picfort.collections

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.ljarka.picfort.UrlProvider
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.model.FamilyMemberProvider
import com.github.ljarka.picfort.core.network.CleartextNotAllowedException
import com.github.ljarka.picfort.ui.timeline.AlbumRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val value: T) : LoadState<T>
    data class Error(val message: String) : LoadState<Nothing>
}

/**
 * What to show for a failed load. An unexpected error (e.g. a response the app can't read after a
 * server update, T-061) is logged and named, so a screenshot is enough to tell what broke.
 */
fun describeError(e: Exception): String = when (e) {
    is CleartextNotAllowedException -> e.message ?: "Use https://"

    is IOException -> "Can't reach the server"

    is HttpException -> "Server error (HTTP ${e.code()})"

    else -> {
        Log.w("Picfort", "Unexpected error", e)
        "Something went wrong (${e.javaClass.simpleName})"
    }
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
    private val family: FamilyMemberProvider,
) : ViewModel() {
    private val _albums = MutableStateFlow<LoadState<List<AlbumDto>>>(LoadState.Loading)
    val albums: StateFlow<LoadState<List<AlbumDto>>> = _albums.asStateFlow()

    init {
        reload()
        viewModelScope.launch {
            repository.deletedAlbums.collect { id ->
                _albums.update { state ->
                    if (state is LoadState.Loaded) LoadState.Loaded(state.value.filterNot { it.id == id }) else state
                }
            }
        }
    }

    fun canDelete(album: AlbumDto): Boolean = album.canDelete(family.myId)

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
    private val route = savedStateHandle.toRoute<AlbumRoute>()
    val name: String = route.name
    val source = "album:${route.id}"
    val canDelete: Boolean = route.canDelete

    private val _delete = MutableStateFlow(AlbumDeleteUi())
    val delete: StateFlow<AlbumDeleteUi> = _delete.asStateFlow()

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

    fun deleteAlbum() {
        if (_delete.value.deleting) return
        _delete.value = AlbumDeleteUi(deleting = true)
        viewModelScope.launch {
            _delete.value = try {
                repository.deleteAlbum(route.id)
                AlbumDeleteUi(deleted = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AlbumDeleteUi(error = deleteError(e))
            }
        }
    }

    fun deleteErrorShown() = _delete.update { it.copy(error = null) }
}

/** Deleting an album: in progress, refused (shown in the dialog) or done (the screen closes). */
data class AlbumDeleteUi(val deleting: Boolean = false, val error: String? = null, val deleted: Boolean = false)

fun deleteError(e: Exception): String = when {
    e is HttpException && e.code() == 403 -> "Only the album's owner can delete it"
    e is HttpException && e.code() == 404 -> "The album is already gone"
    else -> describeError(e)
}

data class SearchUi(
    val query: String = "",
    val results: List<GridAsset> = emptyList(),
    val isLoading: Boolean = false,
    val nextPage: Int? = null,
    val error: String? = null,
    val searched: Boolean = false,
    /** Named people in Immich, shown above the results; empty while loading or if it failed. */
    val people: List<SearchPerson> = emptyList(),
    /** Photos must have all of them; alone or with [query]. */
    val selectedPeople: Set<String> = emptySet(),
    /** Picked under "All people": at the end of the row, kept there while the screen is open. */
    val addedToRow: List<String> = emptyList(),
    /** Only photos or only videos; alone it lists them all, newest first (T-071). */
    val media: MediaFilter = MediaFilter.All,
)

/** "Ola", "Ola and Kuba", "Ola, Kuba and Staś" (the people picked, in Immich's order). */
fun SearchUi.selectedNames(): String {
    val names = people.filter { it.id in selectedPeople }.map { it.name }
    return if (names.size <= 1) names.joinToString() else names.dropLast(1).joinToString() + " and " + names.last()
}

/** Shown when nothing matched: what was searched for, in words. */
fun SearchUi.nothingFound(): String {
    val noun = if (media == MediaFilter.Videos) "videos" else "photos"
    val among = when (media) {
        MediaFilter.All -> ""
        MediaFilter.Photos -> " in photos"
        MediaFilter.Videos -> " in videos"
    }
    return when {
        query.isEmpty() && selectedPeople.isEmpty() -> "No $noun"
        selectedPeople.isEmpty() -> "Nothing found for \"$query\"$among"
        query.isEmpty() && selectedPeople.size == 1 -> "No $noun of ${selectedNames()}"
        query.isEmpty() -> "No $noun of ${selectedNames()} together"
        else -> "Nothing found for \"$query\" with ${selectedNames()}$among"
    }
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: CollectionsRepository,
    private val urlProvider: UrlProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUi())
    val state: StateFlow<SearchUi> = _state.asStateFlow()
    private var job: Job? = null

    val source: String get() = _state.value.let {
        "search:${it.query}:${it.selectedPeople.sorted().joinToString(",")}:${it.media}"
    }

    init {
        viewModelScope.launch {
            // Without people the row is just not shown; text search still works.
            val people = try {
                repository.people()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            _state.update { it.copy(people = people) }
        }
    }

    fun search(query: String) {
        val s = _state.value
        job?.cancel()
        if (query.isBlank() && s.selectedPeople.isEmpty() && s.media == MediaFilter.All) {
            _state.value = SearchUi(people = s.people, addedToRow = s.addedToRow)
            return
        }
        _state.value = SearchUi(
            query = query.trim(),
            isLoading = true,
            searched = true,
            people = s.people,
            selectedPeople = s.selectedPeople,
            addedToRow = s.addedToRow,
            media = s.media,
        )
        fetch(page = 1)
    }

    /** All, photos or videos; searches again with the text in the field. */
    fun setMedia(media: MediaFilter, query: String) {
        if (media == _state.value.media) return
        _state.update { it.copy(media = media) }
        search(query)
    }

    /** Picks or drops a person and searches again with the text in the field. */
    fun togglePerson(id: String, query: String) {
        _state.update { s ->
            if (id in s.selectedPeople) {
                s.copy(selectedPeople = s.selectedPeople - id)
            } else {
                s.copy(selectedPeople = s.selectedPeople + id, addedToRow = addedToRow(s.people, s.addedToRow, id))
            }
        }
        search(query)
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
        val people = _state.value.selectedPeople
        val media = _state.value.media
        val source = source
        job = viewModelScope.launch {
            try {
                val result = repository.search(query, page, people, media)
                _state.update {
                    val all = (if (page == 1) emptyList() else it.results) + result.assets
                    repository.putList(source, all)
                    it.copy(results = all, nextPage = result.nextPage, isLoading = false, error = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = describeError(e)) }
            }
        }
    }

    fun personThumbnail(id: String) = urlProvider.personThumbnail(id)

    fun thumbnail(asset: GridAsset) = urlProvider.getThumbnail(asset.id, AssetType.REMOTE, asset.isVideo)
}
