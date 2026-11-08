package com.github.ljarka.picfort.ui.timeline

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class TimelineModule {

    @Provides
    fun timelineBucketService(retrofit: retrofit2.Retrofit) =
        retrofit.create(TimelineBucketsService::class.java)
}