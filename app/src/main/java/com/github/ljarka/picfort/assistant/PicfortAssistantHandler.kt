package com.github.ljarka.picfort.assistant

import com.github.ljarka.picfort.core.model.AssistantSchoolEntry
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.model.FamilyEventsProvider
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.FamilyMemberProvider
import com.github.ljarka.picfort.core.model.SchoolAssistantProvider
import com.github.ljarka.picfort.core.model.ShoppingAssistantProvider
import com.github.ljarka.picfort.core.model.WallAssistantProvider
import com.github.ljarka.picfort.core.network.PicfortServerClient
import java.text.Normalizer
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class EventSummaryItem(
    val title: String,
    val date: String,
    val startTime: String? = null,
    val location: String? = null,
    val isBirthday: Boolean = false,
    val turnsAge: Int? = null,
    val forMembers: List<String> = emptyList(),
)

data class ShoppingListOverview(val title: String, val uncheckedCount: Int, val totalCount: Int)

data class ShoppingListItemsResult(
    val listTitle: String?,
    val uncheckedItems: List<String>,
    val checkedItems: List<String>,
    val message: String,
)

data class FamilyMemberSummary(val name: String, val isImmichUser: Boolean, val birthDate: String? = null)

/**
 * Pure domain/data coordinator for Picfort's AppFunctions (T-074). Kept separate from
 * [BasePicfortAppFunctionService] so all logic is unit-tested on JVM without Android 16 OS.
 */
@Singleton
class PicfortAssistantHandler(
    private val isServerConfigured: () -> Boolean,
    private val school: SchoolAssistantProvider,
    private val events: FamilyEventsProvider,
    private val family: FamilyMemberProvider,
    private val shopping: ShoppingAssistantProvider,
    private val wall: WallAssistantProvider,
) {
    @Inject
    constructor(
        server: PicfortServerClient,
        school: SchoolAssistantProvider,
        events: FamilyEventsProvider,
        family: FamilyMemberProvider,
        shopping: ShoppingAssistantProvider,
        wall: WallAssistantProvider,
    ) : this({ server.isConfigured }, school, events, family, shopping, wall)

    suspend fun getChildSchoolFinishTime(childName: String, dateIso: String?): String {
        if (!isServerConfigured()) return SERVER_NOT_CONFIGURED
        return school.getChildTimetable(childName, dateIso).message
    }

    suspend fun getChildTimetable(childName: String, dateIso: String?): Pair<String, List<AssistantSchoolEntry>> {
        if (!isServerConfigured()) return SERVER_NOT_CONFIGURED to emptyList()
        val result = school.getChildTimetable(childName, dateIso)
        return result.message to result.entries
    }

    suspend fun getUpcomingEvents(
        daysAhead: Int = 30,
        memberName: String? = null,
        birthdaysOnly: Boolean = false,
        today: LocalDate = LocalDate.now(),
    ): List<EventSummaryItem> {
        if (!isServerConfigured()) return emptyList()
        runCatching { family.refresh() }
        runCatching { events.refreshUpcoming() }
        val members = family.allMembers.first()
        val memberById = members.associateBy { it.id }
        val targetMember = memberName?.takeIf { it.isNotBlank() }?.let { findMember(members, it) }
        if (memberName != null && memberName.isNotBlank() && targetMember == null) {
            return emptyList()
        }
        val cutoff = today.plusDays(daysAhead.coerceIn(1, 366).toLong())
        return events.upcoming.value
            .asSequence()
            .filter { !it.effectiveEndDate.isBefore(today) && !it.startDate.isAfter(cutoff) }
            .filter { !birthdaysOnly || it.isBirthday }
            .filter { targetMember == null || targetMember.id in it.who }
            .map { it.toSummary(memberById) }
            .toList()
    }

    suspend fun getNextBirthday(memberName: String? = null, today: LocalDate = LocalDate.now()): EventSummaryItem? =
        getUpcomingEvents(
            daysAhead = 366,
            memberName = memberName,
            birthdaysOnly = true,
            today = today,
        ).firstOrNull()

    suspend fun getShoppingLists(): List<ShoppingListOverview> {
        if (!isServerConfigured()) return emptyList()
        return shopping.getLists().map { list ->
            ShoppingListOverview(
                title = list.title,
                uncheckedCount = list.items.count { !it.checked },
                totalCount = list.items.size,
            )
        }
    }

    suspend fun getShoppingListItems(listName: String?): ShoppingListItemsResult {
        if (!isServerConfigured()) {
            return ShoppingListItemsResult(null, emptyList(), emptyList(), SERVER_NOT_CONFIGURED)
        }
        val list = shopping.getListItems(listName)
            ?: return ShoppingListItemsResult(
                null,
                emptyList(),
                emptyList(),
                if (listName.isNullOrBlank()) "No active shopping lists found." else "No list matching \"$listName\".",
            )
        return ShoppingListItemsResult(
            listTitle = list.title,
            uncheckedItems = list.unchecked.map { it.text },
            checkedItems = list.checked.map { it.text },
            message = "${list.title}: ${list.unchecked.size} items to buy.",
        )
    }

    suspend fun addShoppingItem(itemName: String, listName: String?): String {
        if (!isServerConfigured()) return SERVER_NOT_CONFIGURED
        return shopping.addItem(itemName, listName)
    }

    suspend fun toggleShoppingItem(itemName: String, listName: String?, checked: Boolean): String {
        if (!isServerConfigured()) return SERVER_NOT_CONFIGURED
        return shopping.toggleItem(itemName, listName, checked)
    }

    suspend fun getRecentWallNotes(limit: Int = 5): List<String> {
        if (!isServerConfigured()) return emptyList()
        return wall.getRecentNotes(limit).map { note ->
            val body = note.text?.takeIf { it.isNotBlank() } ?: if (note.hasPhoto) "[photo]" else ""
            "${note.author} (${note.created}): $body"
        }
    }

    suspend fun postWallNote(text: String): String {
        if (!isServerConfigured()) return SERVER_NOT_CONFIGURED
        return wall.postNote(text)
    }

    suspend fun getFamilyMembers(): List<FamilyMemberSummary> {
        if (!isServerConfigured()) return emptyList()
        runCatching { family.refresh() }
        return family.allMembers.first().map { m ->
            FamilyMemberSummary(
                name = m.name,
                isImmichUser = m.isImmichUser,
                birthDate = m.birthDate?.toString(),
            )
        }
    }

    private fun FamilyEvent.toSummary(memberById: Map<String, FamilyMember>) = EventSummaryItem(
        title = title,
        date = startDate.toString(),
        startTime = startTime?.toString(),
        location = location,
        isBirthday = isBirthday,
        turnsAge = turns,
        forMembers = who.mapNotNull { memberById[it]?.name },
    )

    private fun findMember(members: List<FamilyMember>, query: String): FamilyMember? {
        val norm = normalize(query)
        return members.firstOrNull { normalize(it.name) == norm }
            ?: members.firstOrNull { normalize(it.name).contains(norm) }
            ?: members.firstOrNull { norm.contains(normalize(it.name)) }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace("ł", "l")

    companion object {
        const val SERVER_NOT_CONFIGURED = "Picfort server is not configured."
    }
}
