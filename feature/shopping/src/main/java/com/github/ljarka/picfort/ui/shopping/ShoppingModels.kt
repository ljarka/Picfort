package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.Receipt
import com.github.ljarka.picfort.core.model.ShoppingList
import com.github.ljarka.picfort.core.model.WishlistItem
import com.github.ljarka.picfort.core.model.WishlistPreview
import java.time.LocalDateTime

/** The Shopping tab: the family's lists as cards and the wishlist carousel. */
data class ShoppingListsUi(
    val configured: Boolean = true,
    val lists: List<ShoppingList> = emptyList(),
    val wishlist: List<WishlistItem> = emptyList(),
    val upcomingEvents: List<FamilyEvent> = emptyList(),
    val members: List<FamilyMember> = emptyList(),
    val myId: String = "",
    val wishlistSheetOpen: Boolean = false,
    val editingWishlistItem: WishlistItem? = null,
    val wishlistPreview: WishlistPreview? = null,
    val wishlistPreviewLoading: Boolean = false,
    val savingWishlist: Boolean = false,
    val isLoading: Boolean = false,
    /** Set when nothing could be loaded and there is no offline copy. */
    val loadError: String? = null,
    /** "14:32" when showing the offline copy; editing is off. */
    val offlineSince: String? = null,
    val message: String? = null,
)

/** One open list. */
data class ShoppingListUi(
    val list: ShoppingList? = null,
    val receipts: List<Receipt> = emptyList(),
    val activeLists: List<ShoppingList> = emptyList(),
    val members: List<FamilyMember> = emptyList(),
    val myId: String = "",
    val offlineSince: String? = null,
    val message: String? = null,
    /** Set when the list can't be shown at all (deleted, never loaded). */
    val loadError: String? = null,
    val uploadingReceipt: Boolean = false,
    val canRetryReceiptUpload: Boolean = false,
    val openReceipt: Receipt? = null,
    val savingReceipt: Boolean = false,
    val aiSupported: Boolean = false,
    val readingReceipt: Boolean = false,
    val receiptReadHint: String? = null,
    /** This phone's reminder for the list (T-040). */
    val reminderAt: LocalDateTime? = null,
) {
    val editable: Boolean get() = offlineSince == null
    val isMine: Boolean get() = list?.createdBy == myId

    val receiptsTotalMinor: Long get() = receipts.sumOf { it.totalMinor ?: 0L }
    val receiptsCurrency: String get() = receipts.firstOrNull()?.currency ?: "PLN"

    fun memberOf(id: String?): FamilyMember? = members.firstOrNull { it.id == id }
}
