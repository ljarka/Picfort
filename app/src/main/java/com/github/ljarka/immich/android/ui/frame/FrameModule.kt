package com.github.ljarka.immich.android.ui.frame

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.github.ljarka.immich.android.network.CleartextPolicyInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class FrameModule {

    /** No auth interceptor: the frame authenticates to Immich with its own API key. */
    @FrameHttpClient
    @Singleton
    @Provides
    fun frameHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(CleartextPolicyInterceptor())
        .connectTimeout(5, TimeUnit.SECONDS)
        // The frame downloads and renders the original before answering.
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
}
