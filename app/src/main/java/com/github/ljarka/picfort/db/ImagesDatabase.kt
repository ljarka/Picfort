package com.github.ljarka.picfort.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AssetEntity::class, MonthBucketEntity::class, LocalBackupEntity::class],
    version = 19,
)
abstract class ImagesDatabase : RoomDatabase() {
    companion object {
        /** Not destructive: the backup state lives in this database too. */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE assets ADD COLUMN thumbhash TEXT")
            }
        }
    }

    abstract fun imagesDao(): ImagesDao
    abstract fun backupDao(): BackupDao
}