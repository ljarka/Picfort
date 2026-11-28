package com.github.ljarka.picfort.collections

import com.github.ljarka.picfort.memories.MemoryDto
import retrofit2.Response

/**
 * Immich, as the collections tests need it: empty answers by default. A test overrides only the
 * calls it checks, so a new [CollectionsService] method is added here once, not in every test.
 */
open class FakeCollectionsService : CollectionsService {
    override suspend fun albums(): List<AlbumDto> = emptyList()
    override suspend fun album(albumId: String): AlbumDto = error("unused")
    override suspend fun createAlbum(body: CreateAlbumRequest): AlbumDto = error("unused")
    override suspend fun shareAlbum(albumId: String, body: ShareAlbumUsersRequest): AlbumDto = error("unused")
    override suspend fun deleteAlbum(albumId: String): Response<Unit> = Response.success(204, Unit)
    override suspend fun addToAlbum(albumId: String, body: BulkIdsRequest): List<BulkIdResult> = emptyList()
    override suspend fun smartSearch(body: SmartSearchRequest) = SearchResponse()
    override suspend fun people(page: Int, size: Int, withHidden: Boolean) = PeopleResponse()
    override suspend fun metadataSearch(body: MetadataSearchRequest) = SearchResponse()
    override suspend fun ocrSearch(body: OcrSearchRequest) = SearchResponse()
    override suspend fun tags(): List<TagDto> = emptyList()
    override suspend fun memories(isUpcoming: Boolean, isTrashed: Boolean): List<MemoryDto> = emptyList()
    override suspend fun createApiKey(body: ApiKeyCreateRequest): ApiKeyCreated = error("unused")
    override suspend fun deleteApiKey(id: String): Response<Unit> = Response.success(204, Unit)
    override suspend fun deleteTag(tagId: String): Response<Unit> = Response.success(204, Unit)
}
