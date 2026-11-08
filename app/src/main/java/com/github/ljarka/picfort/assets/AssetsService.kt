package com.github.ljarka.picfort.assets

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Path

@Serializable
data class AssetInfo(
    val id: String,
    val type: String = "IMAGE", // IMAGE, VIDEO, AUDIO, OTHER
    val originalFileName: String = "",
    val originalMimeType: String? = null,
)

@Serializable
data class AssetBulkDeleteRequest(
    val ids: List<String>,
    /** false = move to Immich's trash (recoverable). */
    val force: Boolean = false,
)

interface AssetsService {

    @GET("/api/assets/{id}")
    suspend fun getAssetInfo(@Path("id") id: String): AssetInfo

    @HTTP(method = "DELETE", path = "/api/assets", hasBody = true)
    suspend fun deleteAssets(@Body body: AssetBulkDeleteRequest)
}
