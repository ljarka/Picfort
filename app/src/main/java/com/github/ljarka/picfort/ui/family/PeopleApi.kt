package com.github.ljarka.picfort.ui.family

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.github.ljarka.picfort.network.PicfortServerException
import com.github.ljarka.picfort.ui.server.ServerUrlStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

@Serializable
data class ImmichPersonDto(
    val id: String,
    val name: String = "",
    val isHidden: Boolean = false,
)

@Serializable
data class ImmichPeopleResponseDto(
    val people: List<ImmichPersonDto> = emptyList(),
)

interface PeopleSource {
    /** Named, non-hidden people from the caller's Immich library, sorted by name. */
    suspend fun namedPeople(): List<ImmichPersonItem>

    /** Downloads a person's face thumbnail from Immich, crops square, and resizes to 256x256 JPEG. */
    suspend fun faceThumbnailJpeg(personId: String): ByteArray
}

/**
 * Reads recognised people and face thumbnails from the caller's Immich library (shared by T-032
 * and T-030). AuthInterceptor attaches the caller's Immich token to requests under [ServerUrlStore].
 */
@Singleton
class PeopleApi @Inject constructor(
    private val client: OkHttpClient,
    private val serverUrlStore: ServerUrlStore,
    private val json: Json,
) : PeopleSource {

    override suspend fun namedPeople(): List<ImmichPersonItem> = withContext(Dispatchers.IO) {
        val base = serverUrlStore.serverUrl.trimEnd('/')
        val url = "$base/api/people?withHidden=false".toHttpUrlOrNull()
            ?: throw PicfortServerException.Other("Invalid Immich server URL")
        val raw = getString(url.toString())
        parseNamedPeople(raw, base, json)
    }

    override suspend fun faceThumbnailJpeg(personId: String): ByteArray = withContext(Dispatchers.IO) {
        val base = serverUrlStore.serverUrl.trimEnd('/')
        val bytes = getBytes("$base/api/people/$personId/thumbnail")
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw PicfortServerException.Other("Couldn't decode the face thumbnail from Immich")
        val square = cropSquareAndResize(decoded, AVATAR_PX)
        ByteArrayOutputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private fun getString(url: String): String {
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw PicfortServerException.Other("Immich returned HTTP ${resp.code}")
                }
                return resp.body.string()
            }
        } catch (e: IOException) {
            throw PicfortServerException.Other("Couldn't reach Immich: ${e.message}")
        }
    }

    private fun getBytes(url: String): ByteArray {
        try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw PicfortServerException.Other("Immich returned HTTP ${resp.code}")
                }
                return resp.body.bytes()
            }
        } catch (e: IOException) {
            throw PicfortServerException.Other("Couldn't reach Immich: ${e.message}")
        }
    }

    companion object {
        const val AVATAR_PX = 256
        private const val JPEG_QUALITY = 90

        internal fun parseNamedPeople(raw: String, immichBaseUrl: String, json: Json): List<ImmichPersonItem> {
            val base = immichBaseUrl.trimEnd('/')
            val element = json.parseToJsonElement(raw)
            val dtos: List<ImmichPersonDto> = when (element) {
                is JsonObject -> json.decodeFromJsonElement<ImmichPeopleResponseDto>(element).people
                is JsonArray -> json.decodeFromJsonElement<List<ImmichPersonDto>>(element)
                else -> emptyList()
            }
            return dtos
                .filter { !it.isHidden && it.name.isNotBlank() }
                .map { dto ->
                    ImmichPersonItem(
                        id = dto.id,
                        name = dto.name.trim(),
                        thumbnailUrl = "$base/api/people/${dto.id}/thumbnail",
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }

        internal fun cropSquareAndResize(bitmap: Bitmap, sizePx: Int): Bitmap {
            val side = min(bitmap.width, bitmap.height)
            val x = (bitmap.width - side) / 2
            val y = (bitmap.height - side) / 2
            val square = if (x == 0 && y == 0 && bitmap.width == bitmap.height) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, x, y, side, side)
            }
            return if (square.width == sizePx && square.height == sizePx) {
                square
            } else {
                Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
            }
        }
    }
}
