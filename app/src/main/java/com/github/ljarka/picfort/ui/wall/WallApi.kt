package com.github.ljarka.picfort.ui.wall

import com.github.ljarka.picfort.network.PicfortServerClient
import com.github.ljarka.picfort.core.network.PicfortServerException
import com.github.ljarka.picfort.network.picfortKey
import com.github.ljarka.picfort.ui.frame.FrameSettingsStore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A post as ImageFrameServer returns it (T-001). */
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
)

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
) {
    /** Width / height, or null when unknown. */
    val aspectRatio: Float?
        get() = if (photoWidth != null && photoHeight != null && photoWidth > 0 && photoHeight > 0) {
            photoWidth.toFloat() / photoHeight
        } else null
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
)

/** Family Wall calls to the Picfort server (ImageFrameServer, T-001). */
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

    private fun base(address: String = settings.frameUrl) = server.base(address)

    private suspend fun execute(request: Request): String = server.execute(request) {
        if (request.url.pathSegments.last() == "wall") PicfortServerException.NoWall()
        else PicfortServerException.Other("This post no longer exists")
    }.body
}
