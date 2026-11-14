package com.github.ljarka.picfort.ui.school

import android.content.Context
import com.github.ljarka.picfort.core.network.AccessTokenStore
import com.github.ljarka.picfort.core.network.FrameSettingsStore
import com.github.ljarka.picfort.core.network.PicfortServerException
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The timetables as last seen online, for "when does Ola finish?" away from home (T-029). */
interface SchoolOfflineCopy {
    fun save(raw: String)

    /** (saved at, epoch millis; raw JSON), or null when there is none for this server and user. */
    fun load(): Pair<Long, String>?
}

@Serializable
private data class SavedSchool(val savedAt: Long, val server: String, val user: String, val raw: String)

/** A JSON file. Ignored (and deleted) after a logout or a change of server: it names both. */
@Singleton
class FileSchoolOfflineCopy @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: FrameSettingsStore,
    private val tokens: AccessTokenStore,
    private val json: Json,
) : SchoolOfflineCopy {
    private val file = File(context.filesDir, "school.json")

    override fun save(raw: String) {
        val saved = SavedSchool(System.currentTimeMillis(), settings.frameUrl, tokens.userId, raw)
        runCatching { file.writeText(json.encodeToString(SavedSchool.serializer(), saved)) }
    }

    override fun load(): Pair<Long, String>? {
        val saved =
            runCatching { json.decodeFromString(SavedSchool.serializer(), file.readText()) }.getOrNull() ?: return null
        if (saved.server != settings.frameUrl || saved.user != tokens.userId) {
            file.delete()
            return null
        }
        return saved.savedAt to saved.raw
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SchoolModule {
    @Binds
    abstract fun backend(api: SchoolApi): SchoolBackend

    @Binds
    abstract fun offlineCopy(file: FileSchoolOfflineCopy): SchoolOfflineCopy
}

/**
 * The children's timetables (T-029). Not optimistic: a change is shown once the server has it
 * (the answer replaces the child, or everything is loaded again). A child or entry someone else
 * deleted (404) reloads quietly.
 */
@Singleton
class SchoolRepository @Inject constructor(
    private val backend: SchoolBackend,
    private val offline: SchoolOfflineCopy,
) {
    private val _children = MutableStateFlow<List<Child>>(emptyList())
    val children: StateFlow<List<Child>> = _children.asStateFlow()

    private val _offlineSince = MutableStateFlow<Long?>(null)

    /** When the offline copy shown was saved; null while online. Editing is off then. */
    val offlineSince: StateFlow<Long?> = _offlineSince.asStateFlow()

    private var loaded = false

    /** Whether anything (fresh or the offline copy) has been shown yet. */
    val hasData: Boolean get() = loaded

    /** Loads everything; unreachable, falls back to the copy on the phone. Null on success. */
    suspend fun refresh(): PicfortServerException? = try {
        val (fresh, raw) = backend.children()
        _children.value = fresh
        _offlineSince.value = null
        loaded = true
        offline.save(raw)
        null
    } catch (e: PicfortServerException) {
        if (e is PicfortServerException.Unreachable) useOfflineCopy()
        e
    }

    suspend fun addChild(name: String, color: String): Child = backend.addChild(name.trim(), color).also(::put)

    suspend fun updateChild(id: Long, name: String, color: String) = change {
        backend.updateChild(id, name.trim(), color)
    }

    suspend fun deleteChild(id: Long) = quietlyGone {
        backend.deleteChild(id)
        _children.update { list -> list.filter { it.id != id } }
    }

    suspend fun setPeriods(childId: Long, periods: List<Period>) = change { backend.setPeriods(childId, periods) }

    suspend fun copyPeriods(childId: Long, fromChild: Long) = change { backend.copyPeriods(childId, fromChild) }

    suspend fun addEntry(childId: Long, draft: EntryDraft) = reloading { backend.addEntry(childId, draft) }

    suspend fun updateEntry(childId: Long, entryId: Long, draft: EntryDraft) =
        reloading { backend.updateEntry(childId, entryId, draft) }

    suspend fun deleteEntry(childId: Long, entryId: Long) = reloading { backend.deleteEntry(childId, entryId) }

    /** Copies [from]'s lessons to each of [to], one day after another. */
    suspend fun copyDay(childId: Long, from: DayOfWeek, to: List<DayOfWeek>) = quietlyGone {
        to.forEach { day -> put(backend.copyDay(childId, from, day)) }
    }

    /** A change answered with the child: the answer replaces it. */
    private suspend fun change(call: suspend () -> Child) = quietlyGone { put(call()) }

    /** A change answered with an entry only: everything is loaded again. */
    private suspend fun reloading(call: suspend () -> Unit) = quietlyGone {
        call()
        refresh()
    }

    /** A 404 means someone else deleted the child or entry: show the server's version, no error. */
    private suspend fun quietlyGone(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: PicfortServerException.Gone) {
            refresh()
        }
    }

    private fun put(child: Child) {
        _children.update { list ->
            (list.filter { it.id != child.id } + child).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }

    private fun useOfflineCopy() {
        if (_offlineSince.value != null) return
        val (savedAt, raw) = offline.load() ?: return
        val copy = runCatching { backend.decode(raw) }.getOrNull() ?: return
        _children.value = copy
        _offlineSince.value = savedAt
        loaded = true
    }
}
