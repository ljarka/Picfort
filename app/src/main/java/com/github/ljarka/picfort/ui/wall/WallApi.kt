package com.github.ljarka.picfort.ui.wall

import com.github.ljarka.picfort.network.CleartextNotAllowedException
import com.github.ljarka.picfort.ui.frame.FrameSettingsStore
import com.github.ljarka.picfort.ui.frame.normalizeFrameUrl
import com.github.ljarka.picfort.ui.login.AccessTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WallHttpClient

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

/** Why a wall call failed, as the UI tells it. */
sealed class WallException(message: String) : Exception(message) {
    class NotConfigured : WallException("Connect your Picfort server to use the Family Wall")
    class Unreachable(host: String) : WallException("Can't reach the Picfort server at $host")
    /** 401: the server didn't accept our Immich login (e.g. it uses another Immich). */
    class NotAccepted : WallException("The Picfort server didn't accept your Immich login")
    /** 404 on `/wall`: an ImageFrameServer without the wall. */
    class NoWall : WallException("This Picfort server has no Family Wall yet, update ImageFrameServer")
    /** 502: the server can't ask Immich who we are. */
    class ImmichUnreachable : WallException("The Picfort server can't reach Immich")
    class Forbidden : WallException("Only the author can change this post")
    class Other(message: String) : WallException(message)
}

@Serializable
private data class ErrorBody(val message: String? = null)

/** Family Wall calls to the Picfort server (ImageFrameServer, T-001). */
@Singleton
class WallApi @Inject constructor(
    @WallHttpClient private val client: OkHttpClient,
    private val settings: FrameSettingsStore,
    private val tokens: AccessTokenStore,
    private val json: Json,
) {
    val isConfigured: Boolean get() = normalizeFrameUrl(settings.frameUrl) != null

    /** [address] other than the saved one is for testing a server before saving it. */
    suspend fun posts(limit: Int = 100, address: String = settings.frameUrl): List<WallPost> {
        val base = base(address)
        val url = base.newBuilder().addPathSegment("wall").addQueryParameter("limit", "$limit").build()
        val text = execute(Request.Builder().url(url).build())
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

    private fun base(address: String = settings.frameUrl) =
        normalizeFrameUrl(address) ?: throw WallException.NotConfigured()

    private suspend fun execute(unsigned: Request): String = withContext(Dispatchers.IO) {
        // Signed here too (not only by WallAuthInterceptor), so an address that isn't saved yet
        // can be tested. Only ever for /wall calls (ADR 0006).
        val request = unsigned.newBuilder().header("Authorization", "Bearer ${tokens.accessToken}").build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when (response.code) {
                    in 200..299 -> text
                    401 -> throw WallException.NotAccepted()
                    403 -> throw WallException.Forbidden()
                    404 -> throw if (request.url.pathSegments.last() == "wall") WallException.NoWall()
                    else WallException.Other("This post no longer exists")
                    502 -> throw WallException.ImmichUnreachable()
                    else -> throw WallException.Other(
                        runCatching { json.decodeFromString<ErrorBody>(text).message }.getOrNull()
                            ?: "The Picfort server returned HTTP ${response.code}"
                    )
                }
            }
        } catch (e: CleartextNotAllowedException) {
            throw WallException.Other(e.message ?: "Use https:// for servers outside your network")
        } catch (e: IOException) {
            throw WallException.Unreachable(request.url.host)
        }
    }
}
