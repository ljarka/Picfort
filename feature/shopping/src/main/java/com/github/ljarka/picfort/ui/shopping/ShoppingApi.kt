package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.core.model.ShoppingItem
import com.github.ljarka.picfort.core.model.ShoppingList
import com.github.ljarka.picfort.core.network.PicfortServerClient
import com.github.ljarka.picfort.core.network.PicfortServerException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ShoppingItemDto(
    val id: Long,
    val text: String,
    val checked: Boolean = false,
    val position: Double = 0.0,
    @SerialName("checked_by") val checkedBy: String? = null,
    @SerialName("checked_at") val checkedAt: String? = null,
    @SerialName("created_by") val createdBy: String = "",
) {
    fun toItem() = ShoppingItem(id, text, checked, position, checkedBy, checkedAt, createdBy)
}

@Serializable
data class ShoppingListDto(
    val id: Long,
    val title: String,
    val archived: Boolean = false,
    val revision: Long = 1,
    @SerialName("created_by") val createdBy: String = "",
    val created: String = "",
    val items: List<ShoppingItemDto> = emptyList(),
) {
    fun toList() = ShoppingList(id, title, archived, revision, createdBy, created, items.map { it.toItem() })
}

private fun ShoppingItem.toDto() = ShoppingItemDto(id, text, checked, position, checkedBy, checkedAt, createdBy)

private fun ShoppingList.toDto() = ShoppingListDto(id, title, archived, revision, createdBy, created, items.map { it.toDto() })

/** A list fetched with If-None-Match: null when it hasn't changed (304). */
data class ListFetch(val list: ShoppingList?)

/** What [ShoppingRepository] needs from the server; faked in tests. */
interface ShoppingBackend {
    /** The lists and the raw JSON (kept as the offline copy). */
    suspend fun lists(archived: Boolean = false): Pair<List<ShoppingList>, String>
    suspend fun list(id: Long, knownRevision: Long? = null): ListFetch
    suspend fun create(title: String): ShoppingList
    suspend fun update(id: Long, title: String? = null, archived: Boolean? = null): ShoppingList
    suspend fun delete(id: Long)
    suspend fun uncheckAll(id: Long): ShoppingList
    suspend fun deleteChecked(id: Long): ShoppingList
    suspend fun addItem(listId: Long, text: String, position: Double?): ShoppingItem
    suspend fun updateItem(listId: Long, itemId: Long, text: String? = null, checked: Boolean? = null): ShoppingItem
    suspend fun deleteItem(listId: Long, itemId: Long)
    fun decodeLists(raw: String): List<ShoppingList>
    fun encodeLists(lists: List<ShoppingList>): String
}

/** Shopping lists on the Picfort server (T-018). */
@Singleton
class ShoppingApi @Inject constructor(
    private val server: PicfortServerClient,
    private val json: Json,
) : ShoppingBackend {

    override suspend fun lists(archived: Boolean): Pair<List<ShoppingList>, String> {
        val url = lists().apply { if (archived) addQueryParameter("archived", "1") }.build()
        val raw = call(Request.Builder().url(url).build())
        return decodeLists(raw) to raw
    }

    override fun decodeLists(raw: String): List<ShoppingList> =
        json.decodeFromString<List<ShoppingListDto>>(raw).map { it.toList() }

    override fun encodeLists(lists: List<ShoppingList>): String =
        json.encodeToString(lists.map { it.toDto() })

    override suspend fun list(id: Long, knownRevision: Long?): ListFetch {
        val request = Request.Builder().url(list(id).build())
            .apply { knownRevision?.let { header("If-None-Match", "\"$id-$it\"") } }
            .build()
        val reply = server.execute(request, notFound(id))
        return ListFetch(if (reply.code == 304) null else json.decodeFromString<ShoppingListDto>(reply.body).toList())
    }

    override suspend fun create(title: String) =
        listCall(Request.Builder().url(lists().build()).post(body(buildJsonObject { put("title", title) })).build())

    override suspend fun update(id: Long, title: String?, archived: Boolean?) = listCall(
        Request.Builder().url(list(id).build()).patch(body(buildJsonObject {
            title?.let { put("title", it) }
            archived?.let { put("archived", it) }
        })).build(),
        id,
    )

    override suspend fun delete(id: Long) {
        call(Request.Builder().url(list(id).build()).delete().build(), id)
    }

    override suspend fun uncheckAll(id: Long) =
        listCall(Request.Builder().url(list(id).addPathSegment("uncheck-all").build()).post(empty()).build(), id)

    override suspend fun deleteChecked(id: Long) =
        listCall(Request.Builder().url(list(id).addPathSegment("delete-checked").build()).post(empty()).build(), id)

    override suspend fun addItem(listId: Long, text: String, position: Double?): ShoppingItem {
        val body = buildJsonObject {
            put("text", text)
            position?.let { put("position", it) }
        }
        val url = list(listId).addPathSegment("items").build()
        return itemCall(Request.Builder().url(url).post(body(body)).build(), listId)
    }

    override suspend fun updateItem(listId: Long, itemId: Long, text: String?, checked: Boolean?): ShoppingItem {
        val body = buildJsonObject {
            text?.let { put("text", it) }
            checked?.let { put("checked", it) }
        }
        return itemCall(Request.Builder().url(item(listId, itemId)).patch(body(body)).build(), listId)
    }

    override suspend fun deleteItem(listId: Long, itemId: Long) {
        call(Request.Builder().url(item(listId, itemId)).delete().build(), listId)
    }

    private fun lists(): HttpUrl.Builder = server.base().newBuilder().addPathSegment("lists")
    private fun list(id: Long) = lists().addPathSegment("$id")
    private fun item(listId: Long, itemId: Long) =
        list(listId).addPathSegment("items").addPathSegment("$itemId").build()

    private fun notFound(listId: Long?) = {
        if (listId == null) PicfortServerException.Outdated("shopping lists")
        else PicfortServerException.Gone("This list or item no longer exists")
    }

    private suspend fun call(request: Request, listId: Long? = null) = server.execute(request, notFound(listId)).body

    private suspend fun listCall(request: Request, listId: Long? = null) =
        json.decodeFromString<ShoppingListDto>(call(request, listId)).toList()

    private suspend fun itemCall(request: Request, listId: Long) =
        json.decodeFromString<ShoppingItemDto>(call(request, listId)).toItem()

    private fun body(obj: JsonObject) = obj.toString().toRequestBody(JSON)
    private fun empty() = JsonObject(emptyMap()).toString().toRequestBody(JSON)

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
