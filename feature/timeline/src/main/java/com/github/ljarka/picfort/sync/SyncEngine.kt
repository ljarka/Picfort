package com.github.ljarka.picfort.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/** Lines written and acked per transaction. */
internal const val SYNC_BATCH = 500

/** The server accepts at most this many acks per call. */
private const val MAX_ACKS = 1000

data class SyncResult(
    /** Lines read in the stream that completed, including ack-only ones. */
    val lines: Int,
    /** Whether the copy was rebuilt from scratch (new owner, requested, or the server's reset). */
    val wasReset: Boolean,
)

/**
 * Keeps the local copy in step with Immich's sync stream (ADR 0005):
 * lines are applied in transactions of [SYNC_BATCH], and the latest ack of each type is
 * sent only after its transaction commits, so an interrupted sync resumes without gaps.
 */
@Singleton
class SyncEngine @Inject constructor(private val api: SyncApi, private val store: SyncStore, private val json: Json) {
    private val mutex = Mutex()
    private val _linesApplied = MutableStateFlow<Int?>(null)

    /** Lines applied so far by the running sync; null when none is running. */
    val linesApplied: StateFlow<Int?> = _linesApplied.asStateFlow()

    /**
     * Runs one sync; concurrent calls wait for each other.
     *
     * [owner] identifies the server and login session (checkpoints live on the server per
     * session). If the local copy belongs to someone else or is empty (e.g. the database was
     * wiped), or [reset] is set, the copy is dropped and the server asked for everything:
     * a client-requested reset sends no `SyncResetV1` (T-015 live check).
     * Network and HTTP errors are thrown; whatever was acked before them stays.
     */
    suspend fun sync(owner: String, reset: Boolean = false): SyncResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            _linesApplied.value = 0
            try {
                val fromScratch = reset || store.owner() != owner
                if (fromScratch) {
                    store.clear()
                    store.setOwner(owner)
                }
                val first = stream(fromScratch)
                if (first.serverReset) {
                    store.setOwner(owner)
                    SyncResult(stream(reset = false).lines, wasReset = true)
                } else {
                    SyncResult(first.lines, wasReset = fromScratch)
                }
            } finally {
                _linesApplied.value = null
            }
        }
    }

    private class StreamOutcome(val lines: Int, val serverReset: Boolean)

    private suspend fun stream(reset: Boolean): StreamOutcome {
        val changes = ArrayList<SyncChange>(SYNC_BATCH)
        val acks = LinkedHashMap<String, String>() // latest ack per checkpoint type
        var lines = 0
        var pending = 0

        suspend fun flush() {
            if (changes.isNotEmpty()) store.apply(changes.toList())
            sendAcks(acks.values.toList())
            _linesApplied.value = (_linesApplied.value ?: 0) + pending
            changes.clear()
            acks.clear()
            pending = 0
        }

        api.stream(SyncStreamRequest(TIMELINE_SYNC_TYPES, reset)).use { body ->
            val source = body.source()
            while (true) {
                coroutineContext.ensureActive()
                val text = source.readUtf8Line() ?: break
                if (text.isBlank()) continue
                val line = json.decodeFromString<SyncLine>(text)
                lines++
                when (val change = line.toChange(json)) {
                    SyncChange.Reset -> {
                        // The server wants a fresh copy. Acking the reset clears its flag.
                        flush()
                        store.clear()
                        sendAcks(listOf(line.ack))
                        return StreamOutcome(lines, serverReset = true)
                    }

                    SyncChange.AckOnly -> Unit

                    else -> changes += change
                }
                acks[line.ackType] = line.ack
                if (++pending >= SYNC_BATCH) flush()
            }
        }
        flush()
        return StreamOutcome(lines, serverReset = false)
    }

    private suspend fun sendAcks(acks: List<String>) {
        acks.chunked(MAX_ACKS).forEach { chunk ->
            val response = api.ack(SyncAckRequest(chunk))
            if (!response.isSuccessful) throw HttpException(response)
        }
    }
}
