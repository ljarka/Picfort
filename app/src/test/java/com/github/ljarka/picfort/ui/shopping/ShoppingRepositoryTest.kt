package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.network.PicfortServerException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An in-memory server. [gate] holds the next write until completed; [fail] makes writes fail. */
private class FakeBackend : ShoppingBackend {
    val lists = mutableMapOf<Long, ShoppingList>()
    var nextId = 100L
    var fail: PicfortServerException? = null
    var down = false
    var gate: CompletableDeferred<Unit>? = null
    val writes = mutableListOf<String>()

    private suspend fun write(name: String) {
        gate?.await()
        writes += name
        fail?.let { throw it }
    }

    private fun read() {
        if (down) throw PicfortServerException.Unreachable("server")
    }

    private fun bump(id: Long, change: (ShoppingList) -> ShoppingList): ShoppingList {
        val list = lists[id] ?: throw PicfortServerException.Gone("gone")
        return change(list).copy(revision = list.revision + 1).also { lists[id] = it }
    }

    override suspend fun lists(archived: Boolean): Pair<List<ShoppingList>, String> {
        read()
        val found = lists.values.filter { it.archived == archived }
        return found to "raw:" + found.joinToString(",") { "${it.id}" }
    }

    override fun decodeLists(raw: String) = raw.removePrefix("raw:").split(",").filter { it.isNotEmpty() }
        .map { ShoppingList(it.toLong(), "offline ${it}") }

    override suspend fun list(id: Long, knownRevision: Long?): ListFetch {
        read()
        val list = lists[id] ?: throw PicfortServerException.Gone("gone")
        return ListFetch(list.takeIf { it.revision != knownRevision })
    }

    override suspend fun create(title: String) = ShoppingList(nextId++, title, createdBy = "me").also { lists[it.id] = it }

    override suspend fun update(id: Long, title: String?, archived: Boolean?): ShoppingList {
        write("update")
        return bump(id) { it.copy(title = title ?: it.title, archived = archived ?: it.archived) }
    }

    override suspend fun delete(id: Long) {
        write("delete")
        lists.remove(id)
    }

    override suspend fun uncheckAll(id: Long): ShoppingList {
        write("uncheckAll")
        return bump(id) { l -> l.copy(items = l.items.map { it.copy(checked = false, checkedBy = null) }) }
    }

    override suspend fun deleteChecked(id: Long): ShoppingList {
        write("deleteChecked")
        return bump(id) { l -> l.copy(items = l.items.filter { !it.checked }) }
    }

    override suspend fun addItem(listId: Long, text: String, position: Double?): ShoppingItem {
        write("add $text")
        val item = ShoppingItem(nextId++, text, position = position ?: 99.0)
        bump(listId) { it.copy(items = it.items + item) }
        return item
    }

    override suspend fun updateItem(listId: Long, itemId: Long, text: String?, checked: Boolean?): ShoppingItem {
        write("update $itemId")
        var updated: ShoppingItem? = null
        bump(listId) { l ->
            l.copy(items = l.items.map {
                if (it.id != itemId) it
                else it.copy(text = text ?: it.text, checked = checked ?: it.checked, checkedBy = if (checked == true) "me" else it.checkedBy)
                    .also { u -> updated = u }
            })
        }
        return updated ?: throw PicfortServerException.Gone("gone")
    }

    override suspend fun deleteItem(listId: Long, itemId: Long) {
        write("delete $itemId")
        bump(listId) { l -> l.copy(items = l.items.filter { it.id != itemId }) }
    }
}

private class MemoryCopy : OfflineCopy {
    var saved: Pair<Long, String>? = null
    override fun save(raw: String) {
        saved = 1_000L to raw
    }

    override fun load() = saved
}

