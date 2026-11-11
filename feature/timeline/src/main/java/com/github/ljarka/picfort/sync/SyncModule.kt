package com.github.ljarka.picfort.sync

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    abstract fun syncStore(store: RoomSyncStore): SyncStore

    companion object {
        @Provides
        fun syncApi(retrofit: Retrofit): SyncApi = retrofit.create(SyncApi::class.java)
    }
}
