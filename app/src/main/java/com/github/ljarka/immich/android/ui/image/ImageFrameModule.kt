package com.github.ljarka.immich.android.ui.image

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import retrofit2.Retrofit

@Module
@InstallIn(ViewModelComponent::class)
class ImageFrameModule {

    @Provides
    fun imageFrameService(retrofit: Retrofit) = retrofit.create(ImageFrameService::class.java)

}