package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.core.model.ShoppingAssistantProvider
import com.github.ljarka.picfort.core.model.ShoppingList
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingAssistantImpl @Inject constructor(
    private val repository: ShoppingRepository,
    private val backend: ShoppingBackend,
) : ShoppingAssistantProvider {
    override suspend fun getLists(): List<ShoppingList> {
        repository.refresh(archived = false)
        return repository.active()
    }

    override suspend fun getListItems(listName: String?): ShoppingList? {
        val lists = getLists()
        if (lists.isEmpty()) return null
        return if (listName.isNullOrBlank()) {
            lists.first()
        } else {
            findMatchingList(lists, listName)
        }
    }

    override suspend fun addItem(itemName: String, listName: String?): String {
        val cleanItem = itemName.trim()
        if (cleanItem.isEmpty()) return "Item name cannot be empty."
        val lists = getLists()
        val target = if (listName.isNullOrBlank()) lists.firstOrNull() else findMatchingList(lists, listName)
        if (target == null) {
            return if (listName.isNullOrBlank()) {
                "No active shopping lists found."
            } else {
                "No shopping list matching \"$listName\"."
            }
        }
        return runCatching {
            backend.addItem(target.id, cleanItem, position = null)
            repository.refreshList(target.id)
            "Added \"$cleanItem\" to ${target.title}."
        }.getOrElse { "Couldn't add \"$cleanItem\": ${it.message}" }
    }

    override suspend fun toggleItem(itemName: String, listName: String?, checked: Boolean): String {
        val cleanItem = itemName.trim()
        if (cleanItem.isEmpty()) return "Item name cannot be empty."
        val lists = getLists()
        val target = (if (listName.isNullOrBlank()) lists.firstOrNull() else findMatchingList(lists, listName))
            ?: return if (listName.isNullOrBlank()) {
                "No active shopping lists found."
            } else {
                "No shopping list matching \"$listName\"."
            }
        val normItem = normalize(cleanItem)
        val item = target.items.firstOrNull { normalize(it.text) == normItem }
            ?: target.items.firstOrNull { normalize(it.text).contains(normItem) }
            ?: return "No item matching \"$cleanItem\" on ${target.title}."
        return runCatching {
            backend.updateItem(target.id, item.id, checked = checked)
            repository.refreshList(target.id)
            val state = if (checked) "checked" else "unchecked"
            "Marked \"${item.text}\" as $state on ${target.title}."
        }.getOrElse { "Couldn't update \"${item.text}\": ${it.message}" }
    }

    private fun findMatchingList(lists: List<ShoppingList>, query: String): ShoppingList? {
        val norm = normalize(query)
        return lists.firstOrNull { normalize(it.title) == norm }
            ?: lists.firstOrNull { normalize(it.title).contains(norm) }
            ?: lists.firstOrNull { norm.contains(normalize(it.title)) }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace("ł", "l")
}
