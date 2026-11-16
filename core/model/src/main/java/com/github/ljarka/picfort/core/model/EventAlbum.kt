package com.github.ljarka.picfort.core.model

/** Immich album metadata needed to link, share, and preview albums on events (T-026). */
data class EventAlbum(
    val id: String,
    val name: String,
    val thumbnailUrl: String? = null,
    val assetCount: Int = 0,
    val ownerId: String? = null,
    val memberIds: Set<String> = emptySet(),
    val shared: Boolean = false,
)

/**
 * Cross-feature contract for reading, creating, and sharing Immich albums from `:feature:events`
 * without depending on `:feature:timeline`.
 */
interface EventAlbumsProvider {
    suspend fun albums(): List<EventAlbum>
    suspend fun album(id: String): EventAlbum?
    suspend fun createAlbum(name: String, memberIds: List<String>): EventAlbum
    suspend fun shareAlbum(id: String, memberIds: List<String>): EventAlbum
}
