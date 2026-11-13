package com.github.ljarka.picfort.ui.wall

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.core.model.FamilyEventsProvider
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.FamilyMemberProvider
import com.github.ljarka.picfort.core.model.pollEvery
import com.github.ljarka.picfort.core.ui.avatar.UNKNOWN_MEMBER_COLOR
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How often the open wall asks for new posts. */
private const val POLL_MILLIS = 60_000L

data class FamilyWallUiState(
    val configured: Boolean = true,
    val members: List<FamilyMember> = emptyList(),
    /** Authors whose posts are hidden: test accounts ([isTestAccount]). */
    val hiddenAuthorIds: Set<String> = emptySet(),
    val selectedMemberId: String? = null,
    /** All posts as last loaded (plus optimistic ones), server order. */
    val posts: List<WallPost> = emptyList(),
    val myId: String = "",
    val isLoading: Boolean = true,
    /** Set when nothing could be loaded; with posts on screen, errors go to [message]. */
    val loadError: String? = null,
    /** One-off snackbar text. */
    val message: String? = null,
    /** Today panel (T-027); null when there is no server address. */
    val panel: TodayPanelState? = null,
) {
    private val visible: List<WallPost>
        get() = posts.filter { it.authorId !in hiddenAuthorIds }
            .let { all -> if (selectedMemberId == null) all else all.filter { it.authorId == selectedMemberId } }

    /** Text posts for the carousel: pinned first, then newest. */
    val messages: List<WallPost> get() = visible.filter { it.photoUrl == null }.ordered()

    /** Photo posts for the grid: pinned first, then newest. */
    val photos: List<WallPost> get() = visible.filter { it.photoUrl != null }.ordered()

    /** The member who wrote [post], or a stand-in for authors who aren't Immich users. */
    fun authorOf(post: WallPost): FamilyMember = members.firstOrNull { it.id == post.authorId }
        ?: FamilyMember(post.authorId, post.authorName, avatarUrl = null, color = UNKNOWN_MEMBER_COLOR)

    fun isMine(post: WallPost) = post.authorId == myId && post.id > 0
}

private fun List<WallPost>.ordered() =
    sortedWith(compareByDescending<WallPost> { it.pinned }.thenByDescending { it.created }.thenByDescending { it.id })

@HiltViewModel
class FamilyWallViewModel @Inject constructor(
    private val repository: WallRepository,
    private val family: FamilyMemberProvider,
    private val events: FamilyEventsProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(FamilyWallUiState(myId = repository.myId))
    val state: StateFlow<FamilyWallUiState> = _state.asStateFlow()

    /** Ids for optimistic posts; negative so they never clash with the server's. */
    private var nextTempId = -1

    init {
        viewModelScope.launch {
            family.allMembers.collect { members ->
                _state.update { s ->
                    val selected = s.selectedMemberId?.takeIf { id -> members.any { it.id == id } }
                    s.copy(members = members, selectedMemberId = selected)
                }
            }
        }
        viewModelScope.launch {
            events.upcoming.collect { updatePanel() }
        }
        viewModelScope.launch {
            family.hiddenIds.collect { ids -> _state.update { it.copy(hiddenAuthorIds = ids) } }
        }
    }

    /** Loads now and then every minute; call while the screen is visible. */
    suspend fun keepFresh() {
        pollEvery(intervalMillis = POLL_MILLIS) {
            refresh()
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    /** Recomputes the today panel from the cached events and the current date and time. */
    private fun updatePanel() {
        _state.update { s ->
            val panel = if (s.configured) todayPanel(events.upcoming.value, LocalDate.now(), LocalTime.now()) else null
            s.copy(panel = panel)
        }
    }

    private suspend fun refresh() {
        if (!repository.isConfigured) {
            _state.update {
                it.copy(configured = false, isLoading = false, posts = emptyList(), loadError = null, panel = null)
            }
            return
        }
        _state.update { it.copy(configured = true, isLoading = it.posts.isEmpty()) }
        updatePanel()
        // Independent of the posts: a slow or failing /events must not delay or break the wall.
        viewModelScope.launch { refreshEvents() }
        try {
            val posts = coroutineScope {
                launch { family.refresh() }
                repository.posts()
            }
            _state.update { s ->
                s.copy(posts = posts + s.posts.filter { it.id < 0 }, isLoading = false, loadError = null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // e.g. unexpected JSON or an unreadable photo, not only server errors
            _state.update { s ->
                if (s.posts.isEmpty()) {
                    s.copy(isLoading = false, loadError = e.message)
                } else {
                    s.copy(isLoading = false, message = e.message)
                }
            }
        }
    }

    /** An events failure (old server, offline) never reaches the wall: the panel just keeps what it has. */
    private suspend fun refreshEvents() {
        try {
            events.refreshUpcoming()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        updatePanel()
    }

    fun select(memberId: String?) {
        _state.update { it.copy(selectedMemberId = if (it.selectedMemberId == memberId) null else memberId) }
    }

    fun post(text: String, photo: Uri?) {
        val me = _state.value.members.firstOrNull { it.id == repository.myId }
        val temp = WallPost(
            id = nextTempId--,
            authorId = repository.myId,
            authorName = me?.name.orEmpty(),
            text = text.trim().takeIf { it.isNotEmpty() },
            photoUrl = photo?.toString(),
            photoWidth = null,
            photoHeight = null,
            pinned = false,
            created = Instant.now(),
        )
        _state.update { it.copy(posts = listOf(temp) + it.posts, loadError = null) }
        viewModelScope.launch {
            try {
                val created = repository.create(text, photo)
                _state.update { s -> s.copy(posts = s.posts.map { if (it.id == temp.id) created else it }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // e.g. unexpected JSON or an unreadable photo, not only server errors
                _state.update { s ->
                    s.copy(posts = s.posts.filterNot { it.id == temp.id }, message = "Couldn't post: ${e.message}")
                }
            }
        }
    }

    fun delete(post: WallPost) {
        _state.update { s -> s.copy(posts = s.posts.filterNot { it.id == post.id }) }
        viewModelScope.launch {
            try {
                repository.delete(post.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // e.g. unexpected JSON or an unreadable photo, not only server errors
                _state.update { s -> s.copy(posts = (s.posts + post), message = "Couldn't delete: ${e.message}") }
            }
        }
    }

    fun togglePin(post: WallPost) {
        _state.update { s ->
            s.copy(
                posts = s.posts.map {
                    if (it.id ==
                        post.id
                    ) {
                        it.copy(pinned = !it.pinned)
                    } else {
                        it
                    }
                },
            )
        }
        viewModelScope.launch {
            try {
                val updated = repository.togglePin(post.id)
                _state.update { s -> s.copy(posts = s.posts.map { if (it.id == post.id) updated else it }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // e.g. unexpected JSON or an unreadable photo, not only server errors
                _state.update { s ->
                    s.copy(
                        posts = s.posts.map {
                            if (it.id ==
                                post.id
                            ) {
                                post
                            } else {
                                it
                            }
                        },
                        message = "Couldn't pin: ${e.message}",
                    )
                }
            }
        }
    }

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }
}
