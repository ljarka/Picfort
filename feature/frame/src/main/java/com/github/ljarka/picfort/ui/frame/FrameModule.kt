package com.github.ljarka.picfort.ui.frame

import com.github.ljarka.picfort.core.model.FrameSender
import com.github.ljarka.picfort.core.network.CleartextPolicyInterceptor
import com.github.ljarka.picfort.core.network.PicfortKeyInterceptor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
interface FrameBindingsModule {
    @Binds
    fun bindFrameSender(impl: FrameRepository): FrameSender
}

@Module
@InstallIn(SingletonComponent::class)
class FrameModule {

    /**
     * No Immich token: the frame authenticates to Immich with its own API key. Only the Picfort
     * server's own key (if set) is added.
     */
    @FrameHttpClient
    @Singleton
    @Provides
    fun frameHttpClient(picfortKey: PicfortKeyInterceptor): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(CleartextPolicyInterceptor())
        .addInterceptor(picfortKey)
        .connectTimeout(5, TimeUnit.SECONDS)
        // The frame downloads and renders the original before answering.
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
