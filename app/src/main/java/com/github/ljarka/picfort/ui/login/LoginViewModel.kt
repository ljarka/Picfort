package com.github.ljarka.picfort.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.network.CleartextNotAllowedException
import com.github.ljarka.picfort.ui.server.ServerUrlStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginService: LoginService,
    private val accessTokenStore: AccessTokenStore,
    private val serverUrlStore: ServerUrlStore,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.LoggedOut)
    val state = _state.asStateFlow()

    val serverUrl: String get() = serverUrlStore.serverUrl

    fun clearError() {
        if (_state.value is LoginState.Error) _state.value = LoginState.LoggedOut
    }

    fun performLogin(email: String, password: String) {
        if (_state.value == LoginState.LoggingIn) return
        _state.value = LoginState.LoggingIn
        viewModelScope.launch {
            _state.value = try {
                val result = loginService.login(LoginRequestBody(email.trim(), password))
                accessTokenStore.userId = result.userId
                accessTokenStore.accessToken = result.accessToken
                LoginState.LoggedIn
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                LoginState.Error(
                    if (e.code() == 401 || e.code() == 400) "Wrong email or password"
                    else "Server error (HTTP ${e.code()})"
                )
            } catch (e: CleartextNotAllowedException) {
                LoginState.Error(e.message ?: "Use https://")
            } catch (e: IOException) {
                LoginState.Error("Can't reach the server")
            } catch (e: Exception) { // e.g. unexpected response shape
                LoginState.Error("Login failed: ${e.message}")
            }
        }
    }

    /** Lets the user escape a wrong server address. */
    fun changeServer() {
        accessTokenStore.accessToken = ""
        serverUrlStore.serverUrl = ""
    }
}
