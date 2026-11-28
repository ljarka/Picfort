package com.github.ljarka.picfort.memories

import java.time.LocalDate
import java.util.Locale
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorySectionsTest {
    private val today = LocalDate.parse("2026-11-28")

    private fun memory(
        id: String,
        day: String,
        year: Int,
        photos: Int = 3,
        saved: Boolean = false,
        type: String = "on_this_day",
        person: String? = null,
    ) = MemoryDto(
        id = id,
        type = type,
        data = MemoryDataDto(year = year, personName = person),
        memoryAt = "${day}T00:00:00.000Z",
        isSaved = saved,
        assets = List(photos) { MemoryAssetDto("$id-$it", type = if (it == 2) "VIDEO" else "IMAGE") },
    )

    @Test
    fun `today first with the most recent year on top, then saved, then earlier days`() {
        val sections = memorySections(
            listOf(
                memory("old", "2026-11-28", 2019),
                memory("recent", "2026-11-28", 2025),
                memory("yesterday", "2026-11-27", 2024),
                memory("kept", "2026-11-20", 2022, saved = true),
                memory("empty", "2026-11-28", 2020, photos = 0),
            ),
            today,
            Locale.ENGLISH,
        )
        assertEquals(listOf("Today", "Saved", "Earlier"), sections.map { it.title })
        assertEquals(listOf("recent", "old"), sections[0].memories.map { it.id })
        assertEquals(listOf("1 year ago", "7 years ago"), sections[0].memories.map { it.title })
        assertEquals("November 28, 2025 · 3 photos", sections[0].memories[0].subtitle)
        assertEquals(listOf("kept"), sections[1].memories.map { it.id })
        assertEquals(listOf("yesterday"), sections[2].memories.map { it.id })
    }

    @Test
    fun `birthdays, videos and the viewer source`() {
        val m = memorySections(
            listOf(memory("bday", "2026-11-28", 2016, photos = 1, type = "birthday", person = "Ola")),
            today,
            Locale.ENGLISH,
        ).single().memories.single()
        assertEquals("Ola's birthday", m.title)
        assertTrue(m.subtitle.endsWith("1 photo"))
        assertEquals("memory:bday", m.source)
        val video = memorySections(listOf(memory("v", "2026-11-28", 2024)), today, Locale.ENGLISH)
            .single().memories.single().assets[2]
        assertTrue(video.isVideo)
    }

    @Test
    fun `nothing to show gives no sections`() {
        assertTrue(memorySections(emptyList(), today, Locale.ENGLISH).isEmpty())
    }

    @Test
    fun `reads Immich 3 memory JSON, ignoring the rest of the asset`() {
        val json = Json { ignoreUnknownKeys = true }
        val text = """
            [{"id":"m1","createdAt":"2026-11-28T01:00:00.000Z","updatedAt":"2026-11-28T01:00:00.000Z",
              "memoryAt":"2026-11-28T00:00:00.000Z","ownerId":"u1","type":"on_this_day","data":{"year":2023},
              "isSaved":false,"assets":[{"id":"a1","type":"IMAGE","thumbhash":null,"localDateTime":"2023-11-28T10:00:00.000Z",
              "duration":null,"hasMetadata":true,"width":4000,"height":3000,"ownerId":"u1","originalFileName":"x.jpg",
              "isFavorite":false,"isArchived":false,"isTrashed":false,"isOffline":false,"visibility":"timeline",
              "checksum":"c","isEdited":false}]}]
        """.trimIndent()
        val parsed = json.decodeFromString<List<MemoryDto>>(text)
        assertEquals(2023, parsed.single().data.year)
        assertEquals("3 years ago", memorySections(parsed, today, Locale.ENGLISH).single().memories.single().title)
    }
}
