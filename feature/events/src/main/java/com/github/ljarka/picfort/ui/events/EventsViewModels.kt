package com.github.ljarka.picfort.ui.events

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.github.ljarka.picfort.core.model.EventRepeat
import com.github.ljarka.picfort.core.model.EventsSegment
import com.github.ljarka.picfort.core.model.FamilyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.core.network.PicfortServerClient
import com.github.ljarka.picfort.core.network.PicfortServerException
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.FamilyMemberProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.github.ljarka.picfort.core.model.DayPhotosProvider
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

import javax.inject.Inject

private const val EVENTS_POLL_MILLIS = 60_000L

@HiltViewModel
class EventsListViewModel @Inject constructor(
    private val repository: EventsRepository,
    private val family: FamilyMemberProvider,
    private val server: PicfortServerClient,
) : ViewModel() {

    private data class Extra(
        val configured: Boolean = true,
        val isLoading: Boolean = true,
        val loadError: String? = null,
        val message: String? = null,
        val memberNames: Map<String, String> = emptyMap(),
        val members: List<FamilyMember> = emptyList(),
        val selectedMemberId: String? = null,
    )

    private val extra = MutableStateFlow(Extra(configured = server.isConfigured, isLoading = true))
    private val _state = MutableStateFlow(EventsListUi(configured = server.isConfigured, isLoading = true))
    val state: StateFlow<EventsListUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.segment,
                repository.today,
                repository.upcoming,
                repository.past,
                combine(repository.hasMorePast, repository.isLoadingMorePast, extra, ::Triple),
            ) { segment, today, upcoming, past, (hasMore, loadingMore, ext) ->
                val activeEmpty = if (segment == EventsSegment.UPCOMING) upcoming.isEmpty() else past.isEmpty()
                EventsListUi(
                    configured = ext.configured,
                    segment = segment,
                    today = today,
                    upcomingEvents = upcoming,
                    pastEvents = past,
                    isLoading = ext.isLoading,
                    isLoadingMorePast = loadingMore,
                    hasMorePast = hasMore,
                    loadError = ext.loadError?.takeIf { activeEmpty },
                    message = ext.message,
                    memberNames = ext.memberNames,
                    members = ext.members,
                    selectedMemberId = ext.selectedMemberId?.takeIf { id -> ext.members.any { it.id == id } },
                )
            }.collect { _state.value = it }
        }
        viewModelScope.launch {
            family.allMembers.collect { members ->
                extra.update { it.copy(memberNames = members.associate { m -> m.id to m.name }, members = members) }
            }
        }
        viewModelScope.launch {
            repository.messages.collect { msg ->
                extra.update { it.copy(message = msg) }
            }
        }
    }

    suspend fun keepFresh() {
        while (true) {
            refresh()
            delay(EVENTS_POLL_MILLIS)
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    fun selectSegment(segment: EventsSegment) {
        if (repository.segment.value == segment) return
        repository.setSegment(segment)
        viewModelScope.launch { refresh() }
    }

    /** Filters the list to [memberId]'s events; null shows everyone's. */
    fun selectMember(memberId: String?) {
        extra.update { it.copy(selectedMemberId = memberId) }
    }

    fun loadMorePast() {
        viewModelScope.launch { repository.loadMorePast() }
    }

    fun messageShown() {
        extra.update { it.copy(message = null) }
    }

    private suspend fun refresh() {
        if (!server.isConfigured) {
            extra.update { it.copy(configured = false, isLoading = false, loadError = null) }
            return
        }
        val activeEmptyBefore = if (repository.segment.value == EventsSegment.UPCOMING) {
            repository.upcoming.value.isEmpty()
        } else {
            repository.past.value.isEmpty()
        }
        extra.update { it.copy(configured = true, isLoading = activeEmptyBefore, loadError = null) }
        viewModelScope.launch { family.refresh() }
        val error = repository.refreshActive()
        extra.update {
            it.copy(
                isLoading = false,
                loadError = error?.message,
            )
        }
    }
}

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val repository: EventsRepository,
    private val family: FamilyMemberProvider,
    private val dayPhotos: DayPhotosProvider,
    savedState: SavedStateHandle,
) : ViewModel() {

    val eventId: Long = savedState.get<Long>("eventId") ?: 0L
    private val _state = MutableStateFlow(
        EventDetailUi(
            event = repository.cachedEvent(eventId),
            today = repository.today.value,
            isLoading = repository.cachedEvent(eventId) == null,
        )
    )
    val state: StateFlow<EventDetailUi> = _state.asStateFlow()

    var onGone: (() -> Unit)? = null

    private var knownUsers: Map<String, String> = emptyMap()

    private fun authorName(event: FamilyEvent?): String =
        event?.let { knownUsers[it.createdBy] } ?: "Former member"

    private fun canDelete(event: FamilyEvent?): Boolean =
        event != null && !event.isBirthday && event.createdBy.isNotEmpty() && event.createdBy == family.myId

    init {
        viewModelScope.launch {
            combine(
                repository.upcoming,
                repository.past,
                repository.today,
                family.allMembers,
            ) { _, _, today, users ->
                // Hidden members still have a name; only ids no longer in Immich are "Former member".
                knownUsers = users.associate { it.id to it.name }
                val ev = repository.cachedEvent(eventId) ?: _state.value.event
                _state.value.copy(
                    event = ev,
                    today = today,
                    authorName = authorName(ev),
                    canDelete = canDelete(ev),
                    memberNames = knownUsers,
                    members = users,
                )
            }.collect { _state.value = it }
        }
        viewModelScope.launch {
            // Birthday photos (T-055): reloaded when the birth date or the day changes.
            _state.map { st -> st.event?.let { birthdayDays(it, st.today) }.orEmpty() to st.event?.birthdayOf }
                .distinctUntilChanged()
                .collectLatest { (days, memberId) ->
                    if (days.isEmpty() || memberId == null) {
                        _state.update { it.copy(birthdayPhotos = null) }
                        return@collectLatest
                    }
                    _state.update { it.copy(birthdayPhotos = (it.birthdayPhotos ?: BirthdayPhotosUi()).copy(loading = true)) }
                    val photos = runCatching { dayPhotos.photosOn(days.map { it.first }.toSet(), photoSource) }
                        .getOrDefault(emptyList())
                    _state.update { it.copy(birthdayPhotos = BirthdayPhotosUi(false, birthdayYears(days, photos))) }
                }
        }
    }

    /** The viewer's collection key for this event's photos. */
    val photoSource: String get() = "event:$eventId"

    fun load() {
        viewModelScope.launch {
            family.refresh()
            _state.update { it.copy(isLoading = it.event == null, loadError = null) }
            try {
                val fresh = repository.fetchEvent(eventId)
                _state.update {
                    it.copy(
                        event = fresh,
                        today = repository.rollOverMidnight(),
                        authorName = authorName(fresh),
                        canDelete = canDelete(fresh),
                        isLoading = false,
                        loadError = null,
                    )
                }
            } catch (e: PicfortServerException.Gone) {
                repository.removeFromCache(eventId)
                repository.postMessage("This event no longer exists")
                onGone?.invoke()
            } catch (e: PicfortServerException) {
                _state.update {
                    if (it.event == null) {
                        it.copy(isLoading = false, loadError = e.message)
                    } else {
                        it.copy(isLoading = false, message = e.message)
                    }
                }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.delete(eventId)
                onDeleted()
            } catch (e: PicfortServerException.Gone) {
                repository.postMessage("This event no longer exists")
                onDeleted()
            } catch (e: PicfortServerException) {
                _state.update { it.copy(message = e.message) }
            }
        }
    }

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }
}

