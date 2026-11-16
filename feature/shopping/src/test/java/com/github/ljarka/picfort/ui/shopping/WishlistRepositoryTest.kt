package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.core.model.WishlistItem
import com.github.ljarka.picfort.core.model.WishlistPreview
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WishlistRepositoryTest {

    private class FakeWishlistBackend : WishlistBackend {
        val items = mutableListOf(
            WishlistItem(id = 1, url = "https://a.example", title = "Bought Toy", bought = true),
            WishlistItem(id = 2, url = "https://b.example", title = "Bike", priceMinor = 129_900),
        )

        override suspend fun list(eventId: Long?, bought: Boolean?): List<WishlistItem> =
            items.filter { (eventId == null || it.eventId == eventId) && (bought == null || it.bought == bought) }

        override suspend fun preview(url: String): WishlistPreview =
            WishlistPreview(title = "Preview Title", imageUrl = "https://img.example/1.jpg", priceMinor = 4999)

        override suspend fun create(
            url: String,
            title: String?,
            imageUrl: String?,
            priceMinor: Long?,
            eventId: Long?,
            targetMemberId: String?,
        ): WishlistItem {
            val item = WishlistItem(
                id = 3,
                url = url,
                title = title ?: "Preview Title",
                imageUrl = imageUrl,
                priceMinor = priceMinor,
                eventId = eventId,
                targetMemberId = targetMemberId,
            )
            items.add(item)
            return item
        }

        override suspend fun update(
            id: Long,
            title: String?,
            priceMinor: Long?,
            clearPrice: Boolean,
            eventId: Long?,
            clearEvent: Boolean,
            targetMemberId: String?,
            clearTargetMember: Boolean,
            reservedBy: String?,
            clearReservedBy: Boolean,
            bought: Boolean?,
        ): WishlistItem {
            val current = items.first { it.id == id }
            val updated = current.copy(
                title = title ?: current.title,
                priceMinor = if (clearPrice) null else (priceMinor ?: current.priceMinor),
                eventId = if (clearEvent) null else (eventId ?: current.eventId),
                targetMemberId = if (clearTargetMember) null else (targetMemberId ?: current.targetMemberId),
                reservedBy = if (clearReservedBy) null else (reservedBy ?: current.reservedBy),
                bought = bought ?: current.bought,
            )
            items.replaceAll { if (it.id == id) updated else it }
            return updated
        }

        override suspend fun delete(id: Long) {
            items.removeAll { it.id == id }
        }
    }

    @Test
    fun `refresh sorts unbought items before bought items`() = runBlocking {
        val repo = WishlistRepository(FakeWishlistBackend())
        repo.refresh()
        assertEquals(listOf(2L, 1L), repo.items.value.map { it.id })
    }

    @Test
    fun `setBought moves item to bought section and delete removes it`() = runBlocking {
        val repo = WishlistRepository(FakeWishlistBackend())
        repo.refresh()
        val created = repo.create(url = "https://c.example", title = "Book", eventId = 5)
        assertEquals(listOf(3L, 2L, 1L), repo.items.value.map { it.id })

        repo.setReservedBy(created.id, "tata")
        assertEquals("tata", repo.items.value.first { it.id == 3L }.reservedBy)

        repo.setBought(created.id, true)
        assertTrue(repo.items.value.first { it.id == 3L }.bought)
        assertEquals(listOf(2L, 3L, 1L), repo.items.value.map { it.id })

        repo.delete(created.id)
        assertFalse(repo.items.value.any { it.id == 3L })
    }

    @Test
    fun `preview dto deserializes snake case fields`() {
        val json = Json { ignoreUnknownKeys = true }
        val dto = json.decodeFromString<WishlistPreviewDto>(
            """{"title":"LEGO","image_url":"https://img/a.jpg","price_minor":19900,"shop_name":"Allegro"}""",
        ).toDomain()
        assertEquals("LEGO", dto.title)
        assertEquals("https://img/a.jpg", dto.imageUrl)
        assertEquals(19900L, dto.priceMinor)
        assertEquals("Allegro", dto.shopName)
    }
}
