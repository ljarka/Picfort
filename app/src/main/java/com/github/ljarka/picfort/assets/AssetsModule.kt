package com.github.ljarka.picfort.assets

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
class AssetsModule {

    // Unscoped on purpose: Retrofit is built from the current server URL.
    @Provides
    fun assetsService(retrofit: Retrofit): AssetsService = retrofit.create(AssetsService::class.java)
}
