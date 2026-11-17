package com.github.ljarka.picfort.ui.schooljournal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "SchoolJournal"

enum class JournalMode { LOADING, SETUP, CONTENT }

enum class JournalTab(val title: String) { MESSAGES("Messages"), GRADES("Grades") }

/** One banner under the status line; the first that applies wins (spec §4.1). */
enum class JournalBanner(val text: String) {
    AUTH_FAILED("The server can't log in to the e-journal. Fix its login and restart it."),
    FIRST_SYNC("The first check of the e-journal is still running."),
    OFFLINE("Can't reach the school server. Pull down to try again."),
    STORAGE("The school server has a storage problem."),
}

/** The messages tab (spec §4.3). */
data class MessagesUi(
    val items: List<MessageSummary> = emptyList(),
    /** Next page; null = last page (and always with [unreadOnly]). */
    val nextCursor: String? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val loaded: Boolean = false,
    val unreadOnly: Boolean = false,
    val showRemoved: Boolean = false,
)

/** The configuration form (spec §3). [editing]: opened from the content, so Cancel and Disconnect. */
data class SetupForm(
    val address: String = "",
    val key: String = "",
    val connecting: Boolean = false,
    val error: String? = null,
    /** Why the form is shown again, e.g. "The API key is no longer valid". */
    val notice: String? = null,
    val editing: Boolean = false,
)

data class SchoolJournalUi(
    val mode: JournalMode = JournalMode.LOADING,
    val form: SetupForm = SetupForm(),
    val status: JournalStatus? = null,
    val children: List<ChildAccount> = emptyList(),
    /** Child id, or null for all children. */
    val selectedChild: String? = null,
    val tab: JournalTab = JournalTab.MESSAGES,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val storageProblem: Boolean = false,
    /** Tab dots (spec §6). */
    val news: JournalNews = JournalNews(),
    val messages: MessagesUi = MessagesUi(),
    /** This Immich user's read state (spec §4.6); null until started. */
    val readState: ReadState? = null,
    /** One-off snackbar text. */
    val snackbar: String? = null,
) {
    fun isRead(message: MessageSummary): Boolean = readState?.isRead(message) ?: true

    /** First name for a row when all children are shown. */
    fun childName(childId: String): String? =
        if (selectedChild == null && showChildChips) children.firstOrNull { it.id == childId }?.name else null

    val banner: JournalBanner?
        get() = when {
            status?.authFailed == true -> JournalBanner.AUTH_FAILED

            status != null && status.lastSuccessfulSyncAt == null && status.lastSync?.failed != true ->
                JournalBanner.FIRST_SYNC

            offline -> JournalBanner.OFFLINE

            storageProblem -> JournalBanner.STORAGE

            else -> null
        }

    /** Chips only with two or more children (spec §4.2). */
    val showChildChips: Boolean get() = children.size >= 2
}

