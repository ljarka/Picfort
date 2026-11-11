package com.github.ljarka.picfort.sync

import androidx.room.withTransaction
import com.github.ljarka.picfort.core.database.ImagesDatabase
import com.github.ljarka.picfort.core.database.PartnerEntity
import com.github.ljarka.picfort.core.database.SyncStateEntity
import javax.inject.Inject

/** Where synced changes go. An interface so the engine can be tested without Room. */
interface SyncStore {
    /** Applies the changes in order, all or nothing. */
    suspend fun apply(changes: List<SyncChange>)

    /** Drops the whole local copy, including [owner]. */
    suspend fun clear()

    /** Whose copy this is (server + login session), or null for an empty copy. */
    suspend fun owner(): String?

    suspend fun setOwner(owner: String)
}

class RoomSyncStore @Inject constructor(
    private val database: ImagesDatabase,
) : SyncStore {
    private val dao by lazy { database.remoteAssetsDao() }
    private val users by lazy { database.usersDao() }

    override suspend fun apply(changes: List<SyncChange>) {
        database.withTransaction {
            // Runs of the same kind are written together; order between kinds is kept.
            var i = 0
            while (i < changes.size) {
                val kind = changes[i]::class
                var end = i
                while (end < changes.size && changes[end]::class == kind) end++
                write(changes.subList(i, end))
                i = end
            }
        }
    }

    private suspend fun write(run: List<SyncChange>) {
        when (run.first()) {
            is SyncChange.UpsertAsset -> dao.upsertAssets(run.map { (it as SyncChange.UpsertAsset).asset })
            is SyncChange.DeleteAsset -> run.map { (it as SyncChange.DeleteAsset).id }
                .chunked(500).forEach { dao.deleteAssets(it) }
            is SyncChange.UpsertStack -> dao.upsertStacks(run.map { (it as SyncChange.UpsertStack).stack })
            is SyncChange.DeleteStack -> dao.deleteStacks(run.map { (it as SyncChange.DeleteStack).id })
            is SyncChange.UpsertPartner -> dao.upsertPartners(
                run.map { (it as SyncChange.UpsertPartner).let { p -> PartnerEntity(p.sharedById, p.inTimeline) } }
            )
            is SyncChange.DeletePartner -> dao.deletePartners(run.map { (it as SyncChange.DeletePartner).sharedById })
            is SyncChange.SetUser -> {
                val userId = (run.last() as SyncChange.SetUser).id
                dao.setSyncState((dao.syncState() ?: SyncStateEntity(owner = "", userId = "")).copy(userId = userId))
            }
            is SyncChange.UpsertUser -> users.upsert(run.map { (it as SyncChange.UpsertUser).user })
            is SyncChange.DeleteUser -> users.delete(run.map { (it as SyncChange.DeleteUser).id })
            SyncChange.Reset, SyncChange.AckOnly -> Unit
        }
    }

    override suspend fun owner(): String? = dao.syncState()?.owner?.takeIf { it.isNotEmpty() }

    override suspend fun setOwner(owner: String) {
        database.withTransaction {
            dao.setSyncState((dao.syncState() ?: SyncStateEntity(owner = "", userId = "")).copy(owner = owner))
        }
    }

    override suspend fun clear() {
        database.withTransaction {
            dao.clearAssets()
            dao.clearStacks()
            dao.clearPartners()
            dao.clearSyncState()
            users.clear()
        }
    }
}
