package com.github.ljarka.picfort.sync

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * `UserV1` / `UserDeleteV1` (Family Wall members, T-002). The field names follow Immich's sync
 * DTOs but weren't checked against a live server yet, so parsing must tolerate missing fields.
 */
class SyncUsersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun change(line: String) = json.decodeFromString<SyncLine>(line).toChange(json)

    @Test
    fun `users are requested`() {
        assertTrue("UsersV1" in TIMELINE_SYNC_TYPES)
    }

    @Test
    fun `parses a user`() {
        val c = change(
            """{"type":"UserV1","data":{"id":"u1","name":"Mama","email":"mama@example.com","avatarColor":"pink",
               "deletedAt":null,"hasProfileImage":true,"profileChangedAt":"2026-11-09T17:51:36.681Z"},"ack":"UserV1|1"}"""
        ) as SyncChange.UpsertUser
        with(c.user) {
            assertEquals("u1" to "Mama", id to name)
            assertEquals("pink", avatarColor)
            assertTrue(hasProfileImage)
            assertEquals(Instant.parse("2026-11-09T17:51:36.681Z").toEpochMilli(), profileChangedAt)
        }
    }

    @Test
    fun `missing fields fall back`() {
        val c = change("""{"type":"UserV1","data":{"id":"u2","email":"tata@example.com"},"ack":"UserV1|2"}""")
        with((c as SyncChange.UpsertUser).user) {
            assertEquals("tata@example.com", name) // no name → email
            assertEquals(null, avatarColor)
            assertEquals(false, hasProfileImage)
        }
    }

    @Test
    fun `deleted users are removed`() {
        assertEquals(
            SyncChange.DeleteUser("u1"),
            change("""{"type":"UserV1","data":{"id":"u1","name":"x","deletedAt":"2026-11-09T00:00:00.000Z"},"ack":"UserV1|3"}"""),
        )
        assertEquals(
            SyncChange.DeleteUser("u2"),
            change("""{"type":"UserDeleteV1","data":{"userId":"u2"},"ack":"UserDeleteV1|4"}"""),
        )
    }
}
