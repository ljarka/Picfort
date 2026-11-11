package com.github.ljarka.picfort.ui.wall

import com.github.ljarka.picfort.network.PicfortServerException
import com.github.ljarka.picfort.ui.frame.ConnectionTest
import com.github.ljarka.picfort.ui.frame.wallTestResult
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FamilyWallTest {

    private fun post(id: Int, author: String, minute: Int, photo: Boolean = false, pinned: Boolean = false) = WallPost(
        id = id, authorId = author, authorName = author, text = "t$id",
        photoUrl = if (photo) "http://frame/wall/photo/$id.jpg" else null,
        photoWidth = null, photoHeight = null, pinned = pinned,
        created = Instant.parse("2026-11-10T12:%02d:00Z".format(minute)),
    )

    @Test
    fun `parses a post and resolves the photo url`() {
        val dto = Json.decodeFromString<WallPostDto>(
            """{"id":2,"author_id":"u2","author":"Tata","text":null,"photo_url":"/wall/photo/abc.jpg",
               "photo_width":1920,"photo_height":1440,"pinned":false,"created":"2026-11-10T20:20:02Z"}"""
        )
        val p = dto.toPost("http://192.168.0.105:5000".toHttpUrl())
        assertEquals("http://192.168.0.105:5000/wall/photo/abc.jpg", p.photoUrl)
        assertEquals(4f / 3, p.aspectRatio!!, 0.001f)
        assertEquals(Instant.parse("2026-11-10T20:20:02Z"), p.created)
        assertNull(p.copy(photoWidth = null).aspectRatio)
    }

    @Test
    fun `text and photo posts are split, pinned first, then newest`() {
        val state = FamilyWallUiState(
            posts = listOf(
                post(1, "mama", 1), post(2, "tata", 2), post(3, "mama", 3, pinned = false),
                post(4, "tata", 4, photo = true), post(5, "mama", 5, photo = true), post(6, "tata", 0, pinned = true),
            ),
        )
        assertEquals(listOf(6, 3, 2, 1), state.messages.map { it.id })
        assertEquals(listOf(5, 4), state.photos.map { it.id })
    }

    @Test
    fun `selecting a member filters both`() {
        val state = FamilyWallUiState(
            posts = listOf(post(1, "mama", 1), post(2, "tata", 2), post(3, "mama", 3, photo = true)),
            selectedMemberId = "mama",
        )
        assertEquals(listOf(1), state.messages.map { it.id })
        assertEquals(listOf(3), state.photos.map { it.id })
    }

    @Test
    fun `unknown authors get a stand-in, only own saved posts are mine`() {
        val state = FamilyWallUiState(myId = "mama")
        val stranger = state.authorOf(post(1, "gone", 1))
        assertEquals("gone", stranger.name)
        assertEquals(UNKNOWN_MEMBER_COLOR, stranger.color)
        assertTrue(state.isMine(post(1, "mama", 1)))
        assertEquals(false, state.isMine(post(-1, "mama", 1))) // still sending
        assertEquals(false, state.isMine(post(2, "tata", 1)))
    }

    @Test
    fun `test accounts are recognised by name`() {
        assertTrue(isTestAccount("LukaszTest"))
        assertTrue(isTestAccount("test-user"))
        assertTrue(isTestAccount("TEST"))
        assertFalse(isTestAccount("Mama"))
        assertFalse(isTestAccount("Łukasz"))
    }

    @Test
    fun `posts by hidden authors are left out, also when filtering`() {
        val state = FamilyWallUiState(
            posts = listOf(post(1, "mama", 1), post(2, "tester", 2), post(3, "tester", 3, photo = true)),
            hiddenAuthorIds = setOf("tester"),
        )
        assertEquals(listOf(1), state.messages.map { it.id })
        assertTrue(state.photos.isEmpty())
        assertTrue(state.copy(selectedMemberId = "tester").messages.isEmpty())
    }

    @Test
    fun `initials and member order`() {
        assertEquals(listOf("AK", "M", "?"), listOf("Anna Maria Kowalska", " mama ", "").map(::initials))
        val members = listOf("a", "b", "c").map { FamilyMember(it, it, null, avatarColor(null)) }
        assertEquals(listOf("b", "a", "c"), members.meFirst("b").map { it.id })
        assertEquals(avatarColor("primary"), avatarColor("no-such-color"))
    }

    @Test
    fun `connection test messages`() {
        assertTrue(wallTestResult(null) is ConnectionTest.Ok)
        listOf(
            PicfortServerException.NotAccepted() to "didn't accept your Immich login",
            PicfortServerException.NoWall() to "no Family Wall yet",
            PicfortServerException.ImmichUnreachable() to "can't reach Immich",
            PicfortServerException.Unreachable("x") to "didn't answer",
        ).forEach { (error, expected) ->
            val result = wallTestResult(error)
            assertTrue("$error", result is ConnectionTest.Partial && expected in result.message)
        }
    }
}
