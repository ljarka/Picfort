package com.github.ljarka.picfort.collections

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class AlbumDto(
    val id: String,
    val albumName: String = "",
    val albumThumbnailAssetId: String? = null,
    val assetCount: Int = 0,
    val albumUsers: List<AlbumUserDto> = emptyList(),
)

@Serializable
data class AlbumUserRef(val id: String)

/** Someone with access to an album; the owner has role "owner" (Immich 3). */
@Serializable
data class AlbumUserDto(val user: AlbumUserRef, val role: String = "")

/** The Immich user id of the album's owner, if the response names one. */
val AlbumDto.ownerId: String? get() = albumUsers.firstOrNull { it.role == "owner" }?.user?.id

/**
 * Only the owner can delete an album (Immich answers others with 403). With no id for me (Immich users
 * not synced yet) or no owner in the response the menu shows, and a refusal says why (T-070).
 */
fun AlbumDto.canDelete(myId: String): Boolean = myId.isBlank() || ownerId == null || ownerId == myId

@Serializable
data class CreateAlbumRequest(val albumName: String, val assetIds: List<String> = emptyList())

@Serializable
data class BulkIdsRequest(val ids: List<String>)

@Serializable
data class BulkIdResult(val id: String, val success: Boolean = false, val error: String? = null)

/** [personIds]: only photos with all of them (null is left out: the app's Json skips defaults). */
@Serializable
data class SmartSearchRequest(
    val query: String,
    val page: Int = 1,
    val size: Int = 100,
    val personIds: List<String>? = null,
    /** "IMAGE" or "VIDEO"; null for both (T-071). */
    val type: String? = null,
)

/** A face group in Immich ("people"); unnamed ones have an empty name. */
@Serializable
data class PersonDto(val id: String, val name: String = "", val isHidden: Boolean = false)

@Serializable
data class PeopleResponse(val people: List<PersonDto> = emptyList(), val hasNextPage: Boolean = false)

/** Photos with a person (T-052). No default values: the app's Json doesn't encode defaults. */
@Serializable
data class MetadataSearchRequest(
    val personIds: List<String>,
    val page: Int,
    val size: Int,
    val order: String,
    /** "IMAGE" or "VIDEO"; null (left out) for both (T-071). */
    val type: String? = null,
)

/** Search filter: everything, only photos or only videos (Immich asset type, T-071). */
enum class MediaFilter(val label: String, val immichType: String?) {
    All("All", null),
    Photos("Photos", "IMAGE"),
    Videos("Videos", "VIDEO"),
}

@Serializable
data class SearchAssetDto(
    val id: String,
    val type: String = "IMAGE",
    /** "H:MM:SS.ffffff" on older servers, milliseconds on newer ones. */
    val duration: JsonElement? = null,
)

@Serializable
data class SearchAssetsPage(
    val items: List<SearchAssetDto> = emptyList(),
    /** Next page number as a string, or null on the last page. */
    val nextPage: String? = null,
)

@Serializable
data class SearchResponse(val assets: SearchAssetsPage = SearchAssetsPage())

interface CollectionsService {
    @GET("/api/albums")
    suspend fun albums(): List<AlbumDto>

    @POST("/api/albums")
    suspend fun createAlbum(@Body body: CreateAlbumRequest): AlbumDto

    /** The album only: its photos and videos stay in Immich. 204 No Content. */
    @DELETE("/api/albums/{id}")
    suspend fun deleteAlbum(@Path("id") albumId: String): Response<Unit>

    @PUT("/api/albums/{id}/assets")
    suspend fun addToAlbum(@Path("id") albumId: String, @Body body: BulkIdsRequest): List<BulkIdResult>

    /** CLIP "smart" search: natural-language queries like "dog on the beach". */
    @POST("/api/search/smart")
    suspend fun smartSearch(@Body body: SmartSearchRequest): SearchResponse

    /** Immich's order: favourites first, then those in the most photos. */
    @GET("/api/people")
    suspend fun people(
        @Query("page") page: Int,
        @Query("size") size: Int = 500,
        @Query("withHidden") withHidden: Boolean = false,
    ): PeopleResponse

    @POST("/api/search/metadata")
    suspend fun metadataSearch(@Body body: MetadataSearchRequest): SearchResponse
}
