package com.github.ljarka.picfort.network

import com.github.ljarka.picfort.ui.login.AccessTokenStore
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Provider

class AuthInterceptor @Inject constructor(
    private val tokenProvider: Provider<AccessTokenStore>,
    private val sessionManager: Provider<SessionManager>,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (originalRequest.url.encodedPath.contains("/auth/login")) {
            return chain.proceed(originalRequest)
        }
        val token = tokenProvider.get().accessToken
        if (token.isEmpty()) {
            return chain.proceed(originalRequest) // e.g. checking a server before login
        }
        val request = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        val response = chain.proceed(request)
        if (response.code == 401) {
            sessionManager.get().onUnauthorized()
        }
        return response
    }
}
