package com.github.ljarka.picfort.ui.login

sealed class LoginState {

    data object LoggedOut : LoginState()

    data object LoggingIn : LoginState()

    data object LoggedIn : LoginState()

    data class Error(val message: String) : LoginState()
}
