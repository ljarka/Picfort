package com.github.ljarka.picfort.ui.family

import com.github.ljarka.picfort.core.network.PicfortServerClient
import com.github.ljarka.picfort.core.network.PicfortServerException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class FamilyProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    val hidden: Boolean? = null,
    val manual: Boolean = false,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("no_photo") val noPhoto: Boolean = false,
    @SerialName("updated_by") val updatedBy: String = "",
    val updated: String = "",
)

@Serializable
data class MyPeopleDto(val people: Map<String, String> = emptyMap())

@Serializable
data class FamilyProfilesResponseDto(
    val profiles: List<FamilyProfileDto> = emptyList(),
)

data class FamilyProfile(
    val userId: String,
    val displayName: String? = null,
    /** Absolute URL on the Picfort server, or null. */
    val photoUrl: String? = null,
    val hidden: Boolean? = null,
    val manual: Boolean = false,
    val birthDate: String? = null,
    /** Initials instead of the Immich account picture ("Clear"). */
    val noPhoto: Boolean = false,
)

internal fun FamilyProfileDto.toProfile(base: HttpUrl) = FamilyProfile(
    userId = userId,
    displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
    photoUrl = photoUrl?.let { base.resolve(it)?.toString() },
    hidden = hidden,
    manual = manual,
    birthDate = birthDate,
    noPhoto = noPhoto,
)

interface FamilyBackend {
    val isConfigured: Boolean

    /** Returns `(profiles, rawJson)`. */
    suspend fun profiles(): Pair<List<FamilyProfile>, String>

    fun decodeProfiles(raw: String): List<FamilyProfile>

    /**
     * Updates a user's profile (`PATCH /family/<userId>`).
     * When [includeHidden] is false, `hidden` is omitted from the JSON body so an unset default stays unset.
     */
    suspend fun update(
        userId: String,
        displayName: String?,
        hidden: Boolean? = null,
        includeHidden: Boolean = false,
        birthDate: String? = null,
        includeBirthDate: Boolean = false,
        today: String? = null,
        /** Sent when not null. */
        noPhoto: Boolean? = null,
    ): FamilyProfile

    suspend fun addMember(
        displayName: String,
        birthDate: String? = null,
        today: String? = null,
    ): FamilyProfile

    suspend fun deleteMember(userId: String)

    suspend fun uploadPhoto(userId: String, photoJpeg: ByteArray): FamilyProfile

    suspend fun deletePhoto(userId: String)

    /** My links from members to my Immich people (T-052): member id → person id. */
    suspend fun myPeople(): Map<String, String> = emptyMap()

    suspend fun linkPerson(memberId: String, personId: String) = Unit
}

@Singleton
class FamilyApi @Inject constructor(
    private val server: PicfortServerClient,
    private val json: Json,
) : FamilyBackend {

    override val isConfigured: Boolean get() = server.isConfigured

    override suspend fun profiles(): Pair<List<FamilyProfile>, String> {
        val base = server.base()
        val url = base.newBuilder().addPathSegment("family").build()
        val raw = execute(Request.Builder().url(url).get().build())
        return decodeProfiles(raw, base) to raw
    }

    override fun decodeProfiles(raw: String): List<FamilyProfile> {
        val base = server.base()
        return decodeProfiles(raw, base)
    }

    private fun decodeProfiles(raw: String, base: HttpUrl): List<FamilyProfile> =
        json.decodeFromString<FamilyProfilesResponseDto>(raw).profiles.map { it.toProfile(base) }

    override suspend fun update(
        userId: String,
        displayName: String?,
        hidden: Boolean?,
        includeHidden: Boolean,
        birthDate: String?,
        includeBirthDate: Boolean,
        today: String?,
        noPhoto: Boolean?,
    ): FamilyProfile {
        val base = server.base()
        val url = base.newBuilder().addPathSegment("family").addPathSegment(userId).build()
        val payload = buildJsonObject {
            put("display_name", displayName?.trim()?.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)
            if (includeHidden) {
                put("hidden", hidden?.let(::JsonPrimitive) ?: JsonNull)
            }
            if (includeBirthDate) {
                put("birth_date", birthDate?.let(::JsonPrimitive) ?: JsonNull)
            }
            if (today != null) {
                put("today", JsonPrimitive(today))
            }
            if (noPhoto != null) {
                put("no_photo", JsonPrimitive(noPhoto))
            }
        }
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val raw = execute(Request.Builder().url(url).patch(body).build())
        return json.decodeFromString<FamilyProfileDto>(raw).toProfile(base)
    }

    override suspend fun addMember(
        displayName: String,
        birthDate: String?,
        today: String?,
    ): FamilyProfile {
        val base = server.base()
        val url = base.newBuilder().addPathSegment("family").addPathSegment("members").build()
        val payload = buildJsonObject {
            put("display_name", JsonPrimitive(displayName.trim()))
            if (birthDate != null) {
                put("birth_date", JsonPrimitive(birthDate))
            }
            if (today != null) {
                put("today", JsonPrimitive(today))
            }
        }
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val raw = execute(Request.Builder().url(url).post(body).build())
        return json.decodeFromString<FamilyProfileDto>(raw).toProfile(base)
    }

    override suspend fun deleteMember(userId: String) {
        val url = server.base().newBuilder().addPathSegment("family").addPathSegment(userId).build()
        execute(Request.Builder().url(url).delete().build())
    }

    override suspend fun uploadPhoto(userId: String, photoJpeg: ByteArray): FamilyProfile {
        val base = server.base()
        val url = base.newBuilder().addPathSegment("family").addPathSegment(userId).addPathSegment("photo").build()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("photo", "photo.jpg", photoJpeg.toRequestBody("image/jpeg".toMediaType()))
            .build()
        val raw = execute(Request.Builder().url(url).put(body).build())
        return json.decodeFromString<FamilyProfileDto>(raw).toProfile(base)
    }

    override suspend fun deletePhoto(userId: String) {
        val url = server.base().newBuilder().addPathSegment("family").addPathSegment(userId).addPathSegment("photo").build()
        execute(Request.Builder().url(url).delete().build())
    }

    override suspend fun myPeople(): Map<String, String> {
        val url = server.base().newBuilder().addPathSegment("family").addPathSegment("people").build()
        return json.decodeFromString<MyPeopleDto>(execute(Request.Builder().url(url).get().build())).people
    }

    override suspend fun linkPerson(memberId: String, personId: String) {
        val url = server.base().newBuilder().addPathSegment("family").addPathSegment(memberId)
            .addPathSegment("person").build()
        val body = buildJsonObject { put("person_id", JsonPrimitive(personId)) }.toString().toRequestBody(JSON_MEDIA_TYPE)
        execute(Request.Builder().url(url).put(body).build())
    }

    private suspend fun execute(request: Request): String = server.execute(request) {
        PicfortServerException.Outdated("family profiles")
    }.body

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
