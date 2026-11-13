package com.github.ljarka.picfort.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Rows the timeline shows (ADR 0005, rule 6): in the timeline (not archived, hidden or
 * locked), not in the trash, owned by the user or by a partner shown in the timeline, and
 * not a non-primary member of a stack. `a` is `remote_assets`.
 */
private const val TIMELINE_FILTER = """
    a.visibility = 'timeline' AND a.deletedAt IS NULL AND a.localDateTime IS NOT NULL
    AND (a.ownerId = :userId OR a.ownerId IN (SELECT sharedById FROM partners WHERE inTimeline = 1))
    AND (a.stackId IS NULL
        OR NOT EXISTS (SELECT 1 FROM remote_stacks s WHERE s.id = a.stackId)
        OR a.id = (SELECT s.primaryAssetId FROM remote_stacks s WHERE s.id = a.stackId))
"""

@Dao
interface RemoteAssetsDao {

    @Upsert
    suspend fun upsertAssets(assets: List<RemoteAssetEntity>)

    /** Unknown ids are fine: a full stream deletes assets this copy never had. */
    @Query("DELETE FROM remote_assets WHERE id IN (:ids)")
    suspend fun deleteAssets(ids: List<String>)

    @Upsert
    suspend fun upsertStacks(stacks: List<RemoteStackEntity>)

    @Query("DELETE FROM remote_stacks WHERE id IN (:ids)")
    suspend fun deleteStacks(ids: List<String>)

    @Upsert
    suspend fun upsertPartners(partners: List<PartnerEntity>)

    @Query("DELETE FROM partners WHERE sharedById IN (:ids)")
    suspend fun deletePartners(ids: List<String>)

    @Query("DELETE FROM remote_assets")
    suspend fun clearAssets()

    @Query("DELETE FROM remote_stacks")
    suspend fun clearStacks()

    @Query("DELETE FROM partners")
    suspend fun clearPartners()

    @Query("SELECT * FROM sync_state WHERE id = 0")
    suspend fun syncState(): SyncStateEntity?

    @Query("SELECT * FROM sync_state WHERE id = 0")
    fun syncStateFlow(): Flow<SyncStateEntity?>

    @Upsert
    suspend fun setSyncState(state: SyncStateEntity)

    @Query("DELETE FROM sync_state")
    suspend fun clearSyncState()

    /** The whole timeline, newest first; re-emits on every change to the tables. */
    @Query("SELECT * FROM remote_assets a WHERE $TIMELINE_FILTER ORDER BY a.localDateTime DESC, a.id")
    fun timelineAll(userId: String): Flow<List<RemoteAssetEntity>>

    /**
     * Timeline assets taken (local time, [localDateTime] in ms) on one of [monthDays] ("MM-DD")
     * from [fromMillis] on, newest first. The caller keeps the exact days it wants (T-055).
     */
    @Query(
        "SELECT * FROM remote_assets a WHERE $TIMELINE_FILTER AND a.localDateTime >= :fromMillis" +
            " AND strftime('%m-%d', a.localDateTime / 1000, 'unixepoch') IN (:monthDays)" +
            " ORDER BY a.localDateTime DESC, a.id"
    )
    suspend fun takenOnMonthDays(userId: String, fromMillis: Long, monthDays: List<String>): List<RemoteAssetEntity>

    @Query("SELECT COUNT(*) FROM remote_assets")
    suspend fun countAll(): Int
}
