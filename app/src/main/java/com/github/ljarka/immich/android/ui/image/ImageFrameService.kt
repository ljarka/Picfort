package com.github.ljarka.immich.android.ui.image

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ImageFrameService {

    @GET("http://192.168.0.105:5000/run")
    suspend fun sendToImageFrame(@Query("assetId") assetId: String): Response<Unit>
}