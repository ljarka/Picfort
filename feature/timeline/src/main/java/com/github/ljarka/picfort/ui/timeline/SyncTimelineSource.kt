package com.github.ljarka.picfort.ui.timeline

import androidx.room.withTransaction
import com.github.ljarka.picfort.cleanup.PhotoInsightsClient
import com.github.ljarka.picfort.collections.CollectionsService
import com.github.ljarka.picfort.collections.MetadataSearchRequest
import com.github.ljarka.picfort.collections.OcrSearchRequest
import com.github.ljarka.picfort.core.database.AiLabelEntity
import com.github.ljarka.picfort.core.database.ImagesDatabase
import com.github.ljarka.picfort.core.database.RemoteAssetEntity
import com.github.ljarka.picfort.core.database.RemoteBestEntity
import com.github.ljarka.picfort.core.database.RemoteDocumentEntity
import com.github.ljarka.picfort.core.network.AccessTokenStore
import com.github.ljarka.picfort.core.network.ServerUrlStore
import com.github.ljarka.picfort.sync.SyncEngine
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

private val DOCUMENT_OCR_TERMS = listOf("PARAGON", "NIP", "FAKTURA", "SUMA PLN", "VAT", "UMOWA", "PESEL")
private const val OCR_REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1000

/** Tag the Picfort server gives its frame-worthy picks (ADR 0019); feeds "Best of the month" (T-094). */
const val BEST_TAG_VALUE = "ai/best/frame-worthy"
private const val BEST_PAGE_SIZE = 1000
private const val BEST_MAX_PAGES = 50

/**
 * The timeline's Immich assets: a local copy kept up to date by the sync stream (ADR 0005).
 * Month keys are midnight UTC on the 1st. Servers older than v2 are not supported.
 */
@Singleton
class SyncTimelineSource @Inject constructor(
    private val engine: SyncEngine,
    private val database: ImagesDatabase,
    private val collections: Provider<CollectionsService>,
    private val photoInsights: Provider<PhotoInsightsClient>,
    private val serverUrlStore: ServerUrlStore,
    private val accessTokenStore: AccessTokenStore,
) {
    private val dao = database.remoteAssetsDao()
    private var lastOcrRefreshAt = 0L

    /**
     * Server and login session. Checkpoints live on the server per session; the token is
     * hashed so it isn't stored a second time.
     */
    private fun owner(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(accessTokenStore.accessToken.toByteArray())
        return serverUrlStore.serverUrl + "|" + digest.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Syncs with the server (throws on network errors; the copy stays usable offline).
     * Returns whether anything may have changed.
     */
    suspend fun refresh(): Boolean {
        val result = engine.sync(owner())
        val now = System.currentTimeMillis()
        if (result.wasReset || lastOcrRefreshAt == 0L || now - lastOcrRefreshAt >= OCR_REFRESH_INTERVAL_MS) {
            refreshDocumentsQuietly(now)
            refreshBestQuietly()
            refreshAiLabelsQuietly()
        }
        return result.wasReset || result.lines > 1 // a stream with no changes is just SyncCompleteV1
    }

    /** Refreshes OCR document ids (T-088); failures (e.g. older Immich without OCR) are ignored. */
    private suspend fun refreshDocumentsQuietly(now: Long) {
        try {
            val ids = LinkedHashSet<String>()
            val service = collections.get()
            for (term in DOCUMENT_OCR_TERMS) {
                val page = service.ocrSearch(OcrSearchRequest(ocr = term, type = "IMAGE", size = 1000))
                page.assets.items.forEach { ids += it.id }
            }
            database.withTransaction {
                dao.clearDocuments()
                if (ids.isNotEmpty()) dao.upsertDocuments(ids.map(::RemoteDocumentEntity))
            }
            lastOcrRefreshAt = now
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Optional signal: keep whatever was cached previously.
        }
    }

    /**
     * Refreshes the ids tagged [BEST_TAG_VALUE] (T-094). No such tag (server tagging off) empties
     * the cache; any failure keeps what was cached before.
     */
    private suspend fun refreshBestQuietly() {
        try {
            val service = collections.get()
            val tagId = service.tags().firstOrNull { it.value.equals(BEST_TAG_VALUE, ignoreCase = true) }?.id
            val ids = LinkedHashSet<String>()
            if (tagId != null) {
                var page = 1
                while (page <= BEST_MAX_PAGES) {
                    val result = service.metadataSearch(
                        MetadataSearchRequest(
                            personIds = emptyList(),
                            page = page,
                            size = BEST_PAGE_SIZE,
                            order = "desc",
                            type = "IMAGE",
                            tagIds = listOf(tagId),
                        ),
                    ).assets
                    result.items.forEach { ids += it.id }
                    page = result.nextPage?.toIntOrNull() ?: break
                }
            }
            database.withTransaction {
                dao.clearBest()
                if (ids.isNotEmpty()) dao.upsertBest(ids.map(::RemoteBestEntity))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Optional signal: keep whatever was cached previously.
        }
    }

    /**
     * Refreshes the photo AI's labels from the Picfort server (T-098, T-099). No server, or one
     * without photo insights: keeps the previous cache (then the guesses decide).
     */
    private suspend fun refreshAiLabelsQuietly() {
        val client = photoInsights.get()
        if (!client.isConfigured) return
        try {
            val labels = client.labels()
            database.withTransaction {
                dao.clearAiLabels()
                labels.chunked(1000).forEach { chunk ->
                    dao.upsertAiLabels(chunk.map { AiLabelEntity(it.assetId, it.category, it.sensitive) })
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Optional signal: keep whatever was cached previously.
        }
    }

    /** The photo AI's labels, split for the filters (T-098, T-099). */
    fun aiLabels(): Flow<AiLabels> = dao.aiLabelsFlow().map(::aiLabelsOf)

    /** Immich asset ids tagged [BEST_TAG_VALUE] (T-094). */
    fun bestAssetIds(): Flow<Set<String>> = dao.bestAssetIdsFlow().map { it.toSet() }

    /** The timeline's Immich assets, newest first; follows every change to the copy. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun assets(): Flow<List<RemoteAssetEntity>> =
        dao.syncStateFlow().map { it?.userId.orEmpty() }.distinctUntilChanged()
            .flatMapLatest { userId -> dao.timelineAll(userId) }

    /** Immich asset ids whose synced EXIF has no camera make/model (T-088). */
    fun nonCameraAssetIds(): Flow<Set<String>> = dao.nonCameraAssetIdsFlow().map { it.toSet() }

    /** Immich asset ids matched by document/receipt OCR terms (T-088). */
    fun documentAssetIds(): Flow<Set<String>> = dao.documentAssetIdsFlow().map { it.toSet() }
}