@HiltViewModel
class EventFormViewModel @Inject constructor(
    private val repository: EventsRepository,
    private val family: FamilyMemberProvider,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val editEventId: Long? = savedState.get<Long>("eventId")?.takeIf { it > 0L }
    private var originalEvent: FamilyEvent? = editEventId?.let(repository::cachedEvent)

    private val _state = MutableStateFlow(initialUi(originalEvent, repository.rollOverMidnight(), editEventId != null))
    val state: StateFlow<EventFormUi> = _state.asStateFlow()

    init {
        if (editEventId != null && originalEvent == null) {
            viewModelScope.launch {
                try {
                    val fetched = repository.fetchEvent(editEventId)
                    originalEvent = fetched
                    _state.value = initialUi(fetched, repository.rollOverMidnight(), isEditing = true)
                        .copy(members = _state.value.members)
                } catch (e: PicfortServerException) {
                    _state.update { it.copy(message = e.message) }
                }
            }
        }
    }

    fun onTitleChange(title: String) {
        updateDraft { copy(title = title.take(100)) }
    }

    fun onStartDateChange(date: LocalDate) {
        updateDraft {
            val clearedEnd = endDate?.takeIf { it.isAfter(date) }
            copy(startDate = date, endDate = clearedEnd)
        }
    }

    fun onEndDateChange(date: LocalDate?) {
        updateDraft {
            val validEnd = if (date != null && startDate != null && !date.isAfter(startDate)) null else date
            copy(endDate = validEnd)
        }
    }

    fun onStartTimeChange(time: LocalTime?) {
        updateDraft { copy(startTime = time) }
    }

    fun onLocationChange(location: String) {
        updateDraft { copy(location = location.take(200)) }
    }

    fun onNoteChange(note: String) {
        updateDraft { copy(note = note.take(2000)) }
    }

    fun onRepeatChange(repeat: EventRepeat?) {
        updateDraft { copy(repeat = repeat, repeatUntil = repeatUntil.takeIf { repeat != null }) }
    }

    fun onRepeatUntilChange(date: LocalDate?) {
        updateDraft {
            copy(repeatUntil = date?.takeIf { repeat != null && (startDate == null || !it.isBefore(startDate)) })
        }
    }

    init {
        viewModelScope.launch {
            family.allMembers.collect { members -> _state.update { it.copy(members = members) } }
        }
    }

    /** "Who is it for": adds or removes a member, keeping the order they were chosen in (T-053). */
    fun onToggleMember(id: String) {
        updateDraft { copy(memberIds = if (id in memberIds) memberIds - id else memberIds + id) }
    }

    fun save(onSaved: (Long) -> Unit, onGone: () -> Unit) {
        val current = _state.value
        val start = current.startDate ?: return
        if (!current.canSave) return
        val draft = EventDraft(
            title = current.title,
            startDate = start,
            endDate = current.endDate,
            startTime = current.startTime,
            location = current.location,
            note = current.note,
            albumId = originalEvent?.albumId,
            repeat = current.repeat,
            repeatUntil = current.repeatUntil,
            memberIds = current.memberIds,
        )
        _state.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch {
            try {
                val saved = if (editEventId != null) {
                    val base = originalEvent ?: repository.fetchEvent(editEventId)
                    repository.update(base, draft)
                } else {
                    repository.create(draft)
                }
                _state.update { it.copy(isSaving = false, hasUnsavedChanges = false) }
                onSaved(saved.id)
            } catch (e: PicfortServerException.Gone) {
                editEventId?.let(repository::removeFromCache)
                repository.postMessage("This event no longer exists")
                _state.update { it.copy(isSaving = false) }
                onGone()
            } catch (e: PicfortServerException) {
                _state.update { it.copy(isSaving = false, message = e.message) }
            }
        }
    }

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun updateDraft(block: EventFormUi.() -> EventFormUi) {
        _state.update { current ->
            val next = current.block()
            next.copy(hasUnsavedChanges = hasChanges(next, originalEvent))
        }
    }

    private companion object {
        fun initialUi(event: FamilyEvent?, today: LocalDate, isEditing: Boolean): EventFormUi =
            if (event != null) {
                EventFormUi(
                    isEditing = true,
                    title = event.title,
                    // The form edits the series, so it starts from the first occurrence.
                    startDate = event.seriesStartDate,
                    endDate = event.seriesEndDate,
                    startTime = event.startTime,
                    location = event.location.orEmpty(),
                    note = event.note.orEmpty(),
                    repeat = event.repeat,
                    repeatUntil = event.repeatUntil,
                    editsSeries = event.repeats,
                    memberIds = event.memberIds,
                    today = today,
                    hasUnsavedChanges = false,
                )
            } else {
                EventFormUi(
                    isEditing = isEditing,
                    startDate = today,
                    today = today,
                    hasUnsavedChanges = false,
                )
            }

        fun hasChanges(ui: EventFormUi, original: FamilyEvent?): Boolean {
            if (original == null) {
                return ui.title.isNotBlank() ||
                    ui.repeat != null ||
                    ui.endDate != null ||
                    ui.startTime != null ||
                    ui.location.isNotBlank() ||
                    ui.note.isNotBlank() ||
                    ui.memberIds.isNotEmpty()
            }
            return ui.title.trim() != original.title ||
                ui.startDate != original.seriesStartDate ||
                ui.endDate != original.seriesEndDate ||
                ui.repeat != original.repeat ||
                ui.repeatUntil != original.repeatUntil ||
                ui.startTime != original.startTime ||
                ui.location.trim().ifEmpty { null } != original.location ||
                ui.note.trim().ifEmpty { null } != original.note ||
                ui.memberIds != original.memberIds
        }
    }
}

