package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.ui.wall.FamilyMember

/** An item as the app keeps it. Items not yet saved have a negative id. */
data class ShoppingItem(
    val id: Long,
    val text: String,
    val checked: Boolean = false,
    val position: Double = 0.0,
    val checkedBy: String? = null,
    val checkedAt: String? = null,
    val createdBy: String = "",
)

data class ShoppingList(
    val id: Long,
    val title: String,
    val archived: Boolean = false,
    val revision: Long = 1,
    val createdBy: String = "",
    val created: String = "",
    val items: List<ShoppingItem> = emptyList(),
) {
    /** By position, as the server orders them. */
    val unchecked: List<ShoppingItem> get() = items.filter { !it.checked }.sortedWith(compareBy({ it.position }, { it.id }))

    /** The most recently checked first. */
    val checked: List<ShoppingItem>
        get() = items.filter { it.checked }.sortedWith(compareByDescending<ShoppingItem> { it.checkedAt }.thenByDescending { it.id })

    val checkedCount: Int get() = items.count { it.checked }
}

/** The Shopping tab: the family's lists as cards. */
data class ShoppingListsUi(
    val configured: Boolean = true,
    val lists: List<ShoppingList> = emptyList(),
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
    val members: List<FamilyMember> = emptyList(),
    val myId: String = "",
    val offlineSince: String? = null,
    val message: String? = null,
    /** Set when the list can't be shown at all (deleted, never loaded). */
    val loadError: String? = null,
) {
    val editable: Boolean get() = offlineSince == null
    val isMine: Boolean get() = list?.createdBy == myId

    fun memberOf(id: String?): FamilyMember? = members.firstOrNull { it.id == id }
}
