package com.github.ljarka.picfort.ui.wall

import com.github.ljarka.picfort.core.network.FrameSettingsStore
import com.github.ljarka.picfort.core.network.PicfortServerClient
import com.github.ljarka.picfort.core.network.PicfortServerException
import com.github.ljarka.picfort.core.network.picfortKey
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A post as the Picfort server returns it (T-001). */
@Serializable
data class WallPostDto(
    val id: Int,
    @SerialName("author_id") val authorId: String,
    val author: String,
    val text: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("photo_width") val photoWidth: Int? = null,
    @SerialName("photo_height") val photoHeight: Int? = null,
    val pinned: Boolean = false,
    val created: String,
    val reactions: List<WallReactionDto> = emptyList(),
)

/** One emoji on a post with everyone who chose it (T-084). */
@Serializable
data class WallReactionDto(
    val emoji: String,
    val count: Int = 0,
    @SerialName("user_ids") val userIds: List<String> = emptyList(),
    val names: List<String> = emptyList(),
)

/** A post of mine that got its first reaction from someone else (T-084), for a notification. */
@Serializable
data class FirstReactionDto(
    @SerialName("post_id") val postId: Int,
    val text: String? = null,
    @SerialName("has_photo") val hasPhoto: Boolean = false,
    val at: String,
    val by: String,
    val emoji: String,
)

@Serializable
private data class FirstReactionsDto(val items: List<FirstReactionDto> = emptyList())

/** What the frame shows (T-091): [url] for the card, [backdropUrl] a tiny copy for the Wall's background (T-106). */
data class FramePhoto(val url: String, val backdropUrl: String)

/** The server rounds sizes up to 128 px: plenty for a blurred background. */
private const val BACKDROP_SIDE = 128

@Serializable
private data class FrameStateDto(val version: Int = 0, val shown: Boolean = false)

