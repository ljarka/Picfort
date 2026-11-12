package com.github.ljarka.picfort.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.UrlProvider
import com.github.ljarka.picfort.collections.CollectionsRepository
import com.github.ljarka.picfort.collections.GridAsset
import com.github.ljarka.picfort.collections.LoadState
import com.github.ljarka.picfort.collections.describeError
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.FamilyMemberProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The family carousel on Photos (T-052): one member at a time; their photos come from my Immich
 * library through the person linked when their face was chosen in Family settings.
 */
@HiltViewModel
class PeopleFilterViewModel @Inject constructor(
    private val family: FamilyMemberProvider,
    private val repository: CollectionsRepository,
    private val urlProvider: UrlProvider,
) : ViewModel() {
    private val selectedId = MutableStateFlow<String?>(null)

    val people: StateFlow<PeopleFilterUi> =
        combine(family.allMembers, family.myPersonIds, selectedId) { members, links, selected ->
            PeopleFilterUi(members, links, selected?.takeIf { id -> members.any { it.id == id } })
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PeopleFilterUi())

    private val _photos = MutableStateFlow<LoadState<List<GridAsset>>>(LoadState.Loading)
    val photos: StateFlow<LoadState<List<GridAsset>>> = _photos.asStateFlow()

    /** A tapped member who isn't linked yet: the screen offers "Link". */
    private val _notLinked = MutableSharedFlow<FamilyMember>(extraBufferCapacity = 1)
    val notLinked: SharedFlow<FamilyMember> = _notLinked

    private var nextPage: Int? = null
    private var loading = false
    private var job: Job? = null

    /** The viewer pages through these photos. */
    val source: String get() = "person:${people.value.selectedId?.let { people.value.myPersonIds[it] }}"

    init {
        viewModelScope.launch { family.refresh() }
    }

    /** Tap: select a linked member, tap again (or null) to go back to the whole timeline. */
    fun select(id: String?) {
        when (val tap = people.value.tap(id)) {
            PeopleTap.Clear -> clear()
            PeopleTap.Ignore -> Unit
            is PeopleTap.NotLinked -> _notLinked.tryEmit(tap.member)
            is PeopleTap.Show -> {
                selectedId.value = tap.memberId
                job?.cancel()
                _photos.value = LoadState.Loading
                nextPage = 1
                fetch(tap.personId, page = 1)
            }
        }
    }

    fun clear() {
        job?.cancel()
        selectedId.value = null
        _photos.value = LoadState.Loading
    }

    fun retry() = people.value.selectedId?.let { id ->
        selectedId.value = null
        select(id)
    }

    /** Called near the end of the grid. */
    fun loadMore() {
        val personId = people.value.selectedId?.let { people.value.myPersonIds[it] } ?: return
        val page = nextPage ?: return
        if (loading) return
        fetch(personId, page)
    }

    private fun fetch(personId: String, page: Int) {
        loading = true
        job = viewModelScope.launch {
            try {
                val result = repository.personAssets(personId, page)
                val before = (_photos.value as? LoadState.Loaded)?.value.takeIf { page > 1 }.orEmpty()
                val all = before + result.assets
                repository.putList("person:$personId", all)
                nextPage = result.nextPage
                _photos.value = LoadState.Loaded(all)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (page == 1) _photos.value = LoadState.Error(describeError(e))
            } finally {
                loading = false
            }
        }
    }

    fun thumbnail(asset: GridAsset) = urlProvider.getThumbnail(asset.id, AssetType.REMOTE, asset.isVideo)
}
