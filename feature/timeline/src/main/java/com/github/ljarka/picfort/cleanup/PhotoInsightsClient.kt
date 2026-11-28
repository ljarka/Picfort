package com.github.ljarka.picfort.cleanup

import com.github.ljarka.picfort.core.network.PicfortServerClient
import com.github.ljarka.picfort.core.network.PicfortServerException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class CleanupFilter(val label: String, val queryParam: String) {
    All("All", "all"),
    LowQuality("Low quality", "low_quality"),
    Documents("Screenshots & docs", "documents"),
}

@Serializable
data class PhotoInsightsStatus(
    val enabled: Boolean = false,
    val model: String = "",
    @SerialName("analyzed_count") val analyzedCount: Int = 0,
    @SerialName("cleanup_count") val cleanupCount: Int = 0,
    val running: Boolean = false,
)

@Serializable
data class CleanupCandidateDto(
    @SerialName("asset_id") val assetId: String,
    val scene: String = "",
    val category: String = "photo",
    @SerialName("is_nature") val isNature: Boolean = false,
    val quality: Int = 3,
    val issues: List<String> = emptyList(),
    @SerialName("cleanup_reason") val cleanupReason: String = "",
    @SerialName("analyzed_at") val analyzedAt: String = "",
)

@Serializable
private data class CleanupResponse(val items: List<CleanupCandidateDto> = emptyList())

/**
 * Reads AI photo insights stored on the Picfort server (ADR 0019, T-089).
 * The app never talks to Ollama directly: it only displays and acts on stored candidates.
 */
@Singleton
class PhotoInsightsClient @Inject constructor(private val server: PicfortServerClient, private val json: Json) {
    val isConfigured: Boolean get() = server.isConfigured

    suspend fun status(): PhotoInsightsStatus? {
        if (!server.isConfigured) return null
        return runCatching {
            val url = server.base().newBuilder()
                .addPathSegment("photo-insights")
                .addPathSegment("status")
                .build()
            val reply = server.execute(Request.Builder().url(url).get().build()) {
                PicfortServerException.Other("Photo insights not available on this server")
            }
            json.decodeFromString<PhotoInsightsStatus>(reply.body)
        }.getOrNull()
    }

    suspend fun cleanup(filter: CleanupFilter = CleanupFilter.All, limit: Int = 100): List<CleanupCandidateDto> {
        val url = server.base().newBuilder()
            .addPathSegment("photo-insights")
            .addPathSegment("cleanup")
            .addQueryParameter("filter", filter.queryParam)
            .addQueryParameter("limit", limit.toString())
            .build()
        val reply = server.execute(Request.Builder().url(url).get().build()) {
            PicfortServerException.Other("Photo insights not available on this server")
        }
        return json.decodeFromString<CleanupResponse>(reply.body).items
    }

    suspend fun dismiss(assetId: String) {
        val url = server.base().newBuilder()
            .addPathSegment("photo-insights")
            .addPathSegment(assetId)
            .addPathSegment("dismiss")
            .build()
        val body = "{}".toRequestBody("application/json".toMediaType())
        server.execute(Request.Builder().url(url).post(body).build()) {
            PicfortServerException.Other("Photo insight not found")
        }
    }

    suspend fun deleteRecord(assetId: String) {
        val url = server.base().newBuilder()
            .addPathSegment("photo-insights")
            .addPathSegment(assetId)
            .build()
        server.execute(Request.Builder().url(url).delete().build()) {
            PicfortServerException.Other("Photo insight not found")
        }
    }
}
