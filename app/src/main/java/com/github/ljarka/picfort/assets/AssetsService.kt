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
    val fileCreatedAt: String? = null,
    val exifInfo: ExifInfo? = null,
    val people: List<PersonInfo> = emptyList(),
)

/** Numbers are Double because older servers send e.g. 4000.0 for integers. */
@Serializable
data class ExifInfo(
    val make: String? = null,
    val model: String? = null,
    val lensModel: String? = null,
    val fNumber: Double? = null,
    val exposureTime: String? = null,
    val focalLength: Double? = null,
    val iso: Double? = null,
    val exifImageWidth: Double? = null,
    val exifImageHeight: Double? = null,
    val fileSizeInByte: Long? = null,
    val dateTimeOriginal: String? = null,
    val timeZone: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
)

@Serializable
data class PersonInfo(val name: String = "")

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
