package com.github.ljarka.picfort.ui.wall

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.github.ljarka.picfort.core.model.BirthDate
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.isTestAccount
import com.github.ljarka.picfort.core.model.meFirst
import com.github.ljarka.picfort.core.network.AccessTokenStore
import com.github.ljarka.picfort.core.network.compressImageUri
import com.github.ljarka.picfort.core.ui.avatar.UNKNOWN_MEMBER_COLOR
import com.github.ljarka.picfort.core.ui.avatar.avatarColor
import com.github.ljarka.picfort.core.ui.avatar.initials
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallRepository @Inject constructor(
    private val api: WallApi,
    private val accessTokenStore: AccessTokenStore,
    @ApplicationContext private val context: Context,
) {
    val isConfigured: Boolean get() = api.isConfigured
    val myId: String get() = accessTokenStore.userId

    suspend fun posts(): List<WallPost> = api.posts()

    /** Full-colour photo currently shown on the frame (`/frame/photo`), or null (T-091). */
    suspend fun framePhotoUrl(): String? = runCatching { api.framePhotoUrl() }.getOrNull()

    /** [photo] is compressed before upload (longest edge 1920 px, JPEG 85%). */
    suspend fun create(text: String?, photo: Uri?): WallPost =
        api.create(text?.trim()?.takeIf { it.isNotEmpty() }, photo?.let { compress(it) })

    suspend fun delete(id: Int) = api.delete(id)

    suspend fun togglePin(id: Int): WallPost = api.togglePin(id)

    /** Adds my [emoji] to [post], or removes it when it is already mine (T-084). */
    suspend fun toggleReaction(post: WallPost, emoji: String): WallPost {
        val mine = post.reactions.any { it.emoji == emoji && it.isMine(myId) }
        return api.react(post.id, emoji, add = !mine)
    }

    private suspend fun compress(uri: Uri): ByteArray =
        compressImageUri(context, uri, maxEdge = MAX_EDGE, jpegQuality = JPEG_QUALITY)

    private companion object {
        const val MAX_EDGE = 1920
        const val JPEG_QUALITY = 85
    }
}
