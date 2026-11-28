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

/** One analyzed asset from `/photo-insights/labels`: `[id, category, sensitive 0/1]` (T-102). */
@Serializable
private data class LabelsResponse(val items: List<kotlinx.serialization.json.JsonArray> = emptyList())

data class AiLabel(val assetId: String, val category: String, val sensitive: Boolean)

/** Whether the photo AI covers the logged-in user's own library (T-104). */
@Serializable
data class MyLibraryStatus(
    @SerialName("ai_enabled") val aiEnabled: Boolean = false,
    val enabled: Boolean = false,
    /** The server's own IMMICH_API_KEY is this user's: covered without a key of their own. */
    @SerialName("via_server") val viaServer: Boolean = false,
    @SerialName("key_id") val keyId: String = "",
    val analyzed: Int = 0,
    val images: Int? = null,
    @SerialName("last_error") val lastError: String? = null,
)

@Serializable
private data class MyLibraryKey(@SerialName("api_key") val apiKey: String, @SerialName("key_id") val keyId: String)

@Serializable
private data class RemovedKey(@SerialName("key_id") val keyId: String = "")

@Serializable
private data class CleanupResponse(val items: List<CleanupCandidateDto> = emptyList())

@Serializable
data class AlbumSuggestionDto(
    @SerialName("cluster_key") val clusterKey: String,
    val title: String,
    val subtitle: String = "",
    @SerialName("cover_asset_id") val coverAssetId: String,
    @SerialName("asset_ids") val assetIds: List<String> = emptyList(),
    @SerialName("start_date") val startDate: String = "",
    @SerialName("end_date") val endDate: String = "",
)

@Serializable
private data class AlbumSuggestionsResponse(val items: List<AlbumSuggestionDto> = emptyList())

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

    private fun myLibraryUrl() = server.base().newBuilder()
        .addPathSegment("photo-insights")
        .addPathSegment("my-library")
        .build()

    /** The logged-in user's library on the photo AI (T-104); null when the server can't say. */
    suspend fun myLibrary(): MyLibraryStatus? {
        if (!server.isConfigured) return null
        return runCatching {
            val reply = server.execute(Request.Builder().url(myLibraryUrl()).get().build()) {
                PicfortServerException.Other("Photo insights not available on this server")
            }
            json.decodeFromString<MyLibraryStatus>(reply.body)
        }.getOrNull()
    }

    /** Hands the user's new Immich key to the server; throws with the server's reason when refused. */
    suspend fun addMyLibrary(apiKey: String, keyId: String): MyLibraryStatus {
        val body = json.encodeToString(MyLibraryKey.serializer(), MyLibraryKey(apiKey, keyId))
            .toRequestBody("application/json".toMediaType())
        val reply = server.execute(Request.Builder().url(myLibraryUrl()).put(body).build()) {
            PicfortServerException.Other("The Picfort server refused the key")
        }
        return json.decodeFromString<MyLibraryStatus>(reply.body)
    }

    /** Forgets the user's key on the server; returns its Immich key id ("" if unknown). */
    suspend fun removeMyLibrary(): String {
        val reply = server.execute(Request.Builder().url(myLibraryUrl()).delete().build()) {
            PicfortServerException.Other("No key stored for you")
        }
        return json.decodeFromString<RemovedKey>(reply.body).keyId
    }

    /** Every asset the server's photo AI analyzed (T-102, T-099); throws when unavailable. */
    suspend fun labels(): List<AiLabel> {
        val url = server.base().newBuilder()
            .addPathSegment("photo-insights")
            .addPathSegment("labels")
            .build()
        val reply = server.execute(Request.Builder().url(url).get().build()) {
            PicfortServerException.Other("Photo insights not available on this server")
        }
        return json.decodeFromString<LabelsResponse>(reply.body).items.mapNotNull { row ->
            val id = (row.getOrNull(0) as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
            val category = (row.getOrNull(1) as? kotlinx.serialization.json.JsonPrimitive)?.content ?: "photo"
            val sensitive = (row.getOrNull(2) as? kotlinx.serialization.json.JsonPrimitive)?.content == "1"
            AiLabel(id, category, sensitive)
        }
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

    /** Pending album suggestions grouped by location/date on the Picfort server (T-098). */
    suspend fun albumSuggestions(): List<AlbumSuggestionDto> {
        if (!server.isConfigured) return emptyList()
        return runCatching {
            val url = server.base().newBuilder()
                .addPathSegment("photo-insights")
                .addPathSegment("album-suggestions")
                .build()
            val reply = server.execute(Request.Builder().url(url).get().build()) {
                PicfortServerException.Other("Album suggestions not available")
            }
            json.decodeFromString<AlbumSuggestionsResponse>(reply.body).items
        }.getOrDefault(emptyList())
    }

    suspend fun dismissAlbumSuggestion(clusterKey: String) = postAlbumSuggestionAction(clusterKey, "dismiss")

    suspend fun markAlbumSuggestionCreated(clusterKey: String) = postAlbumSuggestionAction(clusterKey, "created")

    private suspend fun postAlbumSuggestionAction(clusterKey: String, action: String) {
        val url = server.base().newBuilder()
            .addPathSegment("photo-insights")
            .addPathSegment("album-suggestions")
            .addPathSegment(clusterKey)
            .addPathSegment(action)
            .build()
        val body = "{}".toRequestBody("application/json".toMediaType())
        server.execute(Request.Builder().url(url).post(body).build()) {
            PicfortServerException.Other("Album suggestion not found")
        }
    }
}
