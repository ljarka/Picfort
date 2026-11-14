package com.github.ljarka.picfort.ui.school

import com.github.ljarka.picfort.core.network.PicfortServerClient
import com.github.ljarka.picfort.core.network.PicfortServerException
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class PeriodDto(
    val number: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
)

@Serializable
data class EntryDto(
    val id: Long,
    val kind: String,
    val weekday: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    val title: String,
    val period: Int? = null,
    val place: String? = null,
    val teacher: String? = null,
    val note: String? = null,
)

@Serializable
data class ChildDto(
    val id: Long,
    val name: String,
    val color: String,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("my_person_id") val myPersonId: String? = null,
    @SerialName("immich_user_id") val memberId: String? = null,
    val periods: List<PeriodDto> = emptyList(),
    val entries: List<EntryDto> = emptyList(),
)

@Serializable
data class SchoolDto(val children: List<ChildDto> = emptyList())

private fun time(text: String): LocalTime = LocalTime.parse(text)
private fun LocalTime.wire(): String = "%02d:%02d".format(hour, minute)

/** [base] resolves the server's relative photo path; null keeps it as is (offline copy). */
fun ChildDto.toChild(base: HttpUrl?): Child = Child(
    id = id,
    name = name,
    color = color,
    photoUrl = photoUrl?.let { base?.resolve(it)?.toString() ?: it },
    myPersonId = myPersonId,
    memberId = memberId,
    periods = periods.map { Period(it.number, time(it.startTime), time(it.endTime)) },
    entries = entries.map {
        SchoolEntry(
            id = it.id,
            kind = EntryKind.fromWire(it.kind),
            weekday = DayOfWeek.of(it.weekday),
            start = time(it.startTime),
            end = time(it.endTime),
            title = it.title,
            period = it.period,
            place = it.place,
            teacher = it.teacher,
            note = it.note,
        )
    },
)

/** A lesson or activity as the form saves it. A lesson with [period] takes the bell schedule's times. */
data class EntryDraft(
    val kind: EntryKind,
    val weekday: DayOfWeek,
    val title: String,
    val period: Int? = null,
    val start: LocalTime? = null,
    val end: LocalTime? = null,
    val place: String? = null,
    val teacher: String? = null,
    val note: String? = null,
)

/** The school timetables on the Picfort server (T-028). Changes answer with the changed child. */
interface SchoolBackend {
    /** The children and the raw JSON, for the offline copy. */
    suspend fun children(): Pair<List<Child>, String>
    fun decode(raw: String): List<Child>

    /** [memberId]: the family member the child is. */
    suspend fun addChild(name: String, color: String, memberId: String?): Child
    suspend fun deleteChild(id: Long)
    suspend fun setPeriods(childId: Long, periods: List<Period>): Child
    suspend fun copyPeriods(childId: Long, fromChild: Long): Child
    suspend fun addEntry(childId: Long, draft: EntryDraft)
    suspend fun updateEntry(childId: Long, entryId: Long, draft: EntryDraft)
    suspend fun deleteEntry(childId: Long, entryId: Long)
    suspend fun copyDay(childId: Long, from: DayOfWeek, to: DayOfWeek): Child
}

@Singleton
class SchoolApi @Inject constructor(private val server: PicfortServerClient, private val json: Json) : SchoolBackend {

    override suspend fun children(): Pair<List<Child>, String> {
        val raw = server.execute(Request.Builder().url(school().build()).build(), notFound(null)).body
        return decode(raw, server.base()) to raw
    }

    override fun decode(raw: String): List<Child> = decode(raw, runCatching { server.base() }.getOrNull())

    private fun decode(raw: String, base: HttpUrl?) = json.decodeFromString<SchoolDto>(raw).children.map {
        it.toChild(base)
    }

    override suspend fun addChild(name: String, color: String, memberId: String?) = childCall(
        Request.Builder().url(school().addPathSegment("children").build()).post(
            body(
                buildJsonObject {
                    put("name", name)
                    put("color", color)
                    memberId?.let { put("immich_user_id", it) }
                },
            ),
        ).build(),
    )

    override suspend fun deleteChild(id: Long) {
        call(Request.Builder().url(child(id).build()).delete().build(), id)
    }

    override suspend fun setPeriods(childId: Long, periods: List<Period>): Child {
        val array = buildJsonArray {
            periods.forEach { p ->
                add(
                    buildJsonObject {
                        put("number", p.number)
                        put("start_time", p.start.wire())
                        put("end_time", p.end.wire())
                    },
                )
            }
        }
        return childCall(
            Request.Builder().url(child(childId).addPathSegment("periods").build()).put(body(array)).build(),
        )
    }

    override suspend fun copyPeriods(childId: Long, fromChild: Long) = childCall(
        Request.Builder().url(child(childId).addPathSegment("copy-periods").build())
            .post(body(buildJsonObject { put("from_child", fromChild) })).build(),
    )

    override suspend fun addEntry(childId: Long, draft: EntryDraft) {
        call(
            Request.Builder().url(child(childId).addPathSegment("entries").build()).post(body(draft.wire())).build(),
            childId,
        )
    }

    override suspend fun updateEntry(childId: Long, entryId: Long, draft: EntryDraft) {
        val url = child(childId).addPathSegment("entries").addPathSegment("$entryId").build()
        call(Request.Builder().url(url).patch(body(draft.wire())).build(), childId)
    }

    override suspend fun deleteEntry(childId: Long, entryId: Long) {
        val url = child(childId).addPathSegment("entries").addPathSegment("$entryId").build()
        call(Request.Builder().url(url).delete().build(), childId)
    }

    override suspend fun copyDay(childId: Long, from: DayOfWeek, to: DayOfWeek) = childCall(
        Request.Builder().url(child(childId).addPathSegment("copy-day").build()).post(
            body(
                buildJsonObject {
                    put("from", from.value)
                    put("to", to.value)
                    put("kinds", JsonArray(listOf(JsonPrimitive(EntryKind.LESSON.wire))))
                },
            ),
        ).build(),
    )

    /** Every field, `null` clearing the optional ones; times only without a period. */
    private fun EntryDraft.wire() = buildJsonObject {
        put("kind", kind.wire)
        put("weekday", weekday.value)
        put("title", title.trim())
        val byPeriod = kind == EntryKind.LESSON && period != null
        put("period", if (byPeriod) JsonPrimitive(period) else JsonNull)
        if (!byPeriod) {
            put("start_time", start?.wire())
            put("end_time", end?.wire())
        }
        put("place", place.orNullIfBlank())
        put("teacher", if (kind == EntryKind.LESSON) teacher.orNullIfBlank() else JsonNull)
        put("note", note.orNullIfBlank())
    }

    private fun String?.orNullIfBlank(): JsonElement = this?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive)
        ?: JsonNull

    private fun school(): HttpUrl.Builder = server.base().newBuilder().addPathSegment("school")
    private fun child(id: Long) = school().addPathSegment("children").addPathSegment("$id")

    private fun notFound(childId: Long?) = {
        if (childId == null) {
            PicfortServerException.Outdated("school timetables")
        } else {
            PicfortServerException.Gone("Someone else changed this timetable")
        }
    }

    private suspend fun call(request: Request, childId: Long? = -1) = server.execute(request, notFound(childId)).body

    private suspend fun childCall(request: Request) =
        json.decodeFromString<ChildDto>(call(request)).toChild(server.base())

    private fun body(element: JsonElement) = element.toString().toRequestBody(JSON)

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
