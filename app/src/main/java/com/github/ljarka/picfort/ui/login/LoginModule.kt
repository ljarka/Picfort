package com.github.ljarka.picfort.ui.login

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import retrofit2.Retrofit

@Module
@InstallIn(ViewModelComponent::class)
class LoginModule {

    @Provides
    fun loginService(retrofit: Retrofit): LoginService = retrofit.create(LoginService::class.java)
}
