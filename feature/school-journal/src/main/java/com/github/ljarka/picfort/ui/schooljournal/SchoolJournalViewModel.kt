package com.github.ljarka.picfort.ui.schooljournal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** Null = messages and announcements together. */
    val kind: MessageKind? = null,
)

/** The grades tab (spec §4.4). */
data class GradesUi(
    /** Null = whole year. */
    val semester: Int? = null,
    /** False until the default (semester of the newest grade) has been picked. */
    val semesterChosen: Boolean = false,
    val sections: List<GradeSection> = emptyList(),
    /** "New" markers for this visit: grades changed after the seen cursor. */
    val newIds: Set<String> = emptySet(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
)

/** The configuration form (spec §3, §4.7). [editing]: opened from the content, so Cancel and Disconnect. */
data class SetupForm(
    val address: String = "",
    val key: String = "",
    val connecting: Boolean = false,
    val error: String? = null,
    /** Why the form is shown again, e.g. "The API key is no longer valid". */
    val notice: String? = null,
    val editing: Boolean = false,
    val notifications: Boolean = true,
    /** Shown when `POST_NOTIFICATIONS` was denied: how to turn it on in system settings. */
    val notificationsHint: String? = null,
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
    /** The data on screen is the offline copy saved then (spec §7). */
    val cachedAt: Instant? = null,
    val storageProblem: Boolean = false,
    /** Tab dots (spec §6). */
    val news: JournalNews = JournalNews(),
    val messages: MessagesUi = MessagesUi(),
    val grades: GradesUi = GradesUi(),
    /** This Immich user's read state (spec §4.6); null until started. */
    val readState: ReadState? = null,
    /** One-off snackbar text. */
    val snackbar: String? = null,
    /** "Sync now" is running (spec §4.5). */
    val syncing: Boolean = false,
    /** Shown in the status sheet. */
    val address: String? = null,
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val api: SchoolJournalApi,
    private val config: SchoolJournalConfigStore,
    private val readState: ReadStateStore,
    private val news: SchoolJournalNews,
    private val removed: RemovedMessages,
    private val cache: SchoolJournalCacheStore,
    private val notifyScheduler: SchoolJournalNotifyScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(initial())
    val state: StateFlow<SchoolJournalUi> = _state.asStateFlow()

    private var messagesJob: Job? = null
    private var gradesJob: Job? = null

    init {
        // The offline copy first, so the screen isn't empty while the server answers (or doesn't).
        address?.let { a ->
            viewModelScope.launch {
                withContext(Dispatchers.IO) { cache.load(a) }?.let(::showCached)
            }
        }
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
        SchoolJournalUi(mode = JournalMode.SETUP, form = SetupForm(notifications = config.notifications))
    } else {
        SchoolJournalUi(mode = JournalMode.CONTENT, selectedChild = config.child, address = config.address)
    }

    fun onAddressChange(text: String) = _state.update { it.copy(form = it.form.copy(address = text, error = null)) }

    fun onKeyChange(text: String) = _state.update { it.copy(form = it.form.copy(key = text, error = null)) }

    fun onNotificationsChange(enabled: Boolean, deniedHint: String? = null) {
        config.notifications = enabled
        notifyScheduler.start()
        _state.update {
            it.copy(
                form = it.form.copy(notifications = enabled, notificationsHint = deniedHint),
                snackbar = if (it.mode == JournalMode.CONTENT && deniedHint != null) deniedHint else it.snackbar,
            )
        }
    }

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
                notifyScheduler.start()
                val read = readState.ensureStarted(base.toString(), Instant.now(), status.lastEventId)
                _state.update {
                    it.copy(
                        mode = JournalMode.CONTENT,
                        form = SetupForm(notifications = config.notifications),
                        status = status,
                        offline = false,
                        storageProblem = false,
                        selectedChild = config.child,
                        readState = read,
                        address = config.address,
                    )
                }
                saveCache { it.copy(status = status) }
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
                _state.update {
                    it.copy(status = status, offline = false, storageProblem = false, readState = read, cachedAt = null)
                }
                saveCache { it.copy(status = status) }
                loadChildren(connection)
                news.refresh(status)
                if (_state.value.tab == JournalTab.GRADES) loadGrades()
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
            val before = _state.value.selectedChild
            val selected = config.child?.takeIf { id -> children.any { it.id == id } }
                ?: children.firstOrNull()?.id
            config.child = selected
            _state.update { it.copy(children = children, selectedChild = selected) }
            saveCache { it.copy(children = children) }
            if (selected != before && _state.value.messages.loaded) loadMessages()
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
        val chosen = id ?: _state.value.children.firstOrNull()?.id
        if (chosen == _state.value.selectedChild && config.child == chosen) return
        config.child = chosen
        // Another child's grades must not stay on screen if the server can't be reached.
        _state.update {
            it.copy(selectedChild = chosen, grades = it.grades.copy(loaded = false, sections = emptyList()))
        }
        loadMessages()
        if (_state.value.tab == JournalTab.GRADES) loadGrades()
    }

    /**
     * Grades by subject (spec §4.4): one section per child when all of several are shown (the
     * server would mix their subjects). First the "New" ids (after the seen cursor), then the
     * subjects, then the grades count as seen (spec §6).
     */
    fun loadGrades() {
        val c = config.connection.value ?: return
        gradesJob?.cancel()
        _state.update { it.copy(grades = it.grades.copy(loading = true)) }
        gradesJob = viewModelScope.launch {
            val ui = _state.value
            try {
                val fresh = ui.readState?.let { loadNewGradeIds(api, c, it.gradesSeenCursor) }.orEmpty()
                val targets = if (ui.selectedChild == null && ui.showChildChips) {
                    ui.children.map { it.id to it.name }
                } else {
                    listOf(ui.selectedChild to null)
                }
                suspend fun fetch(semester: Int?) =
                    targets.map { (id, name) -> GradeSection(id, name, api.subjects(c, id, semester)) }
                val semester = if (ui.grades.semesterChosen) ui.grades.semester else defaultSemester(fetch(null))
                val sections = fetch(semester)
                saveCache { copy ->
                    val entry = CachedGrades(semester, sections.map { it.toCached() })
                    val keys = setOf(gradesKey(ui.selectedChild, semester), lastGradesKey(ui.selectedChild))
                    copy.copy(grades = copy.grades + keys.associateWith { entry })
                }
                _state.update {
                    it.copy(
                        grades = it.grades.copy(
                            semester = semester,
                            semesterChosen = true,
                            sections = sections,
                            newIds = it.grades.newIds + fresh,
                            loading = false,
                            loaded = true,
                        ),
                    )
                }
                ui.status?.let { news.markGradesSeen(it.lastEventId) }
                address?.let { a -> readState.load(a)?.let { read -> _state.update { it.copy(readState = read) } } }
            } catch (e: SchoolJournalError) {
                if (e is SchoolJournalError.ChildNotFound) {
                    selectChild(null)
                    return@launch
                }
                handle(e)
                _state.update { it.copy(grades = it.grades.copy(loading = false)) }
                if (e.isOffline && !_state.value.grades.loaded) showCachedGrades()
            }
        }
    }

    /** [semester] null = whole year. */
    fun selectSemester(semester: Int?) {
        // Same for another semester's grades.
        _state.update {
            it.copy(
                grades = it.grades.copy(
                    semester = semester,
                    semesterChosen = true,
                    loaded = false,
                    sections = emptyList(),
                ),
            )
        }
        loadGrades()
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
                    val unread = loadUnread(api, c, read, ui.selectedChild, ui.messages.showRemoved, ui.messages.kind)
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
                        kind = ui.messages.kind,
                    )
                    setMessages(page.items, page.nextCursor)
                    saveCache {
                        it.copy(
                            messages =
                                it.messages +
                                    (
                                        messagesKey(ui.selectedChild, ui.messages.showRemoved, ui.messages.kind) to
                                            page.items
                                        ),
                        )
                    }
                }
            } catch (e: SchoolJournalError) {
                if (e is SchoolJournalError.ChildNotFound) {
                    selectChild(null)
                    return@launch
                }
                handle(e)
                _state.update { it.copy(messages = it.messages.copy(loading = false)) }
                if (e.isOffline) showCachedMessages()
            }
        }
    }

    private val SchoolJournalError.isOffline: Boolean
        get() = this is SchoolJournalError.Unreachable || this is SchoolJournalError.CleartextNotAllowed

    /** Saves after a server answer; failures to write only cost the offline copy. */
    private fun saveCache(transform: (JournalCache) -> JournalCache) {
        val a = address ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { cache.update(a, transform = transform) } }
    }

    /** Fills what the server hasn't given yet from the copy (never overwrites fresh data). */
    private fun showCached(copy: JournalCache) {
        val saved = instantOrNull(copy.savedAt)
        _state.update { ui ->
            val children = ui.children.ifEmpty { copy.children }
            val selected = ui.selectedChild?.takeIf { id -> children.isEmpty() || children.any { it.id == id } }
                ?: children.firstOrNull()?.id
            ui.copy(
                status = ui.status ?: copy.status,
                children = children,
                selectedChild = selected,
                cachedAt = if (ui.status == null) saved else ui.cachedAt,
            )
        }
        showCachedMessages(copy)
        showCachedGrades(copy)
    }

    private fun cached(): JournalCache? = address?.let { cache.load(it) }

    private fun showCachedMessages(copy: JournalCache? = cached()) {
        val ui = _state.value
        if (ui.messages.loaded || ui.messages.unreadOnly) return
        if (copy == null) return
        val kind = ui.messages.kind
        val items = copy.messages[messagesKey(ui.selectedChild, ui.messages.showRemoved, kind)]
            ?: copy.messages[messagesKey(ui.selectedChild, ui.messages.showRemoved)]
                ?.filter { kind == null || it.kind == kind }
            ?: return
        _state.update {
            it.copy(
                messages = it.messages.copy(items = items, nextCursor = null, loaded = true, loading = false),
                cachedAt = it.cachedAt ?: instantOrNull(copy.savedAt),
            )
        }
    }

    private fun showCachedGrades(copy: JournalCache? = cached()) {
        val ui = _state.value
        if (ui.grades.loaded) return
        val key = if (ui.grades.semesterChosen) {
            gradesKey(ui.selectedChild, ui.grades.semester)
        } else {
            lastGradesKey(ui.selectedChild)
        }
        val entry = copy?.grades?.get(key) ?: return
        _state.update {
            it.copy(
                grades = it.grades.copy(
                    semester = entry.semester,
                    semesterChosen = true,
                    sections = entry.sections.map { s -> s.toSection() },
                    loaded = true,
                    loading = false,
                ),
                cachedAt = it.cachedAt ?: instantOrNull(copy.savedAt),
            )
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
                    kind = ui.messages.kind,
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

    /** Null = messages and announcements together. */
    fun selectKind(kind: MessageKind?) {
        if (_state.value.messages.kind == kind) return
        _state.update { it.copy(messages = it.messages.copy(kind = kind)) }
        loadMessages()
    }

    fun toggleShowRemoved() {
        _state.update { it.copy(messages = it.messages.copy(showRemoved = !it.messages.showRemoved)) }
        loadMessages()
    }

    fun markRead(message: MessageSummary) {
        dismissMessageNotification(context, message.id)
        changeRead { it.markRead(message.id) }
    }

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
                unread.items.forEach { dismissMessageNotification(context, it.id) }
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

    /** ⟳ Sync now (spec §4.5): ask the server to check the e-journal, wait, reload. */
    fun syncNow() {
        val c = config.connection.value ?: return
        if (_state.value.syncing) return
        _state.update { it.copy(syncing = true) }
        viewModelScope.launch {
            val outcome = syncNow(api, c, _state.value.status)
            when (outcome) {
                is SyncOutcome.Done -> {
                    _state.update { it.copy(status = outcome.status, offline = false, cachedAt = null) }
                    saveCache { it.copy(status = outcome.status) }
                    news.refresh(outcome.status)
                    when (_state.value.tab) {
                        JournalTab.MESSAGES -> loadMessages()
                        JournalTab.GRADES -> loadGrades()
                    }
                }

                is SyncOutcome.AuthFailed -> runCatching { api.status(c) }.getOrNull()?.let { status ->
                    _state.update { it.copy(status = status) }
                }

                is SyncOutcome.Failed -> handle(outcome.error)

                is SyncOutcome.TooSoon, is SyncOutcome.TimedOut -> Unit
            }
            _state.update { it.copy(syncing = false, snackbar = outcome.message() ?: it.snackbar) }
        }
    }

    fun selectTab(tab: JournalTab) {
        _state.update { it.copy(tab = tab) }
        // Showing Grades means its news has been seen (spec §6); loadGrades marks it.
        if (tab == JournalTab.GRADES) loadGrades()
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
                    notifications = config.notifications,
                ),
            )
        }
    }

    fun cancelSettings() {
        if (config.connection.value == null) return
        _state.update { it.copy(mode = JournalMode.CONTENT, form = SetupForm(notifications = config.notifications)) }
    }

    fun disconnect() {
        config.clear()
        notifyScheduler.start()
        news.clear()
        _state.value = SchoolJournalUi(mode = JournalMode.SETUP, form = SetupForm(notifications = config.notifications))
    }
}
