package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.core.model.WishlistItem
import com.github.ljarka.picfort.core.model.WishlistPreview
import com.github.ljarka.picfort.core.model.WishlistProvider
import com.github.ljarka.picfort.core.network.PicfortServerException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Shared wishlist state for Shopping tab and Event detail (T-042).
 * Keeps unbought items first, newest first within each group.
 */
@Singleton
class WishlistRepository @Inject constructor(private val backend: WishlistBackend) : WishlistProvider {

    private val _items = MutableStateFlow<List<WishlistItem>>(emptyList())
    override val items: StateFlow<List<WishlistItem>> = _items.asStateFlow()

    override suspend fun refresh(): String? = try {
        _items.value = sortItems(backend.list())
        null
    } catch (e: PicfortServerException) {
        e.message
    }

    suspend fun preview(url: String): WishlistPreview = backend.preview(url)

    suspend fun create(
        url: String,
        title: String? = null,
        imageUrl: String? = null,
        priceMinor: Long? = null,
        eventId: Long? = null,
        targetMemberId: String? = null,
    ): WishlistItem {
        val created = backend.create(
            url = url,
            title = title,
            imageUrl = imageUrl,
            priceMinor = priceMinor,
            eventId = eventId,
            targetMemberId = targetMemberId,
        )
        _items.update { current -> sortItems(listOf(created) + current.filterNot { it.id == created.id }) }
        return created
    }

    suspend fun update(
        id: Long,
        title: String,
        imageUrl: String?,
        priceMinor: Long?,
        eventId: Long?,
        targetMemberId: String?,
    ): WishlistItem {
        val updated = backend.update(
            id = id,
            title = title,
            imageUrl = imageUrl,
            clearImage = imageUrl == null,
            priceMinor = priceMinor,
            clearPrice = priceMinor == null,
            eventId = eventId,
            clearEvent = eventId == null,
            targetMemberId = targetMemberId,
            clearTargetMember = targetMemberId == null,
        )
        replaceItem(updated)
        return updated
    }

    override suspend fun setBought(itemId: Long, bought: Boolean) {
        val updated = backend.update(id = itemId, bought = bought)
        replaceItem(updated)
    }

    override suspend fun setReservedBy(itemId: Long, reservedBy: String?) {
        val updated = backend.update(
            id = itemId,
            reservedBy = reservedBy,
            clearReservedBy = reservedBy == null,
        )
        replaceItem(updated)
    }

    suspend fun delete(id: Long) {
        backend.delete(id)
        _items.update { current -> current.filterNot { it.id == id } }
    }

    private fun replaceItem(updated: WishlistItem) {
        _items.update { current ->
            sortItems(current.map { if (it.id == updated.id) updated else it })
        }
    }

    private fun sortItems(items: List<WishlistItem>): List<WishlistItem> =
        items.sortedWith(compareBy<WishlistItem> { it.bought }.thenByDescending { it.id })
}