@HiltViewModel
class SchoolJournalViewModel @Inject constructor(
    private val api: SchoolJournalApi,
    private val config: SchoolJournalConfigStore,
    private val readState: ReadStateStore,
    private val news: SchoolJournalNews,
    private val removed: RemovedMessages,
) : ViewModel() {
    private val _state = MutableStateFlow(initial())
    val state: StateFlow<SchoolJournalUi> = _state.asStateFlow()

    private var messagesJob: Job? = null

    init {
        viewModelScope.launch { news.news.collect { n -> _state.update { it.copy(news = n) } } }
        // A message opened from here and gone from the server (404) leaves the list.
        viewModelScope.launch {
            removed.ids.collect { gone ->
                _state.update { ui ->
                    ui.copy(
                        messages = ui.messages.copy(
                            items = ui.messages.items.filter {
                                it.id !in
                                    gone
                            },
                        ),
                    )
                }
            }
        }
    }

    private val address: String? get() = config.connection.value?.base?.toString()

    private fun initial(): SchoolJournalUi = if (config.connection.value == null) {
        SchoolJournalUi(mode = JournalMode.SETUP)
    } else {
        SchoolJournalUi(mode = JournalMode.CONTENT, selectedChild = config.child)
    }

    fun onAddressChange(text: String) = _state.update { it.copy(form = it.form.copy(address = text, error = null)) }

    fun onKeyChange(text: String) = _state.update { it.copy(form = it.form.copy(key = text, error = null)) }

    /** Connect (spec §3): `/health`, then `/status` with the key; only then saved. */
    fun connect() {
        val form = _state.value.form
        if (form.connecting) return
        val base = normalizeJournalAddress(form.address)
        val error = when {
            base == null -> "Enter the server address, e.g. 192.168.1.10:5100"
            form.key.isBlank() -> "Enter the API key"
            else -> null
        }
        if (error != null || base == null) {
            _state.update { it.copy(form = it.form.copy(error = error)) }
            return
        }
        val connection = Connection(base, form.key.trim())
        _state.update { it.copy(form = it.form.copy(connecting = true, error = null)) }
        viewModelScope.launch {
            try {
                api.health(base)
                val status = api.status(connection)
                config.save(connection)
                val read = readState.ensureStarted(base.toString(), Instant.now(), status.lastEventId)
                _state.update {
                    it.copy(
                        mode = JournalMode.CONTENT,
                        form = SetupForm(),
                        status = status,
                        offline = false,
                        storageProblem = false,
                        selectedChild = config.child,
                        readState = read,
                    )
                }
                loadChildren(connection)
                news.refresh(status)
                loadMessages()
            } catch (e: SchoolJournalError) {
                Log.w(TAG, "Connect failed: ${e.message}")
                _state.update { it.copy(form = it.form.copy(connecting = false, error = e.userMessage())) }
            }
        }
    }

    /**
     * Reload status, children and the read state. [full] (pull-to-refresh) also reloads the
     * messages from the first page; on start (e.g. back from a message) only the first load does,
     * so the list keeps its pages and scroll position.
     */
    fun refresh(full: Boolean = false) {
        val connection = config.connection.value ?: return
        if (_state.value.mode != JournalMode.CONTENT) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                val status = api.status(connection)
                val read = readState.ensureStarted(connection.base.toString(), Instant.now(), status.lastEventId)
                _state.update { it.copy(status = status, offline = false, storageProblem = false, readState = read) }
                loadChildren(connection)
                news.refresh(status)
                if (_state.value.tab == JournalTab.GRADES) news.markGradesSeen(status.lastEventId)
                if (full || !_state.value.messages.loaded) loadMessages()
            } catch (e: SchoolJournalError) {
                handle(e)
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    private suspend fun loadChildren(connection: Connection) {
        try {
            val children = api.children(connection)
            val selected = config.child?.takeIf { id -> children.any { it.id == id } }
            if (selected == null) config.child = null
            _state.update { it.copy(children = children, selectedChild = selected) }
        } catch (e: SchoolJournalError) {
            handle(e)
        }
    }

    private fun handle(e: SchoolJournalError) {
        Log.w(TAG, "School server: ${e.message}")
        when (e) {
            is SchoolJournalError.WrongKey -> showSettings(notice = "The API key is no longer valid")

            is SchoolJournalError.Unreachable, is SchoolJournalError.CleartextNotAllowed ->
                _state.update { it.copy(offline = true) }

            is SchoolJournalError.StorageUnavailable -> _state.update { it.copy(storageProblem = true) }

            else -> Unit
        }
    }

    fun selectChild(id: String?) {
        config.child = id
        _state.update { it.copy(selectedChild = id) }
        loadMessages()
    }

    /** First page of messages (or every unread one) for the chosen child and filters. */
    fun loadMessages() {
        val c = config.connection.value ?: return
        messagesJob?.cancel()
        _state.update { it.copy(messages = it.messages.copy(loading = true)) }
        messagesJob = viewModelScope.launch {
            val ui = _state.value
            try {
                if (ui.messages.unreadOnly) {
                    val read = ui.readState ?: return@launch
                    val unread = loadUnread(api, c, read, ui.selectedChild, ui.messages.showRemoved)
                    if (unread.gone.isNotEmpty()) {
                        updateReadState { s ->
                            unread.gone.fold(s) { acc, id -> acc.forget(id) }
                        }
                    }
                    setMessages(unread.items, nextCursor = null)
                } else {
                    val page = api.messages(
                        c,
                        child = ui.selectedChild,
                        limit = MESSAGES_PAGE,
                        includeDeleted = ui.messages.showRemoved,
                    )
                    setMessages(page.items, page.nextCursor)
                }
            } catch (e: SchoolJournalError) {
                if (e is SchoolJournalError.ChildNotFound) {
                    selectChild(null)
                    return@launch
                }
                handle(e)
                _state.update { it.copy(messages = it.messages.copy(loading = false)) }
            }
        }
    }

    private fun setMessages(items: List<MessageSummary>, nextCursor: String?) {
        val gone = removed.ids.value
        _state.update {
            it.copy(
                messages = it.messages.copy(
                    items = items.filter { m -> m.id !in gone },
                    nextCursor = nextCursor,
                    loading = false,
                    loadingMore = false,
                    loaded = true,
                ),
            )
        }
    }

    /** Infinite scroll: the next page, if any. */
    fun loadMoreMessages() {
        val c = config.connection.value ?: return
        val ui = _state.value
        val cursor = ui.messages.nextCursor ?: return
        if (ui.messages.loading || ui.messages.loadingMore || ui.messages.unreadOnly) return
        _state.update { it.copy(messages = it.messages.copy(loadingMore = true)) }
        viewModelScope.launch {
            try {
                val page = api.messages(
                    c,
                    child = ui.selectedChild,
                    limit = MESSAGES_PAGE,
                    cursor = cursor,
                    includeDeleted = ui.messages.showRemoved,
                )
                _state.update {
                    val known = it.messages.items.map { m -> m.id }.toSet()
                    it.copy(
                        messages = it.messages.copy(
                            items = it.messages.items + page.items.filter { m -> m.id !in known },
                            nextCursor = page.nextCursor,
                            loadingMore = false,
                        ),
                    )
                }
            } catch (e: SchoolJournalError) {
                handle(e)
                _state.update { it.copy(messages = it.messages.copy(loadingMore = false)) }
            }
        }
    }

    fun toggleUnreadOnly() {
        _state.update { it.copy(messages = it.messages.copy(unreadOnly = !it.messages.unreadOnly)) }
        loadMessages()
    }

    fun toggleShowRemoved() {
        _state.update { it.copy(messages = it.messages.copy(showRemoved = !it.messages.showRemoved)) }
        loadMessages()
    }

    fun markRead(message: MessageSummary) = changeRead { it.markRead(message.id) }

    fun markUnread(message: MessageSummary) = changeRead { it.markUnread(message) }

    private fun changeRead(transform: (ReadState) -> ReadState) {
        updateReadState(transform)
        viewModelScope.launch { news.refresh(_state.value.status) }
    }

    private fun updateReadState(transform: (ReadState) -> ReadState) {
        val address = address ?: return
        readState.update(address, transform)?.let { new -> _state.update { it.copy(readState = new) } }
    }

    /** Overflow "Mark all as read" for the chosen child, or every child (spec §4.6). */
    fun markAllRead() {
        val c = config.connection.value ?: return
        val ui = _state.value
        val read = ui.readState ?: return
        viewModelScope.launch {
            try {
                val unread = loadUnread(api, c, read, ui.selectedChild)
                val now = Instant.now()
                updateReadState { s -> if (ui.selectedChild == null) s.markAllRead(now) else s.markRead(unread.items) }
                val count = unread.items.size
                _state.update {
                    it.copy(
                        snackbar = when (count) {
                            0 -> "No unread messages"
                            1 -> "Marked 1 message as read"
                            else -> "Marked $count messages as read"
                        },
                    )
                }
                news.refresh(ui.status)
                if (_state.value.messages.unreadOnly) loadMessages()
            } catch (e: SchoolJournalError) {
                handle(e)
            }
        }
    }

    fun snackbarShown() = _state.update { it.copy(snackbar = null) }

    fun selectTab(tab: JournalTab) {
        _state.update { it.copy(tab = tab) }
        // Showing Grades means its news has been seen (spec §6).
        val lastEventId = _state.value.status?.lastEventId ?: return
        if (tab == JournalTab.GRADES) viewModelScope.launch { news.markGradesSeen(lastEventId) }
    }

    /** Server settings from the overflow menu, or after a 401. */
    fun showSettings(notice: String? = null) {
        val connection = config.connection.value
        _state.update {
            it.copy(
                mode = JournalMode.SETUP,
                form = SetupForm(
                    address = config.address,
                    key = connection?.key.orEmpty(),
                    notice = notice,
                    editing = connection != null,
                ),
            )
        }
    }

    fun cancelSettings() {
        if (config.connection.value == null) return
        _state.update { it.copy(mode = JournalMode.CONTENT, form = SetupForm()) }
    }

    fun disconnect() {
        config.clear()
        news.clear()
        _state.value = SchoolJournalUi(mode = JournalMode.SETUP)
    }
}
