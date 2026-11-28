package com.github.ljarka.picfort.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Additive steps that keep the cache instead of wiping it, so an update doesn't force a full
 * timeline re-sync and backup re-hash. Anything without a step here still falls back to a wipe.
 * The SQL is Room's own `createAllTables` statements for those entities.
 */
internal val MIGRATION_23_24 = object : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // T-088: camera EXIF and OCR document caches. Rows arrive with the next sync stream
        // (AssetExifsV1 has no checkpoint yet) and the next OCR refresh.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `remote_asset_exif` " +
                "(`assetId` TEXT NOT NULL, `hasCameraExif` INTEGER NOT NULL, PRIMARY KEY(`assetId`))",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS `remote_documents` (`assetId` TEXT NOT NULL, PRIMARY KEY(`assetId`))")
    }
}

internal val IMAGES_DATABASE_MIGRATIONS = arrayOf(MIGRATION_23_24)
