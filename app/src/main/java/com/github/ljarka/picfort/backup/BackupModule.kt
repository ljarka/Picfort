package com.github.ljarka.picfort.backup

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
class BackupModule {
    @Provides
    fun backupService(retrofit: Retrofit): BackupService = retrofit.create(BackupService::class.java)
}