/** The quick reactions, in the order the server and the app show them (T-084). */
val WALL_REACTIONS =
    listOf("\u2764\uFE0F", "\uD83D\uDC4D", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE22", "\uD83C\uDF89")

data class WallReaction(val emoji: String, val userIds: List<String>, val names: List<String>) {
    val count: Int get() = userIds.size

    fun isMine(myId: String) = myId in userIds
}

data class WallPost(
    val id: Int,
    val authorId: String,
    /** Name at the time of posting; shown for authors who aren't Immich users (any more). */
    val authorName: String,
    val text: String?,
    /** Absolute URL on the Picfort server, or null. */
    val photoUrl: String?,
    val photoWidth: Int?,
    val photoHeight: Int?,
    val pinned: Boolean,
    val created: Instant,
    /** Emojis with people, in [WALL_REACTIONS] order; none until someone reacts (T-084). */
    val reactions: List<WallReaction> = emptyList(),
) {
    /** Width / height, or null when unknown. */
    val aspectRatio: Float?
        get() = if (photoWidth != null && photoHeight != null && photoWidth > 0 && photoHeight > 0) {
            photoWidth.toFloat() / photoHeight
        } else {
            null
        }
}

internal fun WallPostDto.toPost(base: HttpUrl) = WallPost(
    id = id,
    authorId = authorId,
    authorName = author,
    text = text,
    photoUrl = photoUrl?.let { base.resolve(it)?.toString() },
    photoWidth = photoWidth,
    photoHeight = photoHeight,
    pinned = pinned,
    created = runCatching { Instant.parse(created) }.getOrDefault(Instant.EPOCH),
    reactions = reactions.filter { it.userIds.isNotEmpty() }.map { WallReaction(it.emoji, it.userIds, it.names) },
)

/** [post] with [myId]'s [emoji] added or removed, for an instant answer before the server's. */
internal fun WallPost.withReactionToggled(emoji: String, myId: String, myName: String): WallPost {
    val current = reactions.firstOrNull { it.emoji == emoji }
    val updated = when {
        current == null -> reactions + WallReaction(emoji, listOf(myId), listOf(myName))

        current.isMine(myId) -> {
            val index = current.userIds.indexOf(myId)
            val left = current.copy(
                userIds = current.userIds.filterIndexed { i, _ -> i != index },
                names = current.names.filterIndexed { i, _ -> i != index },
            )
            reactions.map { if (it.emoji == emoji) left else it }.filter { it.count > 0 }
        }

        else -> reactions.map {
            if (it.emoji == emoji) it.copy(userIds = it.userIds + myId, names = it.names + myName) else it
        }
    }
    return copy(
        reactions = updated.sortedBy {
            WALL_REACTIONS.indexOf(it.emoji).let { i ->
                if (i <
                    0
                ) {
                    Int.MAX_VALUE
                } else {
                    i
                }
            }
        },
    )
}

/** Family Wall calls to the Picfort server (T-001). */
@Singleton
class WallApi @Inject constructor(
    private val server: PicfortServerClient,
    private val settings: FrameSettingsStore,
    private val json: Json,
) {
    val isConfigured: Boolean get() = server.isConfigured

    /** [address] and [apiKey] other than the saved ones are for testing a server before saving it. */
    suspend fun posts(limit: Int = 100, address: String = settings.frameUrl, apiKey: String? = null): List<WallPost> {
        val base = base(address)
        val url = base.newBuilder().addPathSegment("wall").addQueryParameter("limit", "$limit").build()
        val text = execute(Request.Builder().url(url).apply { apiKey?.let { picfortKey(it) } }.build())
        return json.decodeFromString<List<WallPostDto>>(text).map { it.toPost(base) }
    }

    suspend fun create(text: String?, photoJpeg: ByteArray?): WallPost {
        val base = base()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
            text?.let { addFormDataPart("text", it) }
            photoJpeg?.let { addFormDataPart("photo", "photo.jpg", it.toRequestBody("image/jpeg".toMediaType())) }
        }.build()
        val url = base.newBuilder().addPathSegment("wall").build()
        return json.decodeFromString<WallPostDto>(execute(Request.Builder().url(url).post(body).build())).toPost(base)
    }

    suspend fun delete(id: Int) {
        val url = base().newBuilder().addPathSegment("wall").addPathSegment("$id").build()
        execute(Request.Builder().url(url).delete().build())
    }

    suspend fun togglePin(id: Int): WallPost {
        val base = base()
        val url = base.newBuilder().addPathSegment("wall").addPathSegment("$id").addPathSegment("pin").build()
        val request = Request.Builder().url(url).put(ByteArray(0).toRequestBody()).build()
        return json.decodeFromString<WallPostDto>(execute(request)).toPost(base)
    }

    /** Adds ([add]) or removes the caller's [emoji] on post [id] (T-084); the post as it is now. */
    suspend fun react(id: Int, emoji: String, add: Boolean): WallPost {
        val base = base()
        val url = base.newBuilder().addPathSegment("wall").addPathSegment("$id").addPathSegment("reactions")
            .apply { if (!add) addQueryParameter("emoji", emoji) }
            .build()
        val request = if (add) {
            val body = buildJsonObject {
                put("emoji", emoji)
            }.toString().toRequestBody("application/json".toMediaType())
            Request.Builder().url(url).put(body).build()
        } else {
            Request.Builder().url(url).delete().build()
        }
        return json.decodeFromString<WallPostDto>(execute(request)).toPost(base)
    }

    /** My posts whose first reaction by someone else came after [since] (ISO time), oldest first. */
    suspend fun firstReactions(since: String): List<FirstReactionDto> {
        val url = base().newBuilder().addPathSegment("wall").addPathSegment("first-reactions")
            .addQueryParameter("since", since)
            .build()
        val text = server.execute(Request.Builder().url(url).build()) {
            PicfortServerException.Outdated("wall reactions")
        }.body
        return json.decodeFromString<FirstReactionsDto>(text).items
    }

    /**
     * Full-colour URL of what the frame currently shows (`/frame/photo`, T-091), versioned so
     * Coil reloads whenever the frame switches photo. Null when the browser frame is off or empty.
     */
    suspend fun framePhoto(): FramePhoto? {
        val base = base()
        val stateUrl = base.newBuilder().addPathSegment("frame").addPathSegment("state").build()
        val text = server.execute(Request.Builder().url(stateUrl).build()) {
            PicfortServerException.Other("Browser frame unavailable")
        }.body
        val state = json.decodeFromString<FrameStateDto>(text)
        if (!state.shown) return null
        fun sized(w: Int, h: Int) = base.newBuilder()
            .addPathSegment("frame")
            .addPathSegment("photo")
            .addQueryParameter("w", w.toString())
            .addQueryParameter("h", h.toString())
            .addQueryParameter("v", state.version.toString())
            .build()
            .toString()
        // The backdrop is drawn blurred over the whole Wall: a tiny copy is enough (T-106).
        return FramePhoto(url = sized(1080, 720), backdropUrl = sized(BACKDROP_SIDE, BACKDROP_SIDE))
    }

    private fun base(address: String = settings.frameUrl) = server.base(address)

    private suspend fun execute(request: Request): String = server.execute(request) {
        if (request.url.pathSegments.last() == "wall") {
            PicfortServerException.NoWall()
        } else {
            PicfortServerException.Other("This post no longer exists")
        }
    }.body
}
