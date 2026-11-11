package com.github.ljarka.picfort.ui.wall

import com.github.ljarka.picfort.network.PicfortHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class WallModule {

    /**
     * The shared client: its PicfortAuthInterceptor adds the Immich token to `/wall` URLs on the
     * Picfort server (ADR 0006), and its AuthInterceptor leaves that host alone.
     */
    @PicfortHttpClient
    @Singleton
    @Provides
    fun wallHttpClient(shared: OkHttpClient): OkHttpClient = shared.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS) // photo uploads over home Wi-Fi
        .build()
}
