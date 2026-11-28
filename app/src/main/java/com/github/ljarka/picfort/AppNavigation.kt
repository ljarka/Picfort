package com.github.ljarka.picfort

import com.github.ljarka.picfort.ui.shell.Tab
import kotlinx.serialization.Serializable

sealed interface Screen {
    @Serializable
    object Init : Screen

    /** The Photos tab; [date] (ISO) opens it scrolled to that day (T-056). */
    @Serializable
    data class Timeline(val date: String? = null) : Screen

    @Serializable
    object Settings : Screen

    /** Settings -> Timeline: display filters and Immich tag management (T-092). */
    @Serializable
    object TimelineSettings : Screen

    @Serializable
    object ExperimentalFeatures : Screen

    @Serializable
    data class FamilySettings(val editUserId: String? = null) : Screen

    @Serializable
    object Frame : Screen

    @Serializable
    object Albums : Screen

    @Serializable
    object Search : Screen

    /** Immich memories, opened from the frame card on the Family Wall (T-096). */
    @Serializable
    object Memories : Screen

    /** AI-suggested photo cleanup screen (ADR 0019, T-089). */
    @Serializable
    object Cleanup : Screen

    @Serializable
    object FamilyWall : Screen

    /** One-time, optional step after login when no Picfort server is set. */
    @Serializable
    object PicfortServerSetup : Screen

    @Serializable
    object Shopping : Screen

    /** `archived` is read by ShoppingListsViewModel. */
    @Serializable
    data class ArchivedShopping(val archived: Boolean = true) : Screen

    /** `id` and `focusAdd` are read by ShoppingListViewModel / the screen. */
    @Serializable
    data class ShoppingList(val id: Long, val focusAdd: Boolean = false) : Screen

    @Serializable
    object Spending : Screen

    @Serializable
    object Events : Screen

    /** Reachable from any tab (T-023). `eventId` is read by EventDetailViewModel. */
    @Serializable
    data class EventDetail(val eventId: Long) : Screen

    /** `eventId` 0 = a new event; read by EventFormViewModel. */
    @Serializable
    data class EventForm(val eventId: Long = 0L) : Screen

    @Serializable
    object School : Screen

    /** School journal (ADR 0017, removable): opened from Wall, School or a notification ([tab] = `"grades"`). */
    @Serializable
    data class SchoolJournal(val tab: String? = null) : Screen

    /** One school journal message (ADR 0017). */
    @Serializable
    data class SchoolJournalMessage(val id: String) : Screen
}

/** One nested graph per bottom-bar tab (T-017), so each tab keeps its own back stack. */
sealed interface TabGraph {
    @Serializable
    object Wall : TabGraph

    @Serializable
    object Shopping : TabGraph

    @Serializable
    object Events : TabGraph

    @Serializable
    object School : TabGraph

    @Serializable
    object Photos : TabGraph
}

internal val Tab.graph: TabGraph
    get() = when (this) {
        Tab.WALL -> TabGraph.Wall
        Tab.SHOPPING -> TabGraph.Shopping
        Tab.EVENTS -> TabGraph.Events
        Tab.SCHOOL -> TabGraph.School
        Tab.PHOTOS -> TabGraph.Photos
    }

/** The tab's first screen: the bar is shown only on these. */
internal val Tab.root: Screen
    get() = when (this) {
        Tab.WALL -> Screen.FamilyWall
        Tab.SHOPPING -> Screen.Shopping
        Tab.EVENTS -> Screen.Events
        Tab.SCHOOL -> Screen.School
        Tab.PHOTOS -> Screen.Timeline()
    }
