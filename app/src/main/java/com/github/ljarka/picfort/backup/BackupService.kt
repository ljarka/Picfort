package com.github.ljarka.picfort.backup

import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap

@Serializable
data class ServerVersion(val major: Int, val minor: Int = 0, val patch: Int = 0)

@Serializable
data class BulkUploadCheckItem(val id: String, val checksum: String)

@Serializable
data class BulkUploadCheckRequest(val assets: List<BulkUploadCheckItem>)

@Serializable
data class BulkUploadCheckResult(
    val id: String,
    val action: String, // accept | reject
    val reason: String? = null, // duplicate | unsupported-format
    val assetId: String? = null,
    val isTrashed: Boolean = false,
)

@Serializable
data class BulkUploadCheckResponse(val results: List<BulkUploadCheckResult>)

@Serializable
data class UploadResponse(val id: String, val status: String = "created")

interface BackupService {

    @GET("/api/server/version")
    suspend fun version(): ServerVersion

    @POST("/api/assets/bulk-upload-check")
    suspend fun bulkUploadCheck(@Body body: BulkUploadCheckRequest): BulkUploadCheckResponse

    /** 201 = created, 200 = Immich already had it (status "duplicate"). */
    @Multipart
    @POST("/api/assets")
    suspend fun upload(
        @Header("x-immich-checksum") checksum: String,
        @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Part file: MultipartBody.Part,
    ): UploadResponse
}
