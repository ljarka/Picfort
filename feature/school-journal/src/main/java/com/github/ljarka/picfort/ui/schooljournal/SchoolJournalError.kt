package com.github.ljarka.picfort.ui.schooljournal

/**
 * Why a school-server call failed (spec §5). Mapped from the `{"error":{"code","message"}}` body
 * by [code]; the server's `message` only goes to logs, the user sees [userMessage].
 */
sealed class SchoolJournalError(message: String) : Exception(message) {
    /** No answer: offline, wrong address, timeout. */
    class Unreachable(val host: String) : SchoolJournalError("Can't reach the school server at $host")

    /** Plain http:// to a host outside the home network (CleartextPolicyInterceptor). */
    class CleartextNotAllowed(val host: String) : SchoolJournalError("Plain http to $host")

    /** `/health` answered, but not like a school-server. */
    class NotSchoolServer(val host: String) : SchoolJournalError("$host is not a school server")

    /** 401: missing or wrong `X-School-Key`. */
    class WrongKey : SchoolJournalError("Wrong API key")

    /** 429 `TOO_MANY_WRONG_KEYS`. */
    class TooManyWrongKeys(val retryAfterSeconds: Long?) : SchoolJournalError("Too many wrong keys")

    /** 429 `SYNC_TOO_SOON`. */
    class SyncTooSoon(val retryAfterSeconds: Long?) : SchoolJournalError("Sync too soon")

    /** 409 `SYNC_IN_PROGRESS`. */
    class SyncInProgress : SchoolJournalError("Sync in progress")

    /** 409 `AUTH_FAILED`: the server can't log in to the e-journal. */
    class AuthFailed : SchoolJournalError("The server can't log in to the e-journal")

    /** 404 `NOT_FOUND`: the item is gone. */
    class NotFound : SchoolJournalError("Not found")

    /** 404 `CHILD_NOT_FOUND`: the `child` filter is unknown. */
    class ChildNotFound : SchoolJournalError("Unknown child")

    /** 503 `NO_DATA_YET`: no successful sync yet. */
    class NoDataYet : SchoolJournalError("No data yet")

    /** 503 `STORAGE_UNAVAILABLE`. */
    class StorageUnavailable : SchoolJournalError("Server storage unavailable")

    /** 400 `BAD_REQUEST`: a bug on our side. */
    class BadRequest(detail: String) : SchoolJournalError("Bad request: $detail")

    /** Anything else (unknown code, unreadable body). */
    class Other(val http: Int, val code: String, detail: String) : SchoolJournalError("HTTP $http $code: $detail")

    fun userMessage(): String = when (this) {
        is Unreachable -> "Can't reach the school server at $host"
        is CleartextNotAllowed -> "Plain http:// only works on the home network. Use https://"
        is NotSchoolServer -> "$host doesn't look like a school server"
        is WrongKey -> "Wrong API key"
        is TooManyWrongKeys -> "Too many wrong keys. Try again ${retryText(retryAfterSeconds)}"
        is SyncTooSoon -> "Checked recently. Try again ${retryText(retryAfterSeconds)}"
        is SyncInProgress -> "The server is checking the e-journal right now"
        is AuthFailed -> "The server can't log in to the e-journal"
        is NotFound -> "It's no longer in the e-journal"
        is ChildNotFound -> "That child is no longer on the server"
        is NoDataYet -> "The first check of the e-journal is still running"
        is StorageUnavailable -> "The school server has a storage problem"
        is BadRequest, is Other -> "Something went wrong with the school server"
    }

    companion object {
        /** Maps a non-2xx answer. */
        fun of(http: Int, body: ErrorBody?, retryAfter: String?, host: String): SchoolJournalError {
            val code = body?.error?.code.orEmpty()
            val detail = body?.error?.message.orEmpty()
            val retry = retryAfter?.trim()?.toLongOrNull()
            return when {
                http == 401 -> WrongKey()

                code == "TOO_MANY_WRONG_KEYS" -> TooManyWrongKeys(retry)

                code == "SYNC_TOO_SOON" -> SyncTooSoon(retry)

                code == "SYNC_IN_PROGRESS" -> SyncInProgress()

                code == "AUTH_FAILED" -> AuthFailed()

                code == "CHILD_NOT_FOUND" -> ChildNotFound()

                code == "NO_DATA_YET" -> NoDataYet()

                code == "STORAGE_UNAVAILABLE" -> StorageUnavailable()

                code == "BAD_REQUEST" || http == 400 -> BadRequest(detail)

                http == 404 -> NotFound()

                // A 429 without a known code is still the limiter.
                http == 429 -> TooManyWrongKeys(retry)

                else -> Other(http, code, detail.ifEmpty { "from $host" })
            }
        }

        private fun retryText(seconds: Long?): String = when {
            seconds == null || seconds <= 0 -> "later"
            seconds < 60 -> "in a minute"
            else -> "in ${(seconds + 59) / 60} min"
        }
    }
}
