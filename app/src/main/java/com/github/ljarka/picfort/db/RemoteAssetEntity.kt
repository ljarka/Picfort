package com.github.ljarka.picfort.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An Immich asset (own or a partner's) from the sync stream. Times are epoch millis. */
@Entity(
    tableName = "remote_assets",
    indices = [Index("localDateTime"), Index("ownerId"), Index("stackId")],
)
data class RemoteAssetEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    /** Wall-clock time where it was taken, stored as if UTC: group months in UTC. */
    val localDateTime: Long?,
    val fileCreatedAt: Long?,
    val isVideo: Boolean,
    /** timeline | archive | hidden | locked */
    val visibility: String,
    /** Set while in the trash; a permanent delete removes the row. */
    val deletedAt: Long?,
    /** Display size, already corrected for EXIF orientation by the server. Null until processed. */
    val width: Int?,
    val height: Int?,
    val thumbhash: String?,
    /** `AssetV2.duration`. Unit not verified yet (no videos on the test account), see T-015. */
    val duration: Long?,
    val livePhotoVideoId: String?,
    val stackId: String?,
    val isFavorite: Boolean,
    val checksum: String,
)

@Entity(tableName = "remote_stacks")
data class RemoteStackEntity(
    @PrimaryKey val id: String,
    val primaryAssetId: String,
    val ownerId: String,
)

/** A user who shares their library with us. */
@Entity(tableName = "partners")
data class PartnerEntity(
    @PrimaryKey val sharedById: String,
    val inTimeline: Boolean,
)

/**
 * Whose copy the remote tables hold. Kept in the same database so a wipe (schema change)
 * clears it too; otherwise the server would only send deltas to an empty copy.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 0,
    /** Server and login session; checkpoints live on the server per session. */
    val owner: String,
    /** The synced account's Immich user id, from `AuthUserV2`. */
    val userId: String,
)

/** Number of timeline assets in a month; [month] is midnight UTC on its 1st, in millis. */
data class RemoteMonthCount(val month: Long, val count: Int)
