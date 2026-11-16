package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.core.model.WishlistItem
import com.github.ljarka.picfort.core.model.WishlistPreview
import com.github.ljarka.picfort.core.network.PicfortServerClient
import com.github.ljarka.picfort.core.network.PicfortServerException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class WishlistItemDto(
    val id: Long,
    val url: String,
    val title: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("price_minor") val priceMinor: Long? = null,
    val currency: String = "PLN",
    @SerialName("event_id") val eventId: Long? = null,
    @SerialName("target_member_id") val targetMemberId: String? = null,
    @SerialName("reserved_by") val reservedBy: String? = null,
    val bought: Boolean = false,
    @SerialName("created_by") val createdBy: String = "",
    val created: String = "",
    val updated: String = "",
) {
    fun toDomain() = WishlistItem(
        id = id,
        url = url,
        title = title,
        imageUrl = imageUrl,
        priceMinor = priceMinor,
        currency = currency,
        eventId = eventId,
        targetMemberId = targetMemberId,
        reservedBy = reservedBy,
        bought = bought,
        createdBy = createdBy,
        created = created,
        updated = updated,
    )
}

@Serializable
data class WishlistListDto(val items: List<WishlistItemDto> = emptyList())

@Serializable
data class WishlistPreviewDto(
    val title: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("price_minor") val priceMinor: Long? = null,
    @SerialName("shop_name") val shopName: String? = null,
) {
    fun toDomain() = WishlistPreview(
        title = title,
        imageUrl = imageUrl,
        priceMinor = priceMinor,
        shopName = shopName,
    )
}

interface WishlistBackend {
    suspend fun list(eventId: Long? = null, bought: Boolean? = null): List<WishlistItem>
    suspend fun preview(url: String): WishlistPreview
    suspend fun create(
        url: String,
        title: String? = null,
        imageUrl: String? = null,
        priceMinor: Long? = null,
        eventId: Long? = null,
        targetMemberId: String? = null,
    ): WishlistItem

    suspend fun update(
        id: Long,
        title: String? = null,
        imageUrl: String? = null,
        clearImage: Boolean = false,
        priceMinor: Long? = null,
        clearPrice: Boolean = false,
        eventId: Long? = null,
        clearEvent: Boolean = false,
        targetMemberId: String? = null,
        clearTargetMember: Boolean = false,
        reservedBy: String? = null,
        clearReservedBy: Boolean = false,
        bought: Boolean? = null,
    ): WishlistItem

    suspend fun delete(id: Long)
}

@Singleton
class WishlistApi @Inject constructor(private val server: PicfortServerClient, private val json: Json) :
    WishlistBackend {

    override suspend fun list(eventId: Long?, bought: Boolean?): List<WishlistItem> {
        val url = server.base().newBuilder()
            .addPathSegment("wishlist")
            .apply {
                if (eventId != null) addQueryParameter("event_id", "$eventId")
                if (bought != null) addQueryParameter("bought", if (bought) "1" else "0")
            }
            .build()
        val raw = server.execute(Request.Builder().url(url).build()) {
            PicfortServerException.Outdated("wishlist")
        }.body
        return json.decodeFromString<WishlistListDto>(raw).items.map { it.toDomain() }
    }

    override suspend fun preview(url: String): WishlistPreview {
        val endpoint = server.base().newBuilder().addPathSegment("wishlist").addPathSegment("preview").build()
        val body = buildJsonObject { put("url", url) }
        val raw = server.execute(Request.Builder().url(endpoint).post(body(body)).build()) {
            PicfortServerException.Outdated("wishlist preview")
        }.body
        return json.decodeFromString<WishlistPreviewDto>(raw).toDomain()
    }

    override suspend fun create(
        url: String,
        title: String?,
        imageUrl: String?,
        priceMinor: Long?,
        eventId: Long?,
        targetMemberId: String?,
    ): WishlistItem {
        val endpoint = server.base().newBuilder().addPathSegment("wishlist").build()
        val body = buildJsonObject {
            put("url", url)
            if (!title.isNullOrBlank()) put("title", title.trim())
            if (!imageUrl.isNullOrBlank()) put("image_url", imageUrl.trim())
            if (priceMinor != null) put("price_minor", priceMinor)
            if (eventId != null) put("event_id", eventId)
            if (!targetMemberId.isNullOrBlank()) put("target_member_id", targetMemberId)
        }
        val raw = server.execute(Request.Builder().url(endpoint).post(body(body)).build()) {
            PicfortServerException.Outdated("wishlist")
        }.body
        return json.decodeFromString<WishlistItemDto>(raw).toDomain()
    }

    override suspend fun update(
        id: Long,
        title: String?,
        imageUrl: String?,
        clearImage: Boolean,
        priceMinor: Long?,
        clearPrice: Boolean,
        eventId: Long?,
        clearEvent: Boolean,
        targetMemberId: String?,
        clearTargetMember: Boolean,
        reservedBy: String?,
        clearReservedBy: Boolean,
        bought: Boolean?,
    ): WishlistItem {
        val endpoint = itemUrl(id)
        val body = buildJsonObject {
            if (title != null) put("title", title.trim())
            when {
                clearImage -> put("image_url", JsonNull)
                imageUrl != null -> put("image_url", imageUrl.trim())
            }
            when {
                clearPrice -> put("price_minor", JsonNull)
                priceMinor != null -> put("price_minor", priceMinor)
            }
            when {
                clearEvent -> put("event_id", JsonNull)
                eventId != null -> put("event_id", eventId)
            }
            when {
                clearTargetMember -> put("target_member_id", JsonNull)
                targetMemberId != null -> put("target_member_id", targetMemberId)
            }
            when {
                clearReservedBy -> put("reserved_by", JsonNull)
                reservedBy != null -> put("reserved_by", reservedBy)
            }
            if (bought != null) put("bought", bought)
        }
        val raw = server.execute(Request.Builder().url(endpoint).patch(body(body)).build()) {
            PicfortServerException.Gone("This wishlist item no longer exists")
        }.body
        return json.decodeFromString<WishlistItemDto>(raw).toDomain()
    }

    override suspend fun delete(id: Long) {
        server.execute(Request.Builder().url(itemUrl(id)).delete().build()) {
            PicfortServerException.Gone("This wishlist item no longer exists")
        }
    }

    private fun itemUrl(id: Long) = server.base().newBuilder().addPathSegment("wishlist").addPathSegment("$id").build()

    private fun body(obj: JsonObject) = obj.toString().toRequestBody(JSON)

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
