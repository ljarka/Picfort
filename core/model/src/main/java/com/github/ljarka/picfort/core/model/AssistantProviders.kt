package com.github.ljarka.picfort.core.model

data class AssistantSchoolEntry(
    val title: String,
    val kind: String,
    val startTime: String,
    val endTime: String,
    val location: String? = null,
)

data class AssistantSchoolResult(
    val found: Boolean,
    val childName: String? = null,
    val date: String,
    val finishesAt: String? = null,
    val entries: List<AssistantSchoolEntry> = emptyList(),
    val message: String,
)

/** Cross-feature contract for reading school timetables from the AppFunctions assistant service. */
interface SchoolAssistantProvider {
    suspend fun getChildTimetable(childName: String, dateIso: String? = null): AssistantSchoolResult
}

/** Cross-feature contract for reading and updating shopping lists from the AppFunctions assistant service. */
interface ShoppingAssistantProvider {
    suspend fun getLists(): List<ShoppingList>
    suspend fun getListItems(listName: String? = null): ShoppingList?
    suspend fun addItem(itemName: String, listName: String? = null): String
    suspend fun toggleItem(itemName: String, listName: String? = null, checked: Boolean = true): String
}

data class AssistantWallNote(
    val author: String,
    val text: String? = null,
    val hasPhoto: Boolean = false,
    val created: String,
)

/** Cross-feature contract for reading and posting Family Wall notes from the AppFunctions assistant service. */
interface WallAssistantProvider {
    suspend fun getRecentNotes(limit: Int = 5): List<AssistantWallNote>
    suspend fun postNote(text: String): String
}
