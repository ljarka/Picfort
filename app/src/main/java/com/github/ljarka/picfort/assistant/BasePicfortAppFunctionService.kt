package com.github.ljarka.picfort.assistant

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@AppFunctionSerializable(isDescribedByKDoc = false)
data class SchoolEntryItem(
    val title: String,
    val kind: String,
    val startTime: String,
    val endTime: String,
    val location: String? = null,
)

@AppFunctionSerializable(isDescribedByKDoc = false)
data class SchoolTimetableResponse(val summary: String, val entries: List<SchoolEntryItem>)

@AppFunctionSerializable(isDescribedByKDoc = false)
data class EventItemResponse(
    val title: String,
    val date: String,
    val startTime: String? = null,
    val location: String? = null,
    val isBirthday: Boolean = false,
    val turnsAge: Int? = null,
    val forMembers: List<String> = emptyList(),
)

@AppFunctionSerializable(isDescribedByKDoc = false)
data class ShoppingListSummaryResponse(val title: String, val uncheckedCount: Int, val totalCount: Int)

@AppFunctionSerializable(isDescribedByKDoc = false)
data class ShoppingListItemsResponse(
    val listTitle: String?,
    val uncheckedItems: List<String>,
    val checkedItems: List<String>,
    val message: String,
)

@AppFunctionSerializable(isDescribedByKDoc = false)
data class FamilyMemberResponse(val name: String, val isImmichUser: Boolean, val birthDate: String? = null)

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AssistantEntryPoint {
    fun handler(): PicfortAssistantHandler
}

/**
 * Exposes Picfort's family hub capabilities (school timetables, events & birthdays, shopping
 * lists, Family Wall, and family members) to the on-device Gemini Assistant on Android 16+.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "PicfortAppFunctionService",
    appFunctionXmlFileName = "picfort_app_function_service",
)
abstract class BasePicfortAppFunctionService : AppFunctionService() {
    private val handler: PicfortAssistantHandler by lazy {
        EntryPointAccessors.fromApplication(applicationContext, AssistantEntryPoint::class.java).handler()
    }

    /**
     * Checks when a child finishes school or extracurricular activities on the given date
     * (YYYY-MM-DD, defaults to today if omitted).
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getChildSchoolFinishTime(childName: String, date: String? = null): String =
        handler.getChildSchoolFinishTime(childName, date)

    /**
     * Returns the child's full school timetable (lessons and activities) for the given date
     * (YYYY-MM-DD, defaults to today if omitted).
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getChildTimetable(childName: String, date: String? = null): SchoolTimetableResponse {
        val (summary, entries) = handler.getChildTimetable(childName, date)
        return SchoolTimetableResponse(
            summary = summary,
            entries = entries.map { SchoolEntryItem(it.title, it.kind, it.startTime, it.endTime, it.location) },
        )
    }

    /**
     * Lists upcoming family events and birthdays within the next [daysAhead] days, optionally
     * filtered by [memberName] or [birthdaysOnly].
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getUpcomingEvents(
        daysAhead: Int = 30,
        memberName: String? = null,
        birthdaysOnly: Boolean = false,
    ): List<EventItemResponse> = handler.getUpcomingEvents(daysAhead, memberName, birthdaysOnly).map {
        EventItemResponse(it.title, it.date, it.startTime, it.location, it.isBirthday, it.turnsAge, it.forMembers)
    }

    /**
     * Finds the next upcoming birthday (for [memberName] if provided, or anyone in the family),
     * including the date and turning age.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getNextBirthday(memberName: String? = null): EventItemResponse? =
        handler.getNextBirthday(memberName)?.let {
            EventItemResponse(it.title, it.date, it.startTime, it.location, it.isBirthday, it.turnsAge, it.forMembers)
        }

    /**
     * Lists active family shopping lists with the count of unchecked items to buy.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getShoppingLists(): List<ShoppingListSummaryResponse> =
        handler.getShoppingLists().map { ShoppingListSummaryResponse(it.title, it.uncheckedCount, it.totalCount) }

    /**
     * Returns the items on a shopping list matching [listName] (or the newest active list if omitted).
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getShoppingListItems(listName: String? = null): ShoppingListItemsResponse {
        val res = handler.getShoppingListItems(listName)
        return ShoppingListItemsResponse(res.listTitle, res.uncheckedItems, res.checkedItems, res.message)
    }

    /**
     * Adds [itemName] to the shopping list matching [listName] (or the newest active list if omitted).
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addShoppingItem(itemName: String, listName: String? = null): String =
        handler.addShoppingItem(itemName, listName)

    /**
     * Marks [itemName] as checked or unchecked on the shopping list matching [listName].
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun toggleShoppingItem(itemName: String, listName: String? = null, checked: Boolean = true): String =
        handler.toggleShoppingItem(itemName, listName, checked)

    /**
     * Returns the latest posts and notes from the Family Wall.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getRecentWallNotes(limit: Int = 5): List<String> = handler.getRecentWallNotes(limit)

    /**
     * Posts a short text note to the Family Wall.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun postWallNote(text: String): String = handler.postWallNote(text)

    /**
     * Lists visible family members and their birth dates if set.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getFamilyMembers(): List<FamilyMemberResponse> =
        handler.getFamilyMembers().map { FamilyMemberResponse(it.name, it.isImmichUser, it.birthDate) }
}
