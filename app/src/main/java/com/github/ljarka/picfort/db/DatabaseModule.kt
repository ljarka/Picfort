package com.github.ljarka.picfort.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ImagesDatabase {
        return Room.databaseBuilder(
            context = context,
            klass = ImagesDatabase::class.java, "images"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun backupDao(database: ImagesDatabase): BackupDao = database.backupDao()
}