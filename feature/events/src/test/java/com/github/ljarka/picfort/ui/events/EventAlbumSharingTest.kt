package com.github.ljarka.picfort.ui.events

import androidx.compose.ui.graphics.Color
import com.github.ljarka.picfort.core.model.EventAlbum
import com.github.ljarka.picfort.core.model.FamilyMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventAlbumSharingTest {

    private val members = listOf(
        FamilyMember("mama", "Mama", null, Color.Red, isImmichUser = true),
        FamilyMember("tata", "Tata", null, Color.Blue, isImmichUser = true),
        FamilyMember("ola", "Ola", null, Color.Green, isImmichUser = false),
        FamilyMember("tester", "Test User", null, Color.Gray, isImmichUser = true),
    )

    @Test
    fun `familyUserIds excludes self, manual members, and test accounts`() {
        assertEquals(listOf("tata"), familyUserIds(members, myId = "mama"))
    }

    @Test
    fun `missingFamilyMembers and isSharedWithFamily detect unshared users`() {
        val unshared = EventAlbum(
            id = "a1",
            name = "Holiday",
            ownerId = "mama",
            memberIds = setOf("mama"),
        )
        assertEquals(listOf("tata"), missingFamilyMembers(unshared, listOf("tata")))
        assertFalse(isSharedWithFamily(unshared, listOf("tata")))

        val shared = unshared.copy(memberIds = setOf("mama", "tata"))
        assertEquals(emptyList<String>(), missingFamilyMembers(shared, listOf("tata")))
        assertTrue(isSharedWithFamily(shared, listOf("tata")))
    }
}
