package com.github.ljarka.picfort.ui.schooljournal

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
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
) {
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
) : ViewModel() {
    private val _state = MutableStateFlow(initial())
    val state: StateFlow<SchoolJournalUi> = _state.asStateFlow()

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
                readState.ensureStarted(base.toString(), Instant.now(), status.lastEventId)
                _state.update {
                    it.copy(
                        mode = JournalMode.CONTENT,
                        form = SetupForm(),
                        status = status,
                        offline = false,
                        storageProblem = false,
                        selectedChild = config.child,
                    )
                }
                loadChildren(connection)
            } catch (e: SchoolJournalError) {
                Log.w(TAG, "Connect failed: ${e.message}")
                _state.update { it.copy(form = it.form.copy(connecting = false, error = e.userMessage())) }
            }
        }
    }

    /** Reload status and children; called when the screen starts and on pull-to-refresh. */
    fun refresh() {
        val connection = config.connection.value ?: return
        if (_state.value.mode != JournalMode.CONTENT) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                val status = api.status(connection)
                readState.ensureStarted(connection.base.toString(), Instant.now(), status.lastEventId)
                _state.update { it.copy(status = status, offline = false, storageProblem = false) }
                loadChildren(connection)
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
    }

    fun selectTab(tab: JournalTab) = _state.update { it.copy(tab = tab) }

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
        _state.value = SchoolJournalUi(mode = JournalMode.SETUP)
    }
}