@Composable
fun EventsListScreen(
    onOpenEvent: (Long) -> Unit,
    onAddEvent: () -> Unit,
    onSettingsClick: () -> Unit,
    onSetUpServer: () -> Unit = onSettingsClick,
) {
    val viewModel = hiltViewModel<EventsListViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.keepFresh()
        }
    }
    EventsListContent(
        state = state,
        locale = LocalConfiguration.current.locales[0],
        onSegmentChange = viewModel::selectSegment,
        onOpenEvent = { onOpenEvent(it.id) },
        onAddClick = onAddEvent,
        onLoadMorePast = viewModel::loadMorePast,
        onRefresh = viewModel::refreshNow,
        onSettingsClick = onSettingsClick,
        onSetUpServer = onSetUpServer,
        onMessageShown = viewModel::messageShown,
        onSelectMember = viewModel::selectMember,
    )
}

@Composable
fun EventDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    /** Opens the member (id) whose birthday this is in Family settings. */
    onEditBirthday: (String) -> Unit = {},
    /** Opens a birthday photo full screen: Immich asset id, its index in [source], the viewer's collection. */
    onOpenPhoto: (assetId: String, index: Int, source: String) -> Unit = { _, _, _ -> },
    /** A birthday year's title: that day in the Photos timeline (T-056). */
    onOpenDay: (LocalDate) -> Unit = {},
) {
    val viewModel = hiltViewModel<EventDetailViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel) {
        viewModel.onGone = onBack
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.load()
        }
    }
    EventDetailContent(
        state = state,
        locale = LocalConfiguration.current.locales[0],
        onBack = onBack,
        onEdit = { onEdit(viewModel.eventId) },
        onDelete = { viewModel.delete(onDeleted = onBack) },
        onLocationClick = { location ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(location)}"))
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
            }
        },
        onRetry = viewModel::load,
        onMessageShown = viewModel::messageShown,
        onEditBirthday = { state.event?.birthdayOf?.let(onEditBirthday) },
        onOpenPhoto = { photo, index -> onOpenPhoto(photo.id, index, viewModel.photoSource) },
        onOpenDay = onOpenDay,
    )
}

