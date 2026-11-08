package com.github.ljarka.picfort.collections

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchDurationTest {

    @Test
    fun `reads both server formats`() {
        assertEquals(12_500L, parseSearchDuration(JsonPrimitive("0:00:12.500000")))
        assertEquals(12_500L, parseSearchDuration(JsonPrimitive(12_500)))
    }

    @Test
    fun `images have none`() {
        assertNull(parseSearchDuration(null))
        assertNull(parseSearchDuration(JsonNull))
        assertNull(parseSearchDuration(JsonPrimitive(0)))
    }

    @Test
    fun `search response parses with either format`() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString<SearchResponse>(
            """{"assets":{"items":[{"id":"a","type":"VIDEO","duration":"0:00:03.000000"}],"nextPage":"2","total":1}}"""
        )
        val new = json.decodeFromString<SearchResponse>(
            """{"assets":{"items":[{"id":"a","type":"VIDEO","duration":3000}],"nextPage":null,"nextCursor":null}}"""
        )
        assertEquals("2", old.assets.nextPage)
        assertEquals(3000L, parseSearchDuration(old.assets.items.single().duration))
        assertEquals(3000L, parseSearchDuration(new.assets.items.single().duration))
        assertNull(new.assets.nextPage)
    }
}
