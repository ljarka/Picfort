package com.github.ljarka.picfort.ui.events

import com.github.ljarka.picfort.core.network.PicfortServerException
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EventsModule {
    @Binds
    abstract fun backend(api: EventsApi): EventsBackend
}

/**
 * Shared state for family events (T-023), read by the Events tab and the Family Wall's today panel (T-027).
 * Unlike shopping lists, changes are not optimistic: the list reloads after a successful save or delete.
 */
@Singleton
class EventsRepository(
    private val backend: EventsBackend,
    private val todayProvider: () -> LocalDate,
) {
    @Inject
    constructor(backend: EventsBackend) : this(backend, LocalDate::now)

    private val _segment = MutableStateFlow(EventsSegment.UPCOMING)
    val segment: StateFlow<EventsSegment> = _segment.asStateFlow()

    private val _today = MutableStateFlow(todayProvider())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _upcoming = MutableStateFlow<List<FamilyEvent>>(emptyList())
    val upcoming: StateFlow<List<FamilyEvent>> = _upcoming.asStateFlow()

    private val _past = MutableStateFlow<List<FamilyEvent>>(emptyList())
    val past: StateFlow<List<FamilyEvent>> = _past.asStateFlow()

    private val _hasMorePast = MutableStateFlow(false)
    val hasMorePast: StateFlow<Boolean> = _hasMorePast.asStateFlow()

    private val _isLoadingMorePast = MutableStateFlow(false)
    val isLoadingMorePast: StateFlow<Boolean> = _isLoadingMorePast.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private var pastLoadedForDate: LocalDate? = null

    fun setSegment(segment: EventsSegment) {
        _segment.value = segment
    }

    fun postMessage(message: String) {
        _messages.tryEmit(message)
    }

    fun cachedEvent(id: Long): FamilyEvent? =
        _upcoming.value.firstOrNull { it.id == id } ?: _past.value.firstOrNull { it.id == id }

    /**
     * Recomputes "today" and moves any events that ended before today from [upcoming] to [past]
     * immediately, before the network refresh completes.
     */
    fun rollOverMidnight(): LocalDate {
        val currentToday = todayProvider()
        val previousToday = _today.value
        if (currentToday != previousToday) {
            _today.value = currentToday
            val (stillUpcoming, newlyPast) = _upcoming.value.partition {
                !it.effectiveEndDate.isBefore(currentToday)
            }
            if (newlyPast.isNotEmpty()) {
                _upcoming.value = stillUpcoming
                _past.update { existing ->
                    (newlyPast + existing).distinctBy { it.id }
                }
            }
        }
        return currentToday
    }

    suspend fun refreshUpcoming(date: LocalDate = rollOverMidnight()): PicfortServerException? = try {
        val fresh = backend.upcoming(date)
        _upcoming.value = fresh
        null
    } catch (e: PicfortServerException) {
        if (_upcoming.value.isNotEmpty()) {
            _messages.tryEmit(e.message.orEmpty())
        }
        e
    }

    suspend fun refreshPast(date: LocalDate = rollOverMidnight()): PicfortServerException? = try {
        val fresh = backend.past(date, limit = PAST_PAGE_SIZE, offset = 0)
        _past.value = fresh
        _hasMorePast.value = fresh.size >= PAST_PAGE_SIZE
        pastLoadedForDate = date
        null
    } catch (e: PicfortServerException) {
        if (_past.value.isNotEmpty()) {
            _messages.tryEmit(e.message.orEmpty())
        }
        e
    }

    suspend fun refreshActive(forceBoth: Boolean = false): PicfortServerException? {
        val previousDate = _today.value
        val date = rollOverMidnight()
        val dateChanged = date != previousDate
        return if (_segment.value == EventsSegment.UPCOMING) {
            val err = refreshUpcoming(date)
            if (forceBoth || dateChanged || pastLoadedForDate != null) {
                refreshPast(date)
            }
            err
        } else {
            val err = refreshPast(date)
            if (forceBoth || dateChanged) {
                refreshUpcoming(date)
            }
            err
        }
    }

    suspend fun loadMorePast(): PicfortServerException? {
        if (_isLoadingMorePast.value || !_hasMorePast.value) return null
        _isLoadingMorePast.value = true
        return try {
            val date = rollOverMidnight()
            val offset = _past.value.size
            val next = backend.past(date, limit = PAST_PAGE_SIZE, offset = offset)
            _past.update { current -> (current + next).distinctBy { it.id } }
            _hasMorePast.value = next.size >= PAST_PAGE_SIZE
            null
        } catch (e: PicfortServerException) {
            _messages.tryEmit(e.message.orEmpty())
            e
        } finally {
            _isLoadingMorePast.value = false
        }
    }

    suspend fun fetchEvent(id: Long): FamilyEvent {
        val fresh = backend.event(id)
        replaceInCache(fresh)
        return fresh
    }

    suspend fun create(draft: EventDraft): FamilyEvent {
        val created = backend.create(draft)
        val date = rollOverMidnight()
        refreshUpcoming(date)
        if (pastLoadedForDate != null || created.effectiveEndDate.isBefore(date)) {
            refreshPast(date)
        }
        return created
    }

    suspend fun update(original: FamilyEvent, draft: EventDraft): FamilyEvent {
        val updated = backend.update(original, draft)
        val date = rollOverMidnight()
        refreshUpcoming(date)
        if (pastLoadedForDate != null || updated.effectiveEndDate.isBefore(date) || original.effectiveEndDate.isBefore(date)) {
            refreshPast(date)
        }
        return updated
    }

    suspend fun delete(id: Long) {
        try {
            backend.delete(id)
        } finally {
            removeFromCache(id)
        }
        val date = rollOverMidnight()
        refreshUpcoming(date)
        if (pastLoadedForDate != null) {
            refreshPast(date)
        }
    }

    fun removeFromCache(id: Long) {
        _upcoming.update { list -> list.filterNot { it.id == id } }
        _past.update { list -> list.filterNot { it.id == id } }
    }

    private fun replaceInCache(event: FamilyEvent) {
        val date = _today.value
        if (!event.effectiveEndDate.isBefore(date)) {
            _upcoming.update { list ->
                val idx = list.indexOfFirst { it.id == event.id }
                if (idx >= 0) list.toMutableList().apply { set(idx, event) } else list
            }
        } else {
            _past.update { list ->
                val idx = list.indexOfFirst { it.id == event.id }
                if (idx >= 0) list.toMutableList().apply { set(idx, event) } else list
            }
        }
    }
}
