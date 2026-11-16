package com.github.ljarka.picfort.core.model

/** A receipt attached to a shopping list (or standalone), stored on the Picfort server. */
data class Receipt(
    val id: Long,
    val listId: Long? = null,
    val photoUrl: String? = null,
    val shop: String? = null,
    val date: String,
    val totalMinor: Long? = null,
    val currency: String = "PLN",
    val payerId: String = "",
    val status: String = "manual",
    val readError: String? = null,
    val createdBy: String = "",
    val created: String = "",
    val updated: String = "",
) {
    val isConfirmed: Boolean get() = status == "confirmed"
}
