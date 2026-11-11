package com.github.ljarka.picfort.ui.shopping

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.network.PicfortServerClient
import com.github.ljarka.picfort.network.PicfortServerException
import com.github.ljarka.picfort.ui.frame.FrameSettingsDialog
import com.github.ljarka.picfort.ui.wall.WallRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

private const val LISTS_POLL_MILLIS = 60_000L
private const val LIST_POLL_MILLIS = 10_000L

/** "14:32" in the device's locale, for the offline banner. */
internal fun Long.asTime(zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(this).atZone(zone))

@HiltViewModel
class ShoppingListsViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val server: PicfortServerClient,
    savedState: SavedStateHandle,
) : ViewModel() {
    val archived: Boolean = savedState.get<Boolean>("archived") ?: false
    private val extra = MutableStateFlow(ShoppingListsUi(isLoading = true))
    private val _state = MutableStateFlow(ShoppingListsUi(isLoading = true))
    val state: StateFlow<ShoppingListsUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.lists, repository.offlineSince, extra) { _, since, ui ->
                ui.copy(
                    lists = if (archived) repository.archived() else repository.active(),
                    offlineSince = since?.asTime()?.takeIf { !archived },
                )
            }.collect { _state.value = it }
        }
        viewModelScope.launch { repository.messages.collect { msg -> extra.update { it.copy(message = msg) } } }
    }

    suspend fun keepFresh() {
        while (true) {
            refresh()
            delay(LISTS_POLL_MILLIS)
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        if (!server.isConfigured) {
            extra.update { it.copy(configured = false, isLoading = false) }
            return
        }
        extra.update { it.copy(configured = true) }
        val error = repository.refresh(archived)
        val nothing = (if (archived) repository.archived() else repository.active()).isEmpty()
        extra.update {
            it.copy(
                isLoading = false,
                loadError = error?.message?.takeIf { nothing && repository.offlineSince.value == null },
            )
        }
    }

    suspend fun create(title: String): ShoppingList? = try {
        repository.create(title)
    } catch (e: PicfortServerException) {
        extra.update { it.copy(message = e.message) }
        null
    }

    fun messageShown() = extra.update { it.copy(message = null) }
}

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    private val repository: ShoppingRepository,
    private val wall: WallRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    val listId: Long = savedState.get<Long>("id") ?: 0L
    private val extra = MutableStateFlow(ShoppingListUi(myId = wall.myId))
    private val _state = MutableStateFlow(ShoppingListUi(myId = wall.myId))
    val state: StateFlow<ShoppingListUi> = _state.asStateFlow()
    /** Set after a delete, for the snackbar's Undo. */
    var undoable: ShoppingItem? = null
        private set

    init {
        viewModelScope.launch {
            combine(repository.lists, repository.offlineSince, wall.members, extra) { lists, since, members, ui ->
                ui.copy(list = lists[listId], members = members, offlineSince = since?.asTime())
            }.collect { _state.value = it }
        }
        viewModelScope.launch { repository.messages.collect { msg -> extra.update { it.copy(message = msg) } } }
    }

    suspend fun keepFresh() {
        while (true) {
            val error = repository.refreshList(listId)
            if (error is PicfortServerException.Gone) extra.update { it.copy(loadError = "This list no longer exists") }
            else if (error != null && repository.lists.value[listId] == null) extra.update { it.copy(loadError = error.message) }
            delay(LIST_POLL_MILLIS)
        }
    }

    fun refreshNow() {
        viewModelScope.launch { repository.refreshList(listId) }
    }

    fun toggle(item: ShoppingItem) = repository.toggle(listId, item, wall.myId)
    fun edit(item: ShoppingItem, text: String) = repository.edit(listId, item, text)
    fun add(text: String) = repository.addItem(listId, text)
    fun rename(title: String) = repository.rename(listId, title)
    fun archive(archived: Boolean) = repository.archive(listId, archived)
    fun uncheckAll() = repository.uncheckAll(listId)
    fun deleteChecked() = repository.deleteChecked(listId)
    fun deleteList() = repository.deleteList(listId)

    fun delete(item: ShoppingItem) {
        repository.delete(listId, item)
        undoable = item
        extra.update { it.copy(message = "Deleted \"${item.text}\"") }
    }

    fun undo() {
        undoable?.let { repository.restore(listId, it) }
        undoable = null
    }

    fun messageShown() {
        undoable = null
        extra.update { it.copy(message = null) }
    }
}

// Screens with state

@Composable
fun ShoppingListsScreen(
    onOpen: (ShoppingList, Boolean) -> Unit,
    onArchivedClick: () -> Unit = {},
    onBack: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val viewModel = hiltViewModel<ShoppingListsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() } }
    var showServerDialog by remember { mutableStateOf(false) }
    if (showServerDialog) FrameSettingsDialog(onDismiss = { showServerDialog = false }, onSaved = viewModel::refreshNow)
    var creating by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(creating) {
        creating?.let { title -> viewModel.create(title)?.let { onOpen(it, true) }; creating = null }
    }
    ShoppingListsContent(
        state = state,
        archived = viewModel.archived,
        onOpen = { onOpen(it, false) },
        onCreate = { creating = it },
        onArchivedClick = onArchivedClick,
        onBack = onBack,
        onSettingsClick = onSettingsClick,
        onRefresh = viewModel::refreshNow,
        onSetUpServer = { showServerDialog = true },
        onMessageShown = viewModel::messageShown,
    )
}

@Composable
fun ShoppingListScreen(onBack: () -> Unit, focusAdd: Boolean) {
    val viewModel = hiltViewModel<ShoppingListViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() } }
    ShoppingListContent(
        state = state,
        onBack = onBack,
        onToggle = { viewModel.toggle(it) },
        onEdit = { item, text -> viewModel.edit(item, text) },
        onAdd = { viewModel.add(it) },
        onDelete = viewModel::delete,
        onUndo = viewModel::undo,
        canUndo = { viewModel.undoable != null },
        onRename = { viewModel.rename(it) },
        onUncheckAll = { viewModel.uncheckAll() },
        onDeleteChecked = { viewModel.deleteChecked() },
        onArchive = { viewModel.archive(it) },
        onDeleteList = { viewModel.deleteList(); onBack() },
        onRefresh = viewModel::refreshNow,
        onMessageShown = viewModel::messageShown,
        focusAdd = focusAdd,
    )
}
