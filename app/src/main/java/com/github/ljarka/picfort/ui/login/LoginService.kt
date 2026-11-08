package com.github.ljarka.picfort.ui.login

import retrofit2.http.Body
import retrofit2.http.POST

interface LoginService {

    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginRequestBody): LoginResponse
}