package com.github.ljarka.picfort.ui.wall

import com.github.ljarka.picfort.core.model.AssistantWallNote
import com.github.ljarka.picfort.core.model.WallAssistantProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallAssistantImpl @Inject constructor(private val repository: WallRepository) : WallAssistantProvider {
    override suspend fun getRecentNotes(limit: Int): List<AssistantWallNote> = runCatching {
        repository.posts().take(limit.coerceIn(1, 20)).map { post ->
            AssistantWallNote(
                author = post.authorName,
                text = post.text,
                hasPhoto = post.photoUrl != null,
                created = post.created.toString(),
            )
        }
    }.getOrDefault(emptyList())

    override suspend fun postNote(text: String): String {
        val clean = text.trim()
        if (clean.isEmpty()) return "Note text cannot be empty."
        return runCatching {
            repository.create(clean, photo = null)
            "Posted to Family Wall: \"$clean\""
        }.getOrElse { "Couldn't post to Family Wall: ${it.message}" }
    }
}
