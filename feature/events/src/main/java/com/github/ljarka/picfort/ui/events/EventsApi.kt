package com.github.ljarka.picfort.ui.events

import com.github.ljarka.picfort.core.model.EventRepeat
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.network.PicfortServerClient
import com.github.ljarka.picfort.core.network.PicfortServerException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

const val PAST_PAGE_SIZE = 50

/** Server `kind` of birthday events (T-036). Top level: a companion in EventDto would replace the serializer's. */
private const val KIND_BIRTHDAY = "birthday"
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
    val repeat: String? = null,
    @SerialName("repeat_until") val repeatUntil: String? = null,
    val kind: String? = null,
    @SerialName("member_id") val memberId: String? = null,
    @SerialName("occurrence_start") val occurrenceStart: String? = null,
    @SerialName("occurrence_end") val occurrenceEnd: String? = null,
    val turns: Int? = null,
    val members: List<String> = emptyList(),
) {
    /** Shown dates are the occurrence (T-035); the series' own dates stay for the form. */
    fun toEvent(): FamilyEvent {
        val seriesStart = LocalDate.parse(startDate)
        val seriesEnd = endDate?.let(LocalDate::parse)
        return FamilyEvent(
            id = id,
            title = title,
            startDate = occurrenceStart?.let(LocalDate::parse) ?: seriesStart,
            endDate = if (occurrenceStart != null) occurrenceEnd?.let(LocalDate::parse) else seriesEnd,
            startTime = startTime?.let { LocalTime.parse(it, TIME_FORMATTER) },
            location = location,
            note = note,
            albumId = albumId,
            createdBy = createdBy,
            created = created,
            updated = updated,
            repeat = EventRepeat.fromWire(repeat),
            repeatUntil = repeatUntil?.let(LocalDate::parse),
            seriesStartDate = seriesStart,
            seriesEndDate = seriesEnd,
            birthdayOf = memberId?.takeIf { kind == KIND_BIRTHDAY },
            turns = turns,
            memberIds = members,
        )
    }
}

data class EventDraft(
    val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val location: String? = null,
    val note: String? = null,
    val albumId: String? = null,
    val repeat: EventRepeat? = null,
    val repeatUntil: LocalDate? = null,
    /** Who the event is for (T-053). */
    val memberIds: List<String> = emptyList(),
) {
    val normalizedTitle: String get() = title.trim()

    /** An end of the repeat only makes sense with a repeat, and not before the first occurrence. */
    val normalizedRepeatUntil: LocalDate? get() = repeatUntil?.takeIf { repeat != null && !it.isBefore(startDate) }
    val normalizedEndDate: LocalDate? get() = endDate?.takeIf { it.isAfter(startDate) }
    val normalizedLocation: String? get() = location?.trim()?.ifEmpty { null }
    val normalizedNote: String? get() = note?.trim()?.ifEmpty { null }
}

/**
 * Builds a PATCH JSON object containing only the fields that changed from [original] (`null` clears
 * optional ones). Dates compare with the series' first occurrence, which is what the form edits.
 */
fun buildEventPatchJson(original: FamilyEvent, draft: EventDraft): JsonObject = buildJsonObject {
    if (draft.normalizedTitle != original.title) {
        put("title", draft.normalizedTitle)
    }
    if (draft.startDate != original.seriesStartDate) {
        put("start_date", draft.startDate.toString())
    }
    val origEnd = original.seriesEndDate?.takeIf { it.isAfter(original.seriesStartDate) }
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
    if (draft.repeat != original.repeat) {
        put("repeat", draft.repeat?.let { JsonPrimitive(it.wire) } ?: JsonNull)
    }
    if (draft.normalizedRepeatUntil != original.repeatUntil) {
        put("repeat_until", draft.normalizedRepeatUntil?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
    }
    if (draft.memberIds != original.memberIds) {
        put("members", JsonArray(draft.memberIds.map(::JsonPrimitive)))
    }
}

interface EventsBackend {
    suspend fun upcoming(after: LocalDate): List<FamilyEvent>
    suspend fun past(before: LocalDate, limit: Int = PAST_PAGE_SIZE, offset: Int = 0): List<FamilyEvent>

    /** The event; for a series, its occurrence that is ongoing or next [on] (the last one once it ended). */
    suspend fun event(id: Long, on: LocalDate): FamilyEvent
    suspend fun create(draft: EventDraft): FamilyEvent
    suspend fun update(original: FamilyEvent, draft: EventDraft): FamilyEvent
    suspend fun delete(id: Long)
}

@Singleton
class EventsApi @Inject constructor(private val server: PicfortServerClient, private val json: Json) : EventsBackend {

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

    override suspend fun event(id: Long, on: LocalDate): FamilyEvent {
        val url = eventsUrl().addPathSegment("$id").addQueryParameter("on", on.toString()).build()
        return eventCall(Request.Builder().url(url).build(), id)
    }

    override suspend fun create(draft: EventDraft): FamilyEvent {
        val payload = buildJsonObject {
            put("title", draft.normalizedTitle)
            put("start_date", draft.startDate.toString())
            draft.normalizedEndDate?.let { put("end_date", it.toString()) }
            draft.startTime?.let { put("start_time", it.format(TIME_FORMATTER)) }
            draft.normalizedLocation?.let { put("location", it) }
            draft.normalizedNote?.let { put("note", it) }
            draft.albumId?.let { put("album_id", it) }
            draft.repeat?.let { put("repeat", it.wire) }
            draft.normalizedRepeatUntil?.let { put("repeat_until", it.toString()) }
            if (draft.memberIds.isNotEmpty()) put("members", JsonArray(draft.memberIds.map(::JsonPrimitive)))
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
        if (eventId == null) {
            PicfortServerException.Outdated("events")
        } else {
            PicfortServerException.Gone("This event no longer exists")
        }
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
