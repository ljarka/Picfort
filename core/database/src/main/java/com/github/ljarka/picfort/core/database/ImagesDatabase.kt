package com.github.ljarka.picfort.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * A cache: a schema change wipes it (no migrations). That includes the backup state, so the
 * first backup after an update re-hashes the phone's files; nothing is uploaded twice.
 */
@Database(
    entities = [
        LocalBackupEntity::class,
        RemoteAssetEntity::class, RemoteStackEntity::class, PartnerEntity::class, SyncStateEntity::class,
        UserEntity::class,
    ],
    version = 23,
    exportSchema = false,
)
abstract class ImagesDatabase : RoomDatabase() {
    abstract fun backupDao(): BackupDao
    abstract fun remoteAssetsDao(): RemoteAssetsDao
    abstract fun usersDao(): UsersDao
}
