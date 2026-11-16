package com.github.ljarka.picfort.assistant

import androidx.compose.ui.graphics.Color
import com.github.ljarka.picfort.core.model.AssistantSchoolEntry
import com.github.ljarka.picfort.core.model.AssistantSchoolResult
import com.github.ljarka.picfort.core.model.AssistantWallNote
import com.github.ljarka.picfort.core.model.BirthDate
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.model.FamilyEventsProvider
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.FamilyMemberProvider
import com.github.ljarka.picfort.core.model.SchoolAssistantProvider
import com.github.ljarka.picfort.core.model.ShoppingAssistantProvider
import com.github.ljarka.picfort.core.model.ShoppingItem
import com.github.ljarka.picfort.core.model.ShoppingList
import com.github.ljarka.picfort.core.model.WallAssistantProvider
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PicfortAssistantHandlerTest {
    private fun makeHandler(configured: Boolean = true): PicfortAssistantHandler {
        val membersList = listOf(
            FamilyMember("mama", "Mama", null, Color.Red, true, BirthDate(1985, 5, 10)),
            FamilyMember("feliks", "Feliks", null, Color.Blue, false, BirthDate(2016, 11, 20)),
            FamilyMember("lukasz", "Łukasz", null, Color.Green, true, null),
        )
        val family = object : FamilyMemberProvider {
            override val myId: String = "mama"
            override val members: Flow<List<FamilyMember>> = flowOf(membersList)
            override val allMembers: Flow<List<FamilyMember>> = flowOf(membersList)
            override suspend fun refresh(): Any? = null
        }

        val eventsList = listOf(
            FamilyEvent(
                id = 1,
                title = "Feliks's 10th birthday",
                startDate = LocalDate.of(2026, 11, 20),
                birthdayOf = "feliks",
                turns = 10,
            ),
            FamilyEvent(
                id = 2,
                title = "Dentist",
                startDate = LocalDate.of(2026, 11, 18),
                startTime = LocalTime.of(15, 30),
                memberIds = listOf("lukasz"),
            ),
            FamilyEvent(
                id = 3,
                title = "Far future trip",
                startDate = LocalDate.of(2027, 3, 1),
            ),
        )
        val events = object : FamilyEventsProvider {
            override val upcoming: StateFlow<List<FamilyEvent>> = MutableStateFlow(eventsList)
            override suspend fun refreshUpcoming(): Any? = null
        }

        val school = object : SchoolAssistantProvider {
            override suspend fun getChildTimetable(childName: String, dateIso: String?): AssistantSchoolResult =
                AssistantSchoolResult(
                    found = true,
                    childName = "Feliks",
                    date = dateIso ?: "2026-11-16",
                    finishesAt = "13:40",
                    entries = listOf(AssistantSchoolEntry("Math", "lesson", "08:00", "08:45")),
                    message = "Feliks finishes at 13:40 on 2026-11-16.",
                )
        }

        val shopping = object : ShoppingAssistantProvider {
            val list = ShoppingList(
                id = 1,
                title = "Biedronka",
                items = listOf(
                    ShoppingItem(1, "Milk", checked = false),
                    ShoppingItem(2, "Bread", checked = true),
                ),
            )

            override suspend fun getLists(): List<ShoppingList> = listOf(list)
            override suspend fun getListItems(listName: String?): ShoppingList = list
            override suspend fun addItem(itemName: String, listName: String?): String =
                "Added \"$itemName\" to Biedronka."
            override suspend fun toggleItem(itemName: String, listName: String?, checked: Boolean): String =
                "Marked \"$itemName\" as checked on Biedronka."
        }

        val wall = object : WallAssistantProvider {
            override suspend fun getRecentNotes(limit: Int): List<AssistantWallNote> =
                listOf(AssistantWallNote("Mama", "Dinner at 18:00", false, "2026-11-16T12:00:00Z"))

            override suspend fun postNote(text: String): String = "Posted to Family Wall: \"$text\""
        }

        return PicfortAssistantHandler({ configured }, school, events, family, shopping, wall)
    }

    @Test
    fun unconfiguredServerReturnsClearMessage() = runBlocking {
        val handler = makeHandler(configured = false)
        assertEquals(PicfortAssistantHandler.SERVER_NOT_CONFIGURED, handler.getChildSchoolFinishTime("Feliks", null))
        assertTrue(handler.getUpcomingEvents().isEmpty())
        assertTrue(handler.getShoppingLists().isEmpty())
    }

    @Test
    fun schoolFinishTimeAndTimetableDelegate() = runBlocking {
        val handler = makeHandler()
        assertEquals("Feliks finishes at 13:40 on 2026-11-16.", handler.getChildSchoolFinishTime("Feliks", null))
        val (summary, entries) = handler.getChildTimetable("Feliks", "2026-11-16")
        assertTrue(summary.contains("13:40"))
        assertEquals(1, entries.size)
    }

    @Test
    fun upcomingEventsAndBirthdaysFilterByWindowAndMember() = runBlocking {
        val handler = makeHandler()
        val today = LocalDate.of(2026, 11, 16)

        val all30 = handler.getUpcomingEvents(daysAhead = 30, today = today)
        assertEquals(2, all30.size)

        val lukaszOnly = handler.getUpcomingEvents(daysAhead = 30, memberName = "lukasz", today = today)
        assertEquals(1, lukaszOnly.size)
        assertEquals("Dentist", lukaszOnly[0].title)
        assertEquals(listOf("Łukasz"), lukaszOnly[0].forMembers)

        val birthday = handler.getNextBirthday("Feliks", today = today)
        assertNotNull(birthday)
        assertEquals(10, birthday?.turnsAge)
        assertTrue(birthday?.isBirthday == true)
    }

    @Test
    fun shoppingAndWallAndMembersWork() = runBlocking {
        val handler = makeHandler()
        val lists = handler.getShoppingLists()
        assertEquals(1, lists.size)
        assertEquals(1, lists[0].uncheckedCount)

        val items = handler.getShoppingListItems("Biedronka")
        assertEquals(listOf("Milk"), items.uncheckedItems)
        assertEquals("Added \"Butter\" to Biedronka.", handler.addShoppingItem("Butter", "Biedronka"))
        assertEquals("Marked \"Milk\" as checked on Biedronka.", handler.toggleShoppingItem("Milk", "Biedronka", true))

        val notes = handler.getRecentWallNotes(5)
        assertEquals(1, notes.size)
        assertTrue(notes[0].contains("Dinner at 18:00"))
        assertEquals("Posted to Family Wall: \"Hi\"", handler.postWallNote("Hi"))

        val members = handler.getFamilyMembers()
        assertEquals(3, members.size)
    }
}
