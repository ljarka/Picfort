package com.github.ljarka.picfort.ui.timeline

import com.github.ljarka.picfort.collections.ImmichEventAlbumsProvider
import com.github.ljarka.picfort.core.model.DayPhotosProvider
import com.github.ljarka.picfort.core.model.EventAlbumsProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class TimelineModule {

    @Provides
    fun timelineBucketService(retrofit: retrofit2.Retrofit) = retrofit.create(TimelineBucketsService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
interface TimelineBindingsModule {
    @Binds
    fun bindDayPhotosProvider(impl: DayPhotosRepository): DayPhotosProvider

    @Binds
    fun bindEventAlbumsProvider(impl: ImmichEventAlbumsProvider): EventAlbumsProvider
}
