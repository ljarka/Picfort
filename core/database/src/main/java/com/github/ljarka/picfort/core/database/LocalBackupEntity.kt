package com.github.ljarka.picfort.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * Backup state of one photo/video on the phone. The checksum is cached so each
 * file is hashed once (until it changes); remoteId is the Immich asset it maps to.
 */
@Entity(tableName = "local_backup", indices = [Index("remoteId"), Index("dateTaken")])
data class LocalBackupEntity(
    @PrimaryKey @ColumnInfo(name = "localId") val localId: Long,
    @ColumnInfo(name = "dateModified") val dateModified: Long,
    @ColumnInfo(name = "size") val size: Long,
    @ColumnInfo(name = "dateTaken") val dateTaken: Long,
    /** SHA-1, hex. */
    @ColumnInfo(name = "checksum") val checksum: String,
    @ColumnInfo(name = "remoteId") val remoteId: String? = null,
    /** Set when Immich won't take the file (e.g. unsupported format); not retried. */
    @ColumnInfo(name = "rejected") val rejected: String? = null,
)

@Dao
interface BackupDao {
    @Query("SELECT * FROM local_backup")
    suspend fun getAll(): List<LocalBackupEntity>

    @Query("SELECT * FROM local_backup WHERE localId IN (:ids)")
    suspend fun get(ids: List<Long>): List<LocalBackupEntity>

    @Upsert
    suspend fun upsert(rows: List<LocalBackupEntity>)

    @Query("UPDATE local_backup SET remoteId = :remoteId, rejected = NULL WHERE localId = :localId")
    suspend fun setRemoteId(localId: Long, remoteId: String)

    @Query("UPDATE local_backup SET rejected = :reason WHERE localId = :localId")
    suspend fun setRejected(localId: Long, reason: String)

    @Query("SELECT * FROM local_backup WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findByRemoteId(remoteId: String): LocalBackupEntity?

    @Query("DELETE FROM local_backup WHERE localId IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM local_backup WHERE remoteId IS NOT NULL AND dateTaken >= :start AND dateTaken < :end")
    suspend fun countBackedUp(start: Long, end: Long): Int

    @Query("SELECT COUNT(*) FROM local_backup WHERE remoteId IS NOT NULL")
    suspend fun countBackedUp(): Int

    /** Phone files Picfort's backup linked to an Immich asset. */
    @Query("SELECT localId FROM local_backup WHERE remoteId IS NOT NULL")
    suspend fun linkedLocalIds(): List<Long>

    /** Forget which Immich assets files map to (another server or account), keeping checksums. */
    @Query("UPDATE local_backup SET remoteId = NULL, rejected = NULL")
    suspend fun resetRemote()
}
