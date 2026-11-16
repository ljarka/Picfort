package com.github.ljarka.picfort.core.model

import kotlinx.coroutines.flow.StateFlow

/** A product link saved on the family wishlist (T-041 / T-042). */
data class WishlistItem(
    val id: Long,
    val url: String,
    val title: String,
    val imageUrl: String? = null,
    val priceMinor: Long? = null,
    val currency: String = "PLN",
    val eventId: Long? = null,
    val targetMemberId: String? = null,
    val reservedBy: String? = null,
    val bought: Boolean = false,
    val createdBy: String = "",
    val created: String = "",
    val updated: String = "",
)

/** OpenGraph metadata extracted by the server for a product URL. */
data class WishlistPreview(
    val title: String? = null,
    val imageUrl: String? = null,
    val priceMinor: Long? = null,
    val shopName: String? = null,
)

/**
 * Cross-feature contract for reading and updating wishlist items linked to family events.
 * Implemented by `:feature:shopping` and consumed by `:feature:events`.
 */
interface WishlistProvider {
    val items: StateFlow<List<WishlistItem>>
    suspend fun refresh(): Any?
    suspend fun setBought(itemId: Long, bought: Boolean)
    suspend fun setReservedBy(itemId: Long, reservedBy: String?)
}
