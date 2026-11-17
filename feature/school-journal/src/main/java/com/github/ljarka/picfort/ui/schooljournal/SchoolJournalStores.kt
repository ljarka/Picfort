package com.github.ljarka.picfort.ui.schooljournal

import android.content.Context
import com.github.ljarka.picfort.core.network.AccessTokenStore
import com.github.ljarka.picfort.core.network.SecurePreferencesStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

private const val KEY_URL = "schoolJournal.url"
private const val KEY_KEY = "schoolJournal.key"
private const val KEY_CHILD = "schoolJournal.child"

/** The school-server address and key (encrypted), and the chosen child (spec §3, §4.2). */
@Singleton
class SchoolJournalConfigStore @Inject constructor(
    private val prefs: SecurePreferencesStore,
    @ApplicationContext private val context: Context,
) {
    private val _connection = MutableStateFlow(load())

    /** Null until configured. */
    val connection: StateFlow<Connection?> = _connection.asStateFlow()

    val address: String get() = prefs.getString(KEY_URL)

    /** Child id, or null for all children. */
    var child: String?
        get() = prefs.getString(KEY_CHILD).ifEmpty { null }
        set(value) = prefs.putString(KEY_CHILD, value.orEmpty())

    fun save(connection: Connection) {
        prefs.putString(KEY_URL, connection.base.toString())
        prefs.putString(KEY_KEY, connection.key)
        _connection.value = connection
    }

    /** Disconnect: address, key, child and every file of the module (read state, cache). */
    fun clear() {
        prefs.putString(KEY_URL, "")
        prefs.putString(KEY_KEY, "")
        prefs.putString(KEY_CHILD, "")
        journalDir(context).deleteRecursively()
        _connection.value = null
    }

    private fun load(): Connection? {
        val base = normalizeJournalAddress(prefs.getString(KEY_URL)) ?: return null
        return Connection(base, prefs.getString(KEY_KEY))
    }
}

/** Everything the module keeps on disk; deleting it is part of Disconnect and of removal. */
internal fun journalDir(context: Context): File = File(context.filesDir, "school-journal")

/**
 * What one Immich user has read and seen (spec §4.6). Kept on the phone, never on a server.
 * A message is read if in [readIds], or sent before [watermark] and not in [unreadIds].
 */
@Serializable
data class ReadState(
    /** ISO instant: everything sent up to here counts as read unless marked unread. */
    val watermark: String,
    val readIds: Set<String> = emptySet(),
    val unreadIds: Set<String> = emptySet(),
    /** Grade change events up to this id have been seen (Grades tab). */
    val gradesSeenCursor: Long = 0,
    /** Change events up to this id have been notified about (T-082). */
    val notifyCursor: Long = 0,
) {
    fun isRead(message: MessageSummary): Boolean {
        if (message.id in readIds) return true
        if (message.id in unreadIds) return false
        val sent = instantOrNull(message.sentAt) ?: return false
        return !sent.isAfter(Instant.parse(watermark))
    }

    /** Grade changes up to [upTo] are seen; the cursor never moves backwards. */
    fun seenGradesUpTo(upTo: Long): ReadState = copy(gradesSeenCursor = maxOf(gradesSeenCursor, upTo))

    companion object {
        /** A fresh start: history is neither unread nor new. */
        fun startingAt(now: Instant, lastEventId: Long) = ReadState(
            // Whole seconds: the server compares `since` as written.
            watermark = now.truncatedTo(ChronoUnit.SECONDS).toString(),
            gradesSeenCursor = lastEventId,
            notifyCursor = lastEventId,
        )
    }
}

/** [ReadState] per Immich user and school-server address, as a JSON file in [journalDir]. */
@Singleton
class ReadStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokens: AccessTokenStore,
) {
    fun load(address: String): ReadState? = file(address).takeIf { it.exists() }?.let { f ->
        runCatching { journalJson.decodeFromString<ReadState>(f.readText()) }.getOrNull()
    }

    fun save(address: String, state: ReadState) {
        val f = file(address)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(journalJson.encodeToString(ReadState.serializer(), state))
        tmp.renameTo(f)
    }

    /** Changes this user's state, if started. */
    fun update(address: String, transform: (ReadState) -> ReadState) {
        load(address)?.let { save(address, transform(it)) }
    }

    /** Starts this user's state if they have none yet (first connect, or another user on this phone). */
    fun ensureStarted(address: String, now: Instant, lastEventId: Long): ReadState =
        load(address) ?: ReadState.startingAt(now, lastEventId).also { save(address, it) }

    private fun file(address: String): File =
        File(journalDir(context), "state-${shortHash(address)}-${shortHash(tokens.userId.ifEmpty { "local" })}.json")
}

internal fun shortHash(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
