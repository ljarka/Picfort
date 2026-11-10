package com.github.ljarka.picfort.sync

import com.github.ljarka.picfort.db.RemoteAssetEntity
import com.github.ljarka.picfort.db.RemoteStackEntity
import com.github.ljarka.picfort.db.UserEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.time.Instant

/**
 * The types we sync: the timeline's (ADR 0005) and the server's users (Family Wall members,
 * T-002). Order doesn't matter; the server has its own.
 */
val TIMELINE_SYNC_TYPES = listOf(
    "AuthUsersV2", "UsersV1", "PartnersV1", "AssetsV2", "PartnerAssetsV2", "StacksV1", "PartnerStacksV1",
)

/** One line of `/api/sync/stream`: `{"type": …, "data": {…}, "ack": "<type>|<id>[|<extra>]"}`. */
@Serializable
data class SyncLine(val type: String, val data: JsonObject = JsonObject(emptyMap()), val ack: String) {
    /** The checkpoint type; differs from [type] for backfill-complete lines (`SyncAckV1`). */
    val ackType: String get() = ack.substringBefore('|')
}

@Serializable
internal data class SyncAssetDto(
    val id: String,
    val ownerId: String,
    val localDateTime: String? = null,
    val fileCreatedAt: String? = null,
    val type: String,
    val visibility: String,
    val deletedAt: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val thumbhash: String? = null,
    val duration: Long? = null,
    val livePhotoVideoId: String? = null,
    val stackId: String? = null,
    val isFavorite: Boolean = false,
    val checksum: String,
)

@Serializable
internal data class SyncAssetDeleteDto(val assetId: String)

@Serializable
internal data class SyncStackDto(val id: String, val primaryAssetId: String, val ownerId: String)

@Serializable
internal data class SyncStackDeleteDto(val stackId: String)

@Serializable
internal data class SyncPartnerDto(val sharedById: String, val sharedWithId: String, val inTimeline: Boolean)

@Serializable
internal data class SyncPartnerDeleteDto(val sharedById: String, val sharedWithId: String)

@Serializable
internal data class SyncAuthUserDto(val id: String)

/** `UserV1`. Only the id is certain; the rest is optional so a field change can't stall the sync. */
@Serializable
internal data class SyncUserDto(
    val id: String,
    val name: String? = null,
    val email: String? = null,
    val avatarColor: String? = null,
    val hasProfileImage: Boolean = false,
    val profileChangedAt: String? = null,
    val deletedAt: String? = null,
)

@Serializable
internal data class SyncUserDeleteDto(val userId: String)

/** What one stream line asks the local copy to do. */
sealed interface SyncChange {
    data class UpsertAsset(val asset: RemoteAssetEntity) : SyncChange
    data class DeleteAsset(val id: String) : SyncChange
    data class UpsertStack(val stack: RemoteStackEntity) : SyncChange
    data class DeleteStack(val id: String) : SyncChange
    data class UpsertPartner(val sharedById: String, val inTimeline: Boolean) : SyncChange
    data class DeletePartner(val sharedById: String) : SyncChange
    data class SetUser(val id: String) : SyncChange
    data class UpsertUser(val user: UserEntity) : SyncChange
    data class DeleteUser(val id: String) : SyncChange

    /** Drop the local copy and sync from scratch. */
    data object Reset : SyncChange

    /** Nothing to store, only to ack (`SyncAckV1`, `SyncCompleteV1`, types we don't use). */
    data object AckOnly : SyncChange
}

/** Maps a line to a change. Unknown types become [SyncChange.AckOnly] so a newer server can't stall us. */
fun SyncLine.toChange(json: Json): SyncChange = when (type) {
    "AssetV2", "PartnerAssetV2", "PartnerAssetBackfillV2" ->
        SyncChange.UpsertAsset(json.decodeFromJsonElement<SyncAssetDto>(data).toEntity())
    "AssetDeleteV1", "PartnerAssetDeleteV1" ->
        SyncChange.DeleteAsset(json.decodeFromJsonElement<SyncAssetDeleteDto>(data).assetId)
    "StackV1", "PartnerStackV1", "PartnerStackBackfillV1" ->
        json.decodeFromJsonElement<SyncStackDto>(data).let {
            SyncChange.UpsertStack(RemoteStackEntity(it.id, it.primaryAssetId, it.ownerId))
        }
    "StackDeleteV1", "PartnerStackDeleteV1" ->
        SyncChange.DeleteStack(json.decodeFromJsonElement<SyncStackDeleteDto>(data).stackId)
    "PartnerV1" -> json.decodeFromJsonElement<SyncPartnerDto>(data).let {
        SyncChange.UpsertPartner(it.sharedById, it.inTimeline)
    }
    "PartnerDeleteV1" -> SyncChange.DeletePartner(json.decodeFromJsonElement<SyncPartnerDeleteDto>(data).sharedById)
    "AuthUserV2" -> SyncChange.SetUser(json.decodeFromJsonElement<SyncAuthUserDto>(data).id)
    "UserV1" -> json.decodeFromJsonElement<SyncUserDto>(data).let {
        // A soft-deleted user is gone for the wall.
        if (it.deletedAt != null) SyncChange.DeleteUser(it.id) else SyncChange.UpsertUser(it.toEntity())
    }
    "UserDeleteV1" -> SyncChange.DeleteUser(json.decodeFromJsonElement<SyncUserDeleteDto>(data).userId)
    "SyncResetV1" -> SyncChange.Reset
    else -> SyncChange.AckOnly
}

private fun String.toEpochMillis(): Long = Instant.parse(this).toEpochMilli()

internal fun SyncUserDto.toEntity() = UserEntity(
    id = id,
    name = name?.takeIf { it.isNotBlank() } ?: email ?: id,
    email = email.orEmpty(),
    avatarColor = avatarColor,
    hasProfileImage = hasProfileImage,
    profileChangedAt = profileChangedAt?.let { runCatching { it.toEpochMillis() }.getOrNull() },
)

internal fun SyncAssetDto.toEntity() = RemoteAssetEntity(
    id = id,
    ownerId = ownerId,
    // Wall-clock time where the photo was taken, written as if it were UTC (live check, T-015).
    localDateTime = (localDateTime ?: fileCreatedAt)?.toEpochMillis(),
    fileCreatedAt = fileCreatedAt?.toEpochMillis(),
    isVideo = type == "VIDEO",
    visibility = visibility,
    deletedAt = deletedAt?.toEpochMillis(),
    width = width,
    height = height,
    thumbhash = thumbhash,
    duration = duration,
    livePhotoVideoId = livePhotoVideoId,
    stackId = stackId,
    isFavorite = isFavorite,
    checksum = checksum,
)
