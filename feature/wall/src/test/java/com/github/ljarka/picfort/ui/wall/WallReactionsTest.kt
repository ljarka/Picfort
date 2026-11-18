package com.github.ljarka.picfort.ui.wall

import java.time.Instant
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallReactionsTest {
    private val heart = WALL_REACTIONS[0]
    private val thumb = WALL_REACTIONS[1]

    private val post = WallPost(
        id = 1, authorId = "mama", authorName = "Mama", text = "Obiad", photoUrl = null,
        photoWidth = null, photoHeight = null, pinned = false, created = Instant.EPOCH,
    )

    @Test
    fun `parses reactions and drops empty ones`() {
        val dto = Json.decodeFromString<WallPostDto>(
            """{"id":1,"author_id":"mama","author":"Mama","created":"2026-11-10T12:00:00Z","reactions":[
               {"emoji":"$heart","count":2,"user_ids":["tata","ola"],"names":["Tata","Ola"]},
               {"emoji":"$thumb","count":0,"user_ids":[],"names":[]}]}""",
        )
        val p = dto.toPost("http://frame:5000".toHttpUrl())
        assertEquals(listOf(WallReaction(heart, listOf("tata", "ola"), listOf("Tata", "Ola"))), p.reactions)
        assertTrue(p.reactions[0].isMine("ola"))
    }

    @Test
    fun `posts from an older server have no reactions`() {
        val dto = Json.decodeFromString<WallPostDto>(
            """{"id":1,"author_id":"mama","author":"Mama","created":"2026-11-10T12:00:00Z"}""",
        )
        assertEquals(emptyList<WallReaction>(), dto.toPost("http://frame:5000".toHttpUrl()).reactions)
    }

    @Test
    fun `toggling adds mine in the fixed order and removes it again`() {
        val withThumb = post.withReactionToggled(thumb, "tata", "Tata")
        val withBoth = withThumb.withReactionToggled(heart, "ola", "Ola")
        assertEquals(listOf(heart, thumb), withBoth.reactions.map { it.emoji })
        val twoHearts = withBoth.withReactionToggled(heart, "tata", "Tata")
        assertEquals(listOf("Ola", "Tata"), twoHearts.reactions[0].names)
        val back = twoHearts.withReactionToggled(heart, "ola", "Ola").withReactionToggled(heart, "tata", "Tata")
        assertEquals(listOf(thumb), back.reactions.map { it.emoji })
    }

    @Test
    fun `summary and who reacted`() {
        val p = post.withReactionToggled(heart, "tata", "Tata").withReactionToggled(heart, "ola", "Ola")
            .withReactionToggled(thumb, "babcia", "Babcia")
        assertEquals("$heart 2  $thumb 1", reactionSummaryText(p.reactions))
        assertEquals("$heart Tata, Ola · $thumb Babcia", reactedText(p.reactions))
        assertEquals(null, reactedText(emptyList()))
    }

    @Test
    fun `first reaction notifications name who and what`() {
        val items = listOf(
            FirstReactionDto(
                postId = 1,
                text = "Pierwsze pomidory",
                at = "2026-11-10T12:03:00Z",
                by = "Tata",
                emoji = heart,
            ),
            FirstReactionDto(
                postId = 2,
                text = null,
                hasPhoto = true,
                at = "2026-11-10T12:04:00Z",
                by = "Ola",
                emoji = thumb,
            ),
            FirstReactionDto(
                postId = 2,
                text = null,
                hasPhoto = true,
                at = "2026-11-10T12:05:00Z",
                by = "Ola",
                emoji = thumb,
            ),
        )
        assertEquals(
            listOf(
                ReactionNotification(1, "Tata reacted $heart", "Pierwsze pomidory"),
                ReactionNotification(2, "Ola reacted $thumb", "Your photo"),
            ),
            planReactionNotifications(items),
        )
    }

    @Test
    fun `cursor times have whole seconds like the server's`() {
        assertEquals("2026-11-10T12:03:04Z", isoSeconds(Instant.parse("2026-11-10T12:03:04.567Z")))
    }
}