class ShoppingRepositoryTest {
    private val backend = FakeBackend().apply {
        lists[1] = ShoppingList(1, "Biedronka", createdBy = "me", items = listOf(
            ShoppingItem(10, "milk", position = 1.0), ShoppingItem(11, "bread", position = 2.0),
        ))
    }
    private val copy = MemoryCopy()
    private val repo = ShoppingRepository(backend, copy, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private fun list() = repo.lists.value.getValue(1)

    @Test
    fun `refresh loads the lists and keeps an offline copy`() = runBlocking {
        assertNull(repo.refresh())
        assertEquals(listOf("Biedronka"), repo.active().map { it.title })
        assertEquals("raw:1", copy.saved?.second)
    }

    @Test
    fun `offline the copy is shown read-only, and online again clears it`() = runBlocking {
        repo.refresh()
        backend.down = true
        val fresh = ShoppingRepository(backend, copy, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        assertTrue(fresh.refresh() is PicfortServerException.Unreachable)
        assertEquals(listOf("offline 1"), fresh.active().map { it.title })
        assertEquals(1_000L, fresh.offlineSince.value)
        backend.down = false
        assertNull(fresh.refresh())
        assertNull(fresh.offlineSince.value)
        assertEquals("Biedronka", fresh.active().single().title)
    }

    @Test
    fun `add shows the item at once, then with the server's id`() = runBlocking {
        repo.refresh()
        val job = repo.addItem(1, "eggs")
        val temp = list().items.last()
        assertTrue(temp.id < 0)
        assertEquals(3.0, temp.position, 0.0)
        job.join()
        assertEquals(listOf("milk", "bread", "eggs"), list().unchecked.map { it.text })
        assertTrue(list().items.all { it.id > 0 })
    }

    @Test
    fun `changes to an unsaved item wait for it and use its real id`() = runBlocking {
        repo.refresh()
        backend.gate = CompletableDeferred()
        repo.addItem(1, "eggs")
        val temp = list().items.last()
        val toggle = repo.toggle(1, temp, "me")
        assertTrue(list().items.last().checked) // shown at once
        backend.gate!!.complete(Unit)
        toggle.join()
        val saved = backend.lists.getValue(1).items.last()
        assertEquals(listOf("add eggs", "update ${saved.id}"), backend.writes)
        assertTrue(saved.checked)
        assertEquals(saved, list().items.single { it.text == "eggs" })
    }

    @Test
    fun `a failed change is rolled back with a message`() = runBlocking {
        repo.refresh()
        backend.fail = PicfortServerException.Other("boom")
        val message = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5_000) { repo.messages.first() } }
        repo.toggle(1, list().items.first(), "me").join()
        assertFalse(list().items.first().checked)
        assertEquals("Couldn't save the change: boom", message.await())
    }

    @Test
    fun `offline a failed change restores the list as it was`() = runBlocking {
        repo.refresh()
        backend.fail = PicfortServerException.Unreachable("server")
        backend.down = true
        repo.edit(1, list().items.first(), "oat milk").join()
        assertEquals("milk", list().items.first().text)
    }

    @Test
    fun `an item deleted by someone else disappears quietly`() = runBlocking {
        repo.refresh()
        val milk = list().items.first()
        backend.lists[1] = backend.lists.getValue(1).let { it.copy(items = it.items.drop(1), revision = it.revision + 1) }
        repo.toggle(1, milk, "me").join()
        assertEquals(listOf("bread"), list().items.map { it.text })
    }

    @Test
    fun `polls are skipped while changes are in flight and use the revision otherwise`() = runBlocking {
        repo.refresh()
        backend.gate = CompletableDeferred()
        val job = repo.edit(1, list().items.first(), "oat milk")
        // Someone else renames the list meanwhile; our poll must not undo our edit.
        backend.lists[1] = backend.lists.getValue(1).copy(title = "Lidl", revision = 9)
        assertNull(repo.refreshList(1))
        assertEquals("oat milk", list().items.first().text)
        assertEquals("Biedronka", list().title)
        backend.gate!!.complete(Unit)
        job.join()
        repo.refreshList(1)
        assertEquals("Lidl", list().title)
        assertEquals("oat milk", list().items.first().text)
    }

    @Test
    fun `delete, undo, uncheck all, delete checked`() = runBlocking {
        repo.refresh()
        val milk = list().items.first()
        repo.delete(1, milk).join()
        assertEquals(listOf("bread"), list().items.map { it.text })
        repo.restore(1, milk).join()
        assertEquals(listOf("milk", "bread"), list().unchecked.map { it.text })
        repo.toggle(1, list().unchecked.first(), "me").join()
        repo.uncheckAll(1).join()
        assertEquals(0, list().checkedCount)
        repo.toggle(1, list().unchecked.last(), "me").join()
        repo.deleteChecked(1).join()
        assertEquals(1, list().items.size)
    }

    @Test
    fun `archive moves a list out of the active ones`() = runBlocking {
        repo.refresh()
        repo.archive(1, true).join()
        assertTrue(repo.active().isEmpty())
        assertEquals(listOf(1L), repo.archived().map { it.id })
    }

    @Test
    fun `offline time is formatted`() {
        assertTrue(0L.asTime(java.time.ZoneOffset.UTC).isNotEmpty())
    }
}
