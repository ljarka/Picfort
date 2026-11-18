package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.core.model.WishlistItem
import com.github.ljarka.picfort.core.model.WishlistPreview
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
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
            imageUrl: String?,
            clearImage: Boolean,
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
                imageUrl = if (clearImage) null else (imageUrl ?: current.imageUrl),
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

        val uploads = mutableListOf<Pair<Long, Int>>()

        override suspend fun uploadPhoto(id: Long, jpeg: ByteArray): WishlistItem {
            uploads += id to jpeg.size
            val updated = items.first { it.id == id }.copy(imageUrl = "http://server.example/wishlist/photo/$id.jpg")
            items.replaceAll { if (it.id == id) updated else it }
            return updated
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
    fun `uploadPhoto replaces the image and keepImage leaves it alone on update`() = runBlocking {
        val backend = FakeWishlistBackend()
        val repo = WishlistRepository(backend)
        repo.refresh()
        repo.uploadPhoto(2, ByteArray(10))
        assertEquals(listOf(2L to 10), backend.uploads)
        val photo = "http://server.example/wishlist/photo/2.jpg"
        assertEquals(photo, repo.items.value.first { it.id == 2L }.imageUrl)

        repo.update(
            2,
            "Bike",
            imageUrl = photo,
            priceMinor = null,
            eventId = null,
            targetMemberId = null,
            keepImage = true,
        )
        assertEquals(photo, repo.items.value.first { it.id == 2L }.imageUrl)

        repo.update(2, "Bike", imageUrl = null, priceMinor = null, eventId = null, targetMemberId = null)
        assertEquals(null, repo.items.value.first { it.id == 2L }.imageUrl)
    }

    @Test
    fun `server photo paths resolve against the server address`() {
        val dto = WishlistItemDto(id = 1, url = "https://a.example", title = "A", imageUrl = "/wishlist/photo/abc.jpg")
        val base = "http://picfort.example:5050/".toHttpUrl()
        assertEquals("http://picfort.example:5050/wishlist/photo/abc.jpg", dto.toDomain(base).imageUrl)
        val shop = dto.copy(imageUrl = "https://shop.example/x.png")
        assertEquals("https://shop.example/x.png", shop.toDomain(base).imageUrl)
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

    @Test
    fun `parseWebViewPreviewJson extracts title, imageUrl and priceMinor`() {
        val raw = "\"{\\\"title\\\":\\\"Multicooker Tefal\\\"," +
            "\\\"imageUrl\\\":\\\"https://a.allegroimg.com/s512/123.jpg\\\"," +
            "\\\"price\\\":\\\"499.99\\\"}\""
        val parsed = parseWebViewPreviewJson(raw)
        assertEquals("Multicooker Tefal", parsed?.title)
        assertEquals("https://a.allegroimg.com/s512/123.jpg", parsed?.imageUrl)
        assertEquals(49999L, parsed?.priceMinor)
    }
}
