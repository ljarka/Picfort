package com.github.ljarka.picfort.collections

import com.github.ljarka.picfort.ui.timeline.TimelineBucketsService
import com.github.ljarka.picfort.ui.timeline.parseImmichDuration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import retrofit2.HttpException
import retrofit2.Retrofit

/** An Immich asset in an album or search result. */
data class GridAsset(val id: String, val isVideo: Boolean = false, val durationMs: Long? = null)

data class SearchPage(val assets: List<GridAsset>, val nextPage: Int?)

/** Up to 5000 face groups; a library with more has them named mostly in the first ones anyway. */
private const val MAX_PEOPLE_PAGES = 10

/** A named person (face group) in Immich, for searching by people. */
data class SearchPerson(val id: String, val name: String)

@Singleton
class CollectionsRepository @Inject constructor(
    private val service: Provider<CollectionsService>,
    private val buckets: Provider<TimelineBucketsService>,
) {
    /** Lists the viewer pages through, keyed by source ("album:<id>", "search:<query>"). */
    private val lists = ConcurrentHashMap<String, List<GridAsset>>()

    fun list(source: String): List<GridAsset> = lists[source].orEmpty()

    fun putList(source: String, assets: List<GridAsset>) {
        lists[source] = assets
    }

    private val _deletedAlbums = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Ids of albums deleted in the app; the albums list drops them without a reload. */
    val deletedAlbums: SharedFlow<String> = _deletedAlbums.asSharedFlow()

    suspend fun albums(): List<AlbumDto> = service.get().albums().sortedBy { it.albumName.lowercase() }

    /**
     * Album contents, newest first. Uses the timeline bucket API with albumId,
     * which works on every Immich version (album responses stopped including
     * assets in v3).
     */
    suspend fun albumAssets(albumId: String): List<GridAsset> = coroutineScope {
        val api = buckets.get()
        val months = api.getTimeBuckets(albumId = albumId, visibility = null, withPartners = false)
        val limit = Semaphore(4)
        months.map { month ->
            async {
                limit.withPermit {
                    val b = api.getBucket(
                        timeBucket = month.timeBucket,
                        albumId = albumId,
                        visibility = null,
                        withPartners = false,
                    )
                    b.id.mapIndexed { i, id ->
                        GridAsset(
                            id = id,
                            isVideo = b.isImage.getOrNull(i) == false,
                            durationMs = parseSearchDuration(b.duration.getOrNull(i)),
                        )
                    }
                }
            }
        }.awaitAll().flatten()
    }

    /**
     * Search: by text (CLIP), with the [personIds] in it (all of them), or only by people and/or
     * [media] type, newest first. At least one of them must be given.
     */
    suspend fun search(
        query: String,
        page: Int,
        personIds: Set<String> = emptySet(),
        media: MediaFilter = MediaFilter.All,
    ): SearchPage {
        val text = query.trim()
        val response = if (text.isEmpty()) {
            service.get().metadataSearch(
                MetadataSearchRequest(
                    personIds.toList(),
                    page = page,
                    size = 100,
                    order = "desc",
                    type = media.immichType,
                ),
            )
        } else {
            service.get().smartSearch(
                SmartSearchRequest(
                    text,
                    page,
                    personIds = personIds.toList().ifEmpty {
                        null
                    },
                    type = media.immichType,
                ),
            )
        }.assets
        return SearchPage(
            assets = response.items.map { item ->
                GridAsset(item.id, isVideo = item.type == "VIDEO", durationMs = parseSearchDuration(item.duration))
            },
            nextPage = response.nextPage?.toIntOrNull(),
        )
    }

    /** Named, visible people in my Immich library, in Immich's order (most photos first). */
    suspend fun people(): List<SearchPerson> {
        val all = ArrayList<PersonDto>()
        var page = 1
        do {
            val response = service.get().people(page)
            all += response.people
            page++
        } while (response.hasNextPage && page <= MAX_PEOPLE_PAGES)
        return all.filter { !it.isHidden && it.name.isNotBlank() }.map { SearchPerson(it.id, it.name.trim()) }
    }

    /** Photos of one person in my Immich library, newest first (T-052). */
    suspend fun personAssets(personId: String, page: Int): SearchPage {
        val response = service.get()
            .metadataSearch(MetadataSearchRequest(listOf(personId), page = page, size = 100, order = "desc")).assets
        return SearchPage(
            assets = response.items.map { item ->
                GridAsset(item.id, isVideo = item.type == "VIDEO", durationMs = parseSearchDuration(item.duration))
            },
            nextPage = response.nextPage?.toIntOrNull(),
        )
    }

    suspend fun createAlbum(name: String, assetIds: List<String>): AlbumDto =
        service.get().createAlbum(CreateAlbumRequest(name.trim(), assetIds))

    /** Deletes the album (not its photos); throws [HttpException] when refused (403: not the owner). */
    suspend fun deleteAlbum(albumId: String) {
        val response = service.get().deleteAlbum(albumId)
        if (!response.isSuccessful) throw HttpException(response)
        lists.remove("album:$albumId")
        _deletedAlbums.emit(albumId)
    }

    /** Returns how many were added (already-present ones count as not added). */
    suspend fun addToAlbum(albumId: String, assetIds: List<String>): Int =
        service.get().addToAlbum(albumId, BulkIdsRequest(assetIds)).count { it.success }
}

/** Search and album buckets: "0:00:12.500000" (Immich < 3) or 12500 (milliseconds, Immich 3+). */
fun parseSearchDuration(value: JsonElement?): Long? = (value as? JsonPrimitive)?.let { p ->
    if (p.isString) parseImmichDuration(p.content) else p.longOrNull?.takeIf { it > 0 }
}

@Module
@InstallIn(SingletonComponent::class)
class CollectionsModule {
    @Provides
    fun collectionsService(retrofit: Retrofit): CollectionsService = retrofit.create(CollectionsService::class.java)
}
