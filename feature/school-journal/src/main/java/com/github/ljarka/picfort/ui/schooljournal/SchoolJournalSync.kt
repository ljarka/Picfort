package com.github.ljarka.picfort.ui.schooljournal

import kotlinx.coroutines.delay

/** How "Sync now" ended (spec §4.5). */
sealed interface SyncOutcome {
    /** A new run finished (ok or not); [status] is the server's latest. */
    data class Done(val status: JournalStatus) : SyncOutcome

    /** 429 `SYNC_TOO_SOON`. */
    data class TooSoon(val error: SchoolJournalError.SyncTooSoon) : SyncOutcome

    /** 409 `AUTH_FAILED`: the server can't log in to the e-journal. */
    data object AuthFailed : SyncOutcome

    /** Still running after [SYNC_MAX_WAIT_MILLIS]. */
    data object TimedOut : SyncOutcome

    data class Failed(val error: SchoolJournalError) : SyncOutcome
}

internal const val SYNC_POLL_MILLIS = 5_000L
internal const val SYNC_MAX_WAIT_MILLIS = 120_000L

/**
 * `POST /sync`, then `/status` every 5 s (max 2 min) until a run newer than [before] has finished.
 * A sync already running (409 `SYNC_IN_PROGRESS`) is waited for the same way. [sleep] is
 * replaced in tests.
 */
internal suspend fun syncNow(
    api: SchoolJournalApi,
    c: Connection,
    before: JournalStatus?,
    sleep: suspend (Long) -> Unit = { delay(it) },
): SyncOutcome {
    try {
        api.sync(c)
    } catch (e: SchoolJournalError.SyncInProgress) {
        // Someone (or the schedule) started it: wait for that run.
    } catch (e: SchoolJournalError.SyncTooSoon) {
        return SyncOutcome.TooSoon(e)
    } catch (e: SchoolJournalError.AuthFailed) {
        return SyncOutcome.AuthFailed
    } catch (e: SchoolJournalError) {
        return SyncOutcome.Failed(e)
    }
    val previousStart = before?.lastSync?.startedAt
    var waited = 0L
    while (waited < SYNC_MAX_WAIT_MILLIS) {
        sleep(SYNC_POLL_MILLIS)
        waited += SYNC_POLL_MILLIS
        val status = try {
            api.status(c)
        } catch (e: SchoolJournalError) {
            return SyncOutcome.Failed(e)
        }
        val run = status.lastSync ?: continue
        if (!run.running && run.startedAt != previousStart) return SyncOutcome.Done(status)
    }
    return SyncOutcome.TimedOut
}

/** The snackbar after "Sync now". */
fun SyncOutcome.message(): String? = when (this) {
    is SyncOutcome.Done -> when {
        status.lastSync?.failed == true -> "The check of the e-journal failed"

        else -> when (val n = status.lastSync?.changesDetected ?: 0) {
            0 -> "Checked the e-journal: nothing new"
            1 -> "Checked the e-journal: 1 change"
            else -> "Checked the e-journal: $n changes"
        }
    }

    is SyncOutcome.TooSoon -> error.userMessage()

    // The auth banner says it.
    is SyncOutcome.AuthFailed -> null

    is SyncOutcome.TimedOut -> "Still checking the e-journal. Pull down in a moment."

    is SyncOutcome.Failed -> error.userMessage()
}
