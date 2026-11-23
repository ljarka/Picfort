package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.core.model.ShoppingItem
import com.github.ljarka.picfort.core.model.ShoppingList
import com.github.ljarka.picfort.ui.shopping.reminders.customReminderTime
import com.github.ljarka.picfort.ui.shopping.reminders.finishedLists
import com.github.ljarka.picfort.ui.shopping.reminders.goneLists
import com.github.ljarka.picfort.ui.shopping.reminders.reminderLabel
import com.github.ljarka.picfort.ui.shopping.reminders.reminderPresets
import com.github.ljarka.picfort.ui.shopping.reminders.reminderText
import com.github.ljarka.picfort.ui.shopping.reminders.reminderTitle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingReminderTimesTest {
    // 2026-11-26 is a Thursday.
    private val thursday = LocalDate.of(2026, 11, 26)

    private fun presets(at: LocalDateTime) = reminderPresets(at).associate { it.label to it.at }

    @Test
    fun `all presets in the morning, Saturday is this week's`() {
        val p = presets(thursday.atTime(8, 0))
        assertEquals(thursday.atTime(17, 0), p["Today at 17:00"])
        assertEquals(thursday.atTime(19, 0), p["Tonight at 19:00"])
        assertEquals(thursday.plusDays(1).atTime(10, 0), p["Tomorrow at 10:00"])
        assertEquals(LocalDate.of(2026, 11, 28).atTime(9, 0), p["Saturday morning at 09:00"])
    }

    @Test
    fun `today's presets go once past`() {
        assertEquals(
            listOf("Tonight at 19:00", "Tomorrow at 10:00", "Saturday morning at 09:00"),
            reminderPresets(thursday.atTime(17, 0)).map { it.label },
        )
        assertEquals(
            listOf("Tomorrow at 10:00", "Saturday morning at 09:00"),
            reminderPresets(thursday.atTime(21, 30)).map { it.label },
        )
    }

    @Test
    fun `on Saturday before nine it's today, after it next week`() {
        val saturday = LocalDate.of(2026, 11, 28)
        assertEquals(saturday.atTime(9, 0), presets(saturday.atTime(7, 0))["Saturday morning at 09:00"])
        assertEquals(saturday.plusWeeks(1).atTime(9, 0), presets(saturday.atTime(9, 0))["Saturday morning at 09:00"])
    }

    @Test
    fun labels() {
        assertEquals("Today, 17:00", reminderLabel(thursday.atTime(17, 0), thursday))
        assertEquals("Tomorrow, 10:00", reminderLabel(thursday.plusDays(1).atTime(10, 0), thursday))
        assertEquals("Saturday, 09:00", reminderLabel(thursday.plusDays(2).atTime(9, 0), thursday))
        assertEquals("3 Dec, 17:00", reminderLabel(thursday.plusDays(7).atTime(17, 0), thursday))
    }

    @Test
    fun `a custom time must be ahead`() {
        val now = thursday.atTime(12, 0)
        assertNull(customReminderTime(thursday, LocalTime.of(11, 0), now))
        assertEquals(thursday.atTime(18, 0), customReminderTime(thursday, LocalTime.of(18, 0), now))
    }

    private fun list(id: Long, vararg items: Pair<String, Boolean>, archived: Boolean = false) = ShoppingList(
        id = id,
        title = "Biedronka",
        archived = archived,
        items = items.mapIndexed { i, (text, checked) ->
            ShoppingItem(i + 1L, text, checked = checked, position = i.toDouble())
        },
    )

    @Test
    fun `text names the items left in order, five at most`() {
        assertEquals("Time for groceries: Biedronka", reminderTitle("Biedronka"))
        assertEquals("1 item left to buy: Milk", reminderText(list(1, "Milk" to false, "Eggs" to true)))
        val many = list(1, *listOf("A", "B", "C", "D", "E", "F").map { it to false }.toTypedArray())
        assertEquals("6 items left to buy: A, B, C, D, E…", reminderText(many))
    }

    @Test
    fun `nothing left to buy means no notification, an unknown list a plain prompt`() {
        assertNull(reminderText(list(1, "Milk" to true)))
        assertNull(reminderText(list(1)))
        assertEquals("Open the list to see what's left to buy.", reminderText(null))
    }

    @Test
    fun `deleted or archived lists lose their reminder, lists not loaded yet keep it`() {
        val before = mapOf(1L to list(1, "Milk" to false), 2L to list(2, "Eggs" to false))
        val after = mapOf(2L to list(2, "Eggs" to false, archived = true))
        assertEquals(setOf(1L, 2L), goneLists(setOf(1L, 2L, 3L), before, after))
        assertEquals(emptySet<Long>(), goneLists(setOf(3L), emptyMap(), emptyMap()))
    }

    @Test
    fun `a list whose last item was checked off is finished`() {
        val before = mapOf(1L to list(1, "Milk" to false), 2L to list(2, "Eggs" to true))
        val after = mapOf(1L to list(1, "Milk" to true), 2L to list(2, "Eggs" to true))
        assertEquals(setOf(1L), finishedLists(before, after))
    }
}
