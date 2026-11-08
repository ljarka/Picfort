package com.github.ljarka.immich.android.network

import com.github.ljarka.immich.android.BuildConfig
import com.github.ljarka.immich.android.ui.server.ServerUrlStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Provides
    fun json() = Json { ignoreUnknownKeys = true }

    @Provides
    fun okhttp(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(CleartextPolicyInterceptor())
            .addInterceptor(authInterceptor)
        if (BuildConfig.DEBUG) {
            // Debug only: bodies include the login password. Added last so it
            // sees the final request, with credentials redacted.
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                    redactHeader("Authorization")
                    redactHeader("x-api-key")
                }
            )
        }
        return builder.build()
    }

    @Provides
    fun retrofit(
        okHttpClient: OkHttpClient,
        serverUrlStore: ServerUrlStore,
        json: Json,
    ) = Retrofit.Builder()
        .baseUrl(serverUrlStore.serverUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}