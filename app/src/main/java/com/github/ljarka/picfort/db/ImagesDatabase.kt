package com.github.ljarka.picfort.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [AssetEntity::class, MonthBucketEntity::class], version = 16)
abstract class ImagesDatabase : RoomDatabase() {
    abstract fun imagesDao(): ImagesDao
}