package com.github.ljarka.picfort.collections

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTagsTest {

    @Test
    fun `curated ai tags come first in order, cleanup tags are omitted, user tags follow sorted`() {
        val raw = listOf(
            TagDto("1", "blurry", "ai/cleanup/blurry"),
            TagDto("2", "nature", "ai/type/nature"),
            TagDto("3", "Vacation", "Vacation"),
            TagDto("4", "frame-worthy", "ai/best/frame-worthy"),
            TagDto("5", "kids-art", "ai/type/kids-art"),
            TagDto("6", "Animals", "Animals"),
            TagDto("7", "ai", "ai"),
            TagDto("8", "sensitive", "ai/privacy/sensitive"),
        )
        val formatted = formatSearchTags(raw)
        assertEquals(
            listOf("Best", "Nature", "Kids' art", "Sensitive", "Animals", "Vacation"),
            formatted.map { it.label },
        )
    }
}
