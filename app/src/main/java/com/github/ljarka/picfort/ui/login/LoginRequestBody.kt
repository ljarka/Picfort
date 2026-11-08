package com.github.ljarka.picfort.ui.login

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestBody(
    val email: String,
    val password: String,
)