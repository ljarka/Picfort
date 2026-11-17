package com.github.ljarka.picfort.ui.schooljournal

import android.util.Log
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What is new for the current Immich user (spec §6): the red dot and the tab dots. */
data class JournalNews(val unreadMessages: Boolean = false, val unseenGrades: Boolean = false) {
    val any: Boolean get() = unreadMessages || unseenGrades
}

/** Messages sent after the watermark are checked in one page of this size. */
internal const val NEWS_MESSAGES_LIMIT = 100

/**
 * The badge rules (spec §6) for one user's [state]; reads the server, saves nothing.
 * - unread: a message marked unread on purpose, or one sent after the watermark and not read;
 * - unseen grades: a grade change event after [ReadState.gradesSeenCursor] (asked only when
 *   [JournalStatus.lastEventId] says there is a newer event at all).
 */
internal suspend fun checkNews(
    api: SchoolJournalApi,
    c: Connection,
    state: ReadState,
    status: JournalStatus,
): JournalNews {
    val unread = state.unreadIds.isNotEmpty() ||
        api.messages(c, since = state.watermark, limit = NEWS_MESSAGES_LIMIT).items
            .any { it.deletedAt == null && !state.isRead(it) }
    val grades = status.lastEventId > state.gradesSeenCursor &&
        api.changes(c, after = state.gradesSeenCursor, type = "grade", limit = 1).items.isNotEmpty()
    return JournalNews(unreadMessages = unread, unseenGrades = grades)
}

/**
 * One [JournalNews] for the whole app: the icon on Wall and School and the journal's tabs read it.
 * Errors keep the last value (offline → the dot doesn't change). Not configured → nothing, no calls.
 */
@Singleton
class SchoolJournalNews @Inject constructor(
    private val api: SchoolJournalApi,
    private val config: SchoolJournalConfigStore,
    private val readStates: ReadStateStore,
) {
    private val _news = MutableStateFlow(JournalNews())
    val news: StateFlow<JournalNews> = _news.asStateFlow()
    private val mutex = Mutex()

    /** [status]: already loaded by the caller, saves a request. */
    suspend fun refresh(status: JournalStatus? = null) = mutex.withLock {
        val c = config.connection.value
        if (c == null) {
            _news.value = JournalNews()
            return@withLock
        }
        try {
            val s = status ?: api.status(c)
            val state = readStates.ensureStarted(c.base.toString(), Instant.now(), s.lastEventId)
            _news.value = checkNews(api, c, state, s)
        } catch (e: SchoolJournalError) {
            Log.w("SchoolJournal", "News not refreshed: ${e.message}")
        }
    }

    /** The Grades tab was shown: grade changes up to [upTo] are seen (never moves backwards). */
    suspend fun markGradesSeen(upTo: Long) {
        val c = config.connection.value ?: return
        readStates.update(c.base.toString()) { it.seenGradesUpTo(upTo) }
        _news.update { it.copy(unseenGrades = false) }
        refresh()
    }

    /** Disconnect. */
    fun clear() {
        _news.value = JournalNews()
    }
}