@Composable
fun EventFormScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onEventGone: () -> Unit,
) {
    val viewModel = hiltViewModel<EventFormViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscardBack by remember { mutableStateOf(false) }

    BackHandler(enabled = state.hasUnsavedChanges && !state.isSaving) {
        confirmDiscardBack = true
    }

    if (confirmDiscardBack) {
        AlertDialog(
            onDismissRequest = { confirmDiscardBack = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved changes to this event will be lost.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscardBack = false; onBack() }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscardBack = false }) {
                    Text("Keep editing")
                }
            },
        )
    }

    EventFormContent(
        state = state,
        locale = LocalConfiguration.current.locales[0],
        onTitleChange = viewModel::onTitleChange,
        onStartDateChange = viewModel::onStartDateChange,
        onEndDateChange = viewModel::onEndDateChange,
        onStartTimeChange = viewModel::onStartTimeChange,
        onLocationChange = viewModel::onLocationChange,
        onNoteChange = viewModel::onNoteChange,
        onRepeatChange = viewModel::onRepeatChange,
        onRepeatUntilChange = viewModel::onRepeatUntilChange,
        onToggleMember = viewModel::onToggleMember,
        onSave = { viewModel.save(onSaved = onSaved, onGone = onEventGone) },
        onClose = onBack,
        onMessageShown = viewModel::messageShown,
    )
}
