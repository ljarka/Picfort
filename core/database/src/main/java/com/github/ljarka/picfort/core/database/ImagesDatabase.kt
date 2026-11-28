package com.github.ljarka.picfort.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * A cache: a schema change without a step in [IMAGES_DATABASE_MIGRATIONS] wipes it. That
 * includes the backup state, so the first backup after such an update re-hashes the phone's
 * files; nothing is uploaded twice.
 */
@Database(
    entities = [
        LocalBackupEntity::class,
        RemoteAssetEntity::class, RemoteStackEntity::class, PartnerEntity::class, SyncStateEntity::class,
        RemoteAssetExifEntity::class, RemoteDocumentEntity::class,
        UserEntity::class,
    ],
    version = 24,
    exportSchema = false,
)
abstract class ImagesDatabase : RoomDatabase() {
    abstract fun backupDao(): BackupDao
    abstract fun remoteAssetsDao(): RemoteAssetsDao
    abstract fun usersDao(): UsersDao
}
