package com.github.ljarka.picfort.collections

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

@Serializable
data class AlbumDto(
    val id: String,
    val albumName: String = "",
    val albumThumbnailAssetId: String? = null,
    val assetCount: Int = 0,
)

@Serializable
data class CreateAlbumRequest(val albumName: String, val assetIds: List<String> = emptyList())

@Serializable
data class BulkIdsRequest(val ids: List<String>)

@Serializable
data class BulkIdResult(val id: String, val success: Boolean = false, val error: String? = null)

@Serializable
data class SmartSearchRequest(val query: String, val page: Int = 1, val size: Int = 100)

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

    @PUT("/api/albums/{id}/assets")
    suspend fun addToAlbum(@Path("id") albumId: String, @Body body: BulkIdsRequest): List<BulkIdResult>

    /** CLIP "smart" search: natural-language queries like "dog on the beach". */
    @POST("/api/search/smart")
    suspend fun smartSearch(@Body body: SmartSearchRequest): SearchResponse
}
