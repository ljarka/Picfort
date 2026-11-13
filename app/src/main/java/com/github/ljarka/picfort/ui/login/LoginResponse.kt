package com.github.ljarka.picfort.ui.login

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val accessToken: String,
    val userId: String,
    val userEmail: String,
    val name: String,
    val isAdmin: Boolean,
    val profileImagePath: String,
    val shouldChangePassword: Boolean,
)
