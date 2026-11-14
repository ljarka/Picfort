package com.github.ljarka.picfort.ui.school

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.core.model.pollEvery
import com.github.ljarka.picfort.core.network.PicfortServerClient
import com.github.ljarka.picfort.core.network.PicfortServerException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val POLL_MILLIS = 60_000L
private const val PREFS = "school"
private const val SELECTED_CHILD = "selected_child"

data class SchoolUi(
    val configured: Boolean = true,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val message: String? = null,
    val children: List<Child> = emptyList(),
    val selectedId: Long? = null,
    val day: DayOfWeek = DayOfWeek.MONDAY,
    val editing: Boolean = false,
    /** "14:32" (or "12 Nov 14:32") when showing the offline copy; editing is off then. */
    val offlineSince: String? = null,
    val today: DayOfWeek = DayOfWeek.MONDAY,
    val now: LocalTime = LocalTime.NOON,
    /** A change is being saved: forms wait. */
    val saving: Boolean = false,
) {
    /** The chosen child; the first one when it's gone. */
    val selected: Child? get() = children.firstOrNull { it.id == selectedId } ?: children.firstOrNull()
    val canEdit: Boolean get() = offlineSince == null && loadError == null
    val days: List<DayOfWeek> get() = visibleDays(selected, editing && canEdit)
}

@HiltViewModel
class SchoolViewModel @Inject constructor(
    private val repository: SchoolRepository,
    private val server: PicfortServerClient,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val local = MutableStateFlow(
        SchoolUi(
            configured = server.isConfigured,
            isLoading = !repository.hasData,
            selectedId = prefs.getLong(SELECTED_CHILD, -1L).takeIf { it >= 0 },
        ).withClock().let { it.copy(day = it.today) },
    )
    private val _state = MutableStateFlow(local.value)
    val state: StateFlow<SchoolUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(local, repository.children, repository.offlineSince) { ui, children, offline ->
                val withChildren = ui.copy(children = children, offlineSince = offline?.let(::savedAt))
                // Today's page, or Monday when today has no chip (weekend without entries).
                withChildren.copy(
                    day = withChildren.day.takeIf { it in withChildren.days }
                        ?: startDay(withChildren.today, withChildren.days),
                )
            }.collect { _state.value = it }
        }
    }

    /** Polls the server every minute while the tab is visible. */
    suspend fun keepFresh() {
        pollEvery(POLL_MILLIS) { refresh() }
    }

    /** Keeps "finishes at" and the current lesson right: the clock, every minute on the minute. */
    suspend fun tick() {
        while (true) {
            local.update { it.withClock() }
            val now = LocalDateTime.now()
            delay(ChronoUnit.MILLIS.between(now, now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)).coerceAtLeast(1))
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        if (!server.isConfigured) {
            local.update { it.copy(configured = false, isLoading = false, loadError = null) }
            return
        }
        local.update { it.copy(configured = true, isLoading = !repository.hasData) }
        val error = repository.refresh()
        val shown = repository.hasData
        local.update {
            it.copy(
                isLoading = false,
                // With something on screen (e.g. the offline copy) an error isn't a dead end.
                loadError = error?.message?.takeIf { !shown },
                editing = it.editing && error == null,
            )
        }
    }

    fun selectChild(id: Long) {
        prefs.edit { putLong(SELECTED_CHILD, id) }
        local.update { it.copy(selectedId = id) }
    }

    fun selectDay(day: DayOfWeek) = local.update { it.copy(day = day) }

    fun toggleEditing() = local.update { it.copy(editing = !it.editing) }

    fun messageShown() = local.update { it.copy(message = null) }

    fun addChild(name: String, color: String, done: () -> Unit) = save(done) {
        val child = repository.addChild(name, color)
        selectChild(child.id)
    }

    fun updateChild(id: Long, name: String, color: String, done: () -> Unit) =
        save(done) { repository.updateChild(id, name, color) }

    fun deleteChild(id: Long, done: () -> Unit) = save(done) { repository.deleteChild(id) }

    fun savePeriods(childId: Long, periods: List<Period>, done: () -> Unit) =
        save(done) { repository.setPeriods(childId, periods) }

    fun copyPeriods(childId: Long, fromChild: Long, done: () -> Unit) =
        save(done) { repository.copyPeriods(childId, fromChild) }

    /** [entryId] null adds a new entry. */
    fun saveEntry(childId: Long, entryId: Long?, draft: EntryDraft, done: () -> Unit) = save(done) {
        if (entryId == null) {
            repository.addEntry(childId, draft)
        } else {
            repository.updateEntry(childId, entryId, draft)
        }
    }

    fun deleteEntry(childId: Long, entryId: Long, done: () -> Unit) =
        save(done) { repository.deleteEntry(childId, entryId) }

    fun copyDay(childId: Long, from: DayOfWeek, to: List<DayOfWeek>, done: () -> Unit) =
        save(done) { repository.copyDay(childId, from, to) }

    private fun save(done: () -> Unit, change: suspend () -> Unit) {
        if (local.value.saving) return
        viewModelScope.launch {
            local.update { it.copy(saving = true) }
            try {
                change()
                done()
            } catch (e: PicfortServerException) {
                local.update { it.copy(message = "Couldn't save: ${e.message}") }
            } finally {
                local.update { it.copy(saving = false) }
            }
        }
    }

    private fun savedAt(millis: Long): String {
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        val pattern = if (at.toLocalDate() == LocalDate.now()) "HH:mm" else "d MMM HH:mm"
        return at.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    private fun SchoolUi.withClock(): SchoolUi {
        val now = LocalDateTime.now()
        return copy(today = now.dayOfWeek, now = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
    }
}
