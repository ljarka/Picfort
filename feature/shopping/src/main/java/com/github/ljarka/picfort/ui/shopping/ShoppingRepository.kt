package com.github.ljarka.picfort.ui.shopping

import android.content.Context
import com.github.ljarka.picfort.core.model.ShoppingItem
import com.github.ljarka.picfort.core.model.ShoppingList
import com.github.ljarka.picfort.core.network.AppScope
import com.github.ljarka.picfort.core.network.PicfortServerException
import com.github.ljarka.picfort.core.network.FrameSettingsStore
import com.github.ljarka.picfort.core.network.AccessTokenStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The active lists as last seen online, kept on the phone for the shop (T-019). */
interface OfflineCopy {
    fun save(raw: String)

    /** (saved at, epoch millis; raw JSON), or null when there is none for this server and user. */
    fun load(): Pair<Long, String>?
}

@Serializable
private data class SavedLists(val savedAt: Long, val server: String, val user: String, val raw: String)

/** A JSON file, not Room: no schema to wipe. Ignored (and deleted) for another server or user. */
@Singleton
class FileOfflineCopy @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: FrameSettingsStore,
    private val tokens: AccessTokenStore,
    private val json: Json,
) : OfflineCopy {
    private val file = File(context.filesDir, "shopping_lists.json")

    override fun save(raw: String) {
        val saved = SavedLists(System.currentTimeMillis(), settings.frameUrl, tokens.userId, raw)
        runCatching { file.writeText(json.encodeToString(SavedLists.serializer(), saved)) }
    }

    override fun load(): Pair<Long, String>? {
        val saved = runCatching { json.decodeFromString(SavedLists.serializer(), file.readText()) }.getOrNull() ?: return null
        if (saved.server != settings.frameUrl || saved.user != tokens.userId) {
            file.delete()
            return null
        }
        return saved.savedAt to saved.raw
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ShoppingModule {
    @Binds
    abstract fun backend(api: ShoppingApi): ShoppingBackend

    @Binds
    abstract fun offlineCopy(file: FileOfflineCopy): OfflineCopy
}

/**
 * The family's shopping lists (T-019). Every change is shown at once and then sent; requests
 * for one list go out one at a time, in order. A failed change is undone by loading the list
 * again (or, offline, by restoring it) and reported on [messages].
 */
@Singleton
class ShoppingRepository @Inject constructor(
    private val backend: ShoppingBackend,
    private val offline: OfflineCopy,
    @AppScope private val scope: CoroutineScope,
) {
    private val all = MutableStateFlow<Map<Long, ShoppingList>>(emptyMap())
    val lists: StateFlow<Map<Long, ShoppingList>> = all.asStateFlow()

    private val _offlineSince = MutableStateFlow<Long?>(null)
    /** When the offline copy shown was saved; null while online. Editing is off then. */
    val offlineSince: StateFlow<Long?> = _offlineSince.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val inFlight = ConcurrentHashMap<Long, Int>()
    /** Temporary (negative) item id -> the server's id, once the item is saved. */
    private val savedIds = ConcurrentHashMap<Long, Long>()
    private var nextTempId = -1L

    fun active(): List<ShoppingList> = all.value.values.filter { !it.archived }.newestFirst()
    fun archived(): List<ShoppingList> = all.value.values.filter { it.archived }.newestFirst()

    /** Loads the lists; offline, falls back to the copy on the phone. Null on success. */
    suspend fun refresh(archived: Boolean = false): PicfortServerException? = try {
        val (fresh, raw) = backend.lists(archived)
        all.update { current ->
            current.filterValues { it.archived != archived || inFlight.containsKey(it.id) } +
                fresh.filter { !inFlight.containsKey(it.id) }.associateBy { it.id }
        }
        _offlineSince.value = null
        if (!archived) offline.save(raw)
        null
    } catch (e: PicfortServerException) {
        if (e is PicfortServerException.Unreachable && !archived) useOfflineCopy()
        e
    }

    /** Polls one open list: nothing happens when it's unchanged or while changes are being sent. */
    suspend fun refreshList(id: Long): PicfortServerException? {
        if (inFlight.containsKey(id)) return null
        val known = all.value[id]?.revision
        return try {
            val fetched = backend.list(id, known).list
            _offlineSince.value = null
            if (fetched != null && !inFlight.containsKey(id)) {
                all.update { it + (id to fetched) }
                keepOfflineCopy()
            }
            null
        } catch (e: PicfortServerException.Gone) {
            all.update { it - id }
            e
        } catch (e: PicfortServerException) {
            if (e is PicfortServerException.Unreachable && all.value.isEmpty()) useOfflineCopy()
            else if (e is PicfortServerException.Unreachable) _offlineSince.value = _offlineSince.value ?: offline.load()?.first
            e
        }
    }

    suspend fun create(title: String): ShoppingList {
        val list = backend.create(title)
        all.update { it + (list.id to list) }
        return list
    }

    fun addItem(listId: Long, text: String, position: Double? = null): Job {
        val list = all.value[listId] ?: return Job()
        val temp = ShoppingItem(
            id = nextTempId--,
            text = text,
            position = position ?: ((list.items.filter { !it.checked }.maxOfOrNull { it.position } ?: 0.0) + 1.0),
        )
        return change(listId, { it.copy(items = it.items + temp) }) {
            val saved = backend.addItem(listId, text, temp.position)
            savedIds[temp.id] = saved.id
            replaceItem(listId, temp.id, saved)
        }
    }

    fun toggle(listId: Long, item: ShoppingItem, me: String): Job {
        val checked = !item.checked
        return change(listId, { list ->
            list.withItem(item.id) {
                it.copy(checked = checked, checkedBy = me.takeIf { checked }, checkedAt = Instant.now().toString().takeIf { checked })
            }
        }) {
            replaceItem(listId, item.id, backend.updateItem(listId, idOf(item.id), checked = checked))
        }
    }

    /** An emptied text deletes the item. */
    fun edit(listId: Long, item: ShoppingItem, text: String): Job {
        if (text.isBlank()) return delete(listId, item)
        return change(listId, { list -> list.withItem(item.id) { it.copy(text = text) } }) {
            replaceItem(listId, item.id, backend.updateItem(listId, idOf(item.id), text = text))
        }
    }

    fun delete(listId: Long, item: ShoppingItem): Job =
        change(listId, { list -> list.copy(items = list.items.filter { it.id != item.id }) }) {
            backend.deleteItem(listId, idOf(item.id))
        }

    /** Undo of [delete]: the same text at the same place. */
    fun restore(listId: Long, item: ShoppingItem): Job = addItem(listId, item.text, item.position)

    fun rename(listId: Long, title: String) = listChange(listId, { it.copy(title = title) }) { backend.update(listId, title = title) }

    fun archive(listId: Long, archived: Boolean) =
        listChange(listId, { it.copy(archived = archived) }) { backend.update(listId, archived = archived) }

    fun uncheckAll(listId: Long) = listChange(listId, { list ->
        list.copy(items = list.items.map { it.copy(checked = false, checkedBy = null, checkedAt = null) })
    }) { backend.uncheckAll(listId) }

    fun deleteChecked(listId: Long) =
        listChange(listId, { list -> list.copy(items = list.items.filter { !it.checked }) }) { backend.deleteChecked(listId) }

    fun deleteList(listId: Long): Job = change(listId, { null }) { backend.delete(listId) }

    // Helpers

    /**
     * Saves what the phone knows now, so a later offline start shows the open list as it was
     * last seen, not as the lists grid last loaded it. Items not yet saved are left out.
     */
    private fun keepOfflineCopy() {
        if (_offlineSince.value != null) return
        val known = active().map { list -> list.copy(items = list.items.filter { it.id > 0 }) }
        offline.save(backend.encodeLists(known))
    }

    private fun useOfflineCopy() {
        val (savedAt, raw) = offline.load() ?: return
        val copy = runCatching { backend.decodeLists(raw) }.getOrNull() ?: return
        all.value = copy.associateBy { it.id }
        _offlineSince.value = savedAt
    }

    /** A change to the whole list whose answer is the list: kept unless more changes are queued. */
    private fun listChange(listId: Long, local: (ShoppingList) -> ShoppingList, remote: suspend () -> ShoppingList) =
        change(listId, local) {
            val fresh = remote()
            if (inFlight[listId] == 1) all.update { it + (listId to fresh) }
        }

    /** [local] null removes the list. */
    private fun change(listId: Long, local: (ShoppingList) -> ShoppingList?, remote: suspend () -> Unit): Job {
        val before = all.value[listId] ?: return Job()
        all.update { lists ->
            val after = local(before)
            if (after == null) lists - listId else lists + (listId to after)
        }
        inFlight.merge(listId, 1, Int::plus)
        return scope.launch {
            try {
                locks.getOrPut(listId) { Mutex() }.withLock { remote() }
                keepOfflineCopy()
            } catch (e: PicfortServerException) {
                undo(listId, before, e)
            } finally {
                inFlight.computeIfPresent(listId) { _, n -> if (n > 1) n - 1 else null }
            }
        }
    }

    private suspend fun undo(listId: Long, before: ShoppingList, error: PicfortServerException) {
        if (error is PicfortServerException.Gone) {
            // Someone else deleted the item or the list: show the server's version quietly.
            runCatching { backend.list(listId).list }.onSuccess { fresh ->
                all.update { it + (listId to fresh!!) }
            }.onFailure { all.update { it - listId } }
            return
        }
        val fresh = runCatching { backend.list(listId).list }.getOrNull()
        all.update { it + (listId to (fresh ?: before)) }
        _messages.tryEmit("Couldn't save the change: ${error.message}")
    }

    private fun idOf(id: Long): Long = if (id < 0) savedIds[id] ?: id else id

    private fun replaceItem(listId: Long, oldId: Long, saved: ShoppingItem) {
        all.update { lists ->
            val list = lists[listId] ?: return@update lists
            lists + (listId to list.copy(items = list.items.map { if (it.id == oldId || it.id == saved.id) saved else it }))
        }
    }

    private fun ShoppingList.withItem(id: Long, change: (ShoppingItem) -> ShoppingItem) =
        copy(items = items.map { if (it.id == id) change(it) else it })

    private fun Collection<ShoppingList>.newestFirst() =
        sortedWith(compareByDescending<ShoppingList> { it.created }.thenByDescending { it.id })
}
