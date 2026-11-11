package com.github.ljarka.picfort.ui.events

import com.github.ljarka.picfort.network.PicfortServerClient
import com.github.ljarka.picfort.network.PicfortServerException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

const val PAST_PAGE_SIZE = 50
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Serializable
data class EventDto(
    val id: Long,
    val title: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    val location: String? = null,
    val note: String? = null,
    @SerialName("album_id") val albumId: String? = null,
    @SerialName("created_by") val createdBy: String = "",
    val created: String = "",
    val updated: String = "",
) {
    fun toEvent(): FamilyEvent = FamilyEvent(
        id = id,
        title = title,
        startDate = LocalDate.parse(startDate),
        endDate = endDate?.let(LocalDate::parse),
        startTime = startTime?.let { LocalTime.parse(it, TIME_FORMATTER) },
        location = location,
        note = note,
        albumId = albumId,
        createdBy = createdBy,
        created = created,
        updated = updated,
    )
}

data class EventDraft(
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val location: String? = null,
    val note: String? = null,
    val albumId: String? = null,
) {
    val normalizedTitle: String get() = title.trim()
    val normalizedEndDate: LocalDate? get() = endDate?.takeIf { it.isAfter(startDate) }
    val normalizedLocation: String? get() = location?.trim()?.ifEmpty { null }
    val normalizedNote: String? get() = note?.trim()?.ifEmpty { null }
}

/** Builds a PATCH JSON object containing only the fields that changed from [original] (`null` clears optional ones). */
fun buildEventPatchJson(original: FamilyEvent, draft: EventDraft): JsonObject = buildJsonObject {
    if (draft.normalizedTitle != original.title) {
        put("title", draft.normalizedTitle)
    }
    if (draft.startDate != original.startDate) {
        put("start_date", draft.startDate.toString())
    }
    val origEnd = original.endDate?.takeIf { it.isAfter(original.startDate) }
    if (draft.normalizedEndDate != origEnd) {
        put("end_date", draft.normalizedEndDate?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
    }
    if (draft.startTime != original.startTime) {
        put("start_time", draft.startTime?.let { JsonPrimitive(it.format(TIME_FORMATTER)) } ?: JsonNull)
    }
    if (draft.normalizedLocation != original.location) {
        put("location", draft.normalizedLocation?.let(::JsonPrimitive) ?: JsonNull)
    }
    if (draft.normalizedNote != original.note) {
        put("note", draft.normalizedNote?.let(::JsonPrimitive) ?: JsonNull)
    }
    if (draft.albumId != original.albumId) {
        put("album_id", draft.albumId?.let(::JsonPrimitive) ?: JsonNull)
    }
}

interface EventsBackend {
    suspend fun upcoming(after: LocalDate): List<FamilyEvent>
    suspend fun past(before: LocalDate, limit: Int = PAST_PAGE_SIZE, offset: Int = 0): List<FamilyEvent>
    suspend fun event(id: Long): FamilyEvent
    suspend fun create(draft: EventDraft): FamilyEvent
    suspend fun update(original: FamilyEvent, draft: EventDraft): FamilyEvent
    suspend fun delete(id: Long)
}

@Singleton
class EventsApi @Inject constructor(
    private val server: PicfortServerClient,
    private val json: Json,
) : EventsBackend {

    override suspend fun upcoming(after: LocalDate): List<FamilyEvent> {
        val url = eventsUrl().addQueryParameter("after", after.toString()).build()
        val raw = call(Request.Builder().url(url).build())
        return json.decodeFromString<List<EventDto>>(raw).map { it.toEvent() }
    }

    override suspend fun past(before: LocalDate, limit: Int, offset: Int): List<FamilyEvent> {
        val url = eventsUrl()
            .addQueryParameter("before", before.toString())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .build()
        val raw = call(Request.Builder().url(url).build())
        return json.decodeFromString<List<EventDto>>(raw).map { it.toEvent() }
    }

    override suspend fun event(id: Long): FamilyEvent =
        eventCall(Request.Builder().url(eventUrl(id)).build(), id)

    override suspend fun create(draft: EventDraft): FamilyEvent {
        val payload = buildJsonObject {
            put("title", draft.normalizedTitle)
            put("start_date", draft.startDate.toString())
            draft.normalizedEndDate?.let { put("end_date", it.toString()) }
            draft.startTime?.let { put("start_time", it.format(TIME_FORMATTER)) }
            draft.normalizedLocation?.let { put("location", it) }
            draft.normalizedNote?.let { put("note", it) }
            draft.albumId?.let { put("album_id", it) }
        }
        return eventCall(Request.Builder().url(eventsUrl().build()).post(body(payload)).build())
    }

    override suspend fun update(original: FamilyEvent, draft: EventDraft): FamilyEvent {
        val patch = buildEventPatchJson(original, draft)
        if (patch.isEmpty()) return original
        return eventCall(Request.Builder().url(eventUrl(original.id)).patch(body(patch)).build(), original.id)
    }

    override suspend fun delete(id: Long) {
        call(Request.Builder().url(eventUrl(id)).delete().build(), id)
    }

    private fun eventsUrl(): HttpUrl.Builder = server.base().newBuilder().addPathSegment("events")
    private fun eventUrl(id: Long): HttpUrl = eventsUrl().addPathSegment("$id").build()

    private fun notFound(eventId: Long?) = {
        if (eventId == null) PicfortServerException.Outdated("events")
        else PicfortServerException.Gone("This event no longer exists")
    }

    private suspend fun call(request: Request, eventId: Long? = null): String =
        server.execute(request, notFound(eventId)).body

    private suspend fun eventCall(request: Request, eventId: Long? = null): FamilyEvent =
        json.decodeFromString<EventDto>(call(request, eventId)).toEvent()

    private fun body(obj: JsonObject) = obj.toString().toRequestBody(JSON)

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
