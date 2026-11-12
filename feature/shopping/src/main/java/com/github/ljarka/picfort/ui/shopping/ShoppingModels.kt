package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.ShoppingItem
import com.github.ljarka.picfort.core.model.ShoppingList

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
