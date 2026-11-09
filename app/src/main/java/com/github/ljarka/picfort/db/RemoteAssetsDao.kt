package com.github.ljarka.picfort.db

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

    @Query(
        """SELECT CAST(strftime('%s', a.localDateTime / 1000, 'unixepoch', 'start of month') AS INTEGER) * 1000
               AS month, COUNT(*) AS count
           FROM remote_assets a WHERE $TIMELINE_FILTER
           GROUP BY month ORDER BY month DESC"""
    )
    fun monthCounts(userId: String): Flow<List<RemoteMonthCount>>

    /** Timeline assets with [from] ≤ localDateTime < [to], newest first. */
    @Query(
        """SELECT * FROM remote_assets a WHERE $TIMELINE_FILTER
           AND a.localDateTime >= :from AND a.localDateTime < :to
           ORDER BY a.localDateTime DESC, a.id"""
    )
    suspend fun timelineAssets(userId: String, from: Long, to: Long): List<RemoteAssetEntity>

    @Query("SELECT * FROM sync_state WHERE id = 0")
    suspend fun syncState(): SyncStateEntity?

    @Upsert
    suspend fun setSyncState(state: SyncStateEntity)

    @Query("DELETE FROM sync_state")
    suspend fun clearSyncState()

    @Query("SELECT COUNT(*) FROM remote_assets")
    suspend fun countAll(): Int
}
