package com.github.ljarka.picfort.core.database

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
    fun database(@ApplicationContext context: Context): ImagesDatabase = Room.databaseBuilder(
        context = context,
        klass = ImagesDatabase::class.java,
        name = "images",
    ).fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun backupDao(database: ImagesDatabase): BackupDao = database.backupDao()

    @Provides
    fun remoteAssetsDao(database: ImagesDatabase): RemoteAssetsDao = database.remoteAssetsDao()

    @Provides
    fun usersDao(database: ImagesDatabase): UsersDao = database.usersDao()
}
