package com.github.ljarka.picfort.collections

import com.github.ljarka.picfort.UrlProvider
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.model.EventAlbum
import com.github.ljarka.picfort.core.model.EventAlbumsProvider
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

@Singleton
class ImmichEventAlbumsProvider @Inject constructor(
    private val collections: CollectionsService,
    private val urlProvider: UrlProvider,
) : EventAlbumsProvider {

    override suspend fun albums(): List<EventAlbum> = collections.albums().map { it.toEventAlbum() }

    override suspend fun album(id: String): EventAlbum? = try {
        collections.album(id).toEventAlbum()
    } catch (e: HttpException) {
        if (e.code() in listOf(400, 403, 404)) null else throw e
    }

    override suspend fun createAlbum(name: String, memberIds: List<String>): EventAlbum {
        val users = memberIds.distinct().map { CreateAlbumUser(it, "editor") }.takeIf { it.isNotEmpty() }
        return collections.createAlbum(CreateAlbumRequest(albumName = name, albumUsers = users)).toEventAlbum()
    }

    override suspend fun shareAlbum(id: String, memberIds: List<String>): EventAlbum {
        val users = memberIds.distinct().map { CreateAlbumUser(it, "editor") }
        return collections.shareAlbum(id, ShareAlbumUsersRequest(users)).toEventAlbum()
    }

    private fun AlbumDto.toEventAlbum() = EventAlbum(
        id = id,
        name = albumName,
        thumbnailUrl = albumThumbnailAssetId?.let { urlProvider.getThumbnail(it, AssetType.REMOTE) },
        assetCount = assetCount,
        ownerId = ownerId ?: albumUsers.firstOrNull()?.user?.id,
        memberIds = albumUsers.mapTo(mutableSetOf()) { it.user.id },
        shared = shared,
    )
}
