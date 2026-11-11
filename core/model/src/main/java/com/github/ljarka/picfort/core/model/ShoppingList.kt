package com.github.ljarka.picfort.core.model

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
