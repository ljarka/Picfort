package com.github.ljarka.picfort.ui.init

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.core.network.AccessTokenStore
import com.github.ljarka.picfort.core.network.CleartextNotAllowedException
import com.github.ljarka.picfort.core.network.FrameSettingsStore
import com.github.ljarka.picfort.core.network.ServerUrlStore
import com.github.ljarka.picfort.core.network.isLocalNetworkHost
import com.github.ljarka.picfort.core.network.normalizeFrameUrl
import com.github.ljarka.picfort.network.normalizeServerUrl
import com.github.ljarka.picfort.ui.frame.ConnectionTest
import com.github.ljarka.picfort.ui.frame.PicfortServerCheck
import com.github.ljarka.picfort.ui.login.LoginRequestBody
import com.github.ljarka.picfort.ui.login.LoginService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider

/** What the setup screen is doing right now. */
enum class SetupStep(val label: String) {
    Idle(""),
    CheckingImmich("Connecting to Immich…"),
    SigningIn("Signing in…"),
    CheckingPicfort("Checking the Picfort server…"),
    Done(""),
}

/**
 * Everything needed to start (T-058): Immich address and login, and the optional Picfort server,
 * on one screen. [signedIn]: Immich is done and only the Picfort server is left (it failed).
 */
data class SetupUi(
    val immichAddress: String = "",
    val email: String = "",
    val password: String = "",
    val picfortAddress: String = "",
    val picfortApiKey: String = "",
    val showApiKey: Boolean = false,
    /** False when a Picfort server is already set (logged out later): the section isn't shown. */
    val askPicfort: Boolean = true,
    val step: SetupStep = SetupStep.Idle,
    val signedIn: Boolean = false,
    val immichError: String? = null,
    val loginError: String? = null,
    val picfortError: String? = null,
) {
    val busy: Boolean get() = step != SetupStep.Idle && step != SetupStep.Done
    val canConnect: Boolean
        get() = !busy && (signedIn || immichAddress.isNotBlank() && email.isNotBlank() && password.isNotEmpty())
}

/** "Will connect to https://photos.example.com": what a typed address becomes, or null. */
fun immichTarget(address: String): String? =
    if (address.isBlank()) null else normalizeServerUrl(address)?.toString()?.trimEnd('/')

fun picfortTarget(address: String): String? =
    if (address.isBlank()) null else normalizeFrameUrl(address)?.toString()?.trimEnd('/')

/** The Picfort server usually runs on the same computer as Immich, on port 5000 (docker/example.env). */
fun picfortSuggestion(immichAddress: String): String? {
    val url = normalizeServerUrl(immichAddress) ?: return null
    if (!isLocalNetworkHost(url.host)) return null
    val host = if (':' in url.host) "[${url.host}]" else url.host
    return "$host:5000"
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val serverUrlStore: ServerUrlStore,
    private val accessTokenStore: AccessTokenStore,
    private val frameSettingsStore: FrameSettingsStore,
    /** Built only once the Immich address is saved: Retrofit takes its base URL from the store. */
    private val loginService: Provider<LoginService>,
    private val okHttpClient: OkHttpClient,
    private val picfortServer: PicfortServerCheck,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SetupUi(
            immichAddress = serverUrlStore.serverUrl,
            askPicfort = !frameSettingsStore.hasData(),
        ),
    )
    val state: StateFlow<SetupUi> = _state.asStateFlow()

    fun onImmichAddress(value: String) = _state.update { it.copy(immichAddress = value, immichError = null) }
    fun onEmail(value: String) = _state.update { it.copy(email = value, loginError = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, loginError = null) }
    fun onPicfortAddress(value: String) = _state.update { it.copy(picfortAddress = value, picfortError = null) }
    fun onPicfortApiKey(value: String) = _state.update { it.copy(picfortApiKey = value, picfortError = null) }
    fun showApiKey() = _state.update { it.copy(showApiKey = true) }

    /** Signed in, but the user wants another Immich account or server. */
    fun changeImmich() {
        accessTokenStore.accessToken = ""
        _state.update { it.copy(signedIn = false, step = SetupStep.Idle) }
    }

    /** The Picfort server failed after signing in: go on without it (Settings has it later). */
    fun skipPicfort() {
        picfortServer.markPrompted()
        _state.update { it.copy(step = SetupStep.Done) }
    }

    fun connect() {
        val s = _state.value
        if (!s.canConnect) return
        viewModelScope.launch {
            if (!s.signedIn && !signIn(s)) return@launch
            val picfort = _state.value.picfortAddress
            if (!_state.value.askPicfort || picfort.isBlank()) {
                if (_state.value.askPicfort) picfortServer.markPrompted()
                _state.update { it.copy(step = SetupStep.Done) }
                return@launch
            }
            _state.update { it.copy(step = SetupStep.CheckingPicfort, picfortError = null) }
            val apiKey = _state.value.picfortApiKey
            when (val result = picfortServer.check(picfort, apiKey)) {
                is ConnectionTest.Failed -> _state.update { it.copy(step = SetupStep.Idle, picfortError = result.message) }
                else -> { // Ok, or it answers but the wall has a problem: saved, details in Settings.
                    picfortServer.save(picfort, apiKey)
                    picfortServer.markPrompted()
                    _state.update { it.copy(step = SetupStep.Done) }
                }
            }
        }
    }

    /** Immich: ping, then log in. Errors land on the field they belong to. */
    private suspend fun signIn(s: SetupUi): Boolean {
        val url = normalizeServerUrl(s.immichAddress)?.toString()?.trimEnd('/')
        if (url == null) {
            _state.update { it.copy(immichError = "That doesn't look like a server address") }
            return false
        }
        _state.update { it.copy(step = SetupStep.CheckingImmich, immichError = null, loginError = null) }
        val pingError = withContext(Dispatchers.IO) { ping(url) }
        if (pingError != null) {
            _state.update { it.copy(step = SetupStep.Idle, immichError = pingError) }
            return false
        }
        serverUrlStore.serverUrl = url
        _state.update { it.copy(step = SetupStep.SigningIn) }
        val error = try {
            val result = loginService.get().login(LoginRequestBody(s.email.trim(), s.password))
            accessTokenStore.userId = result.userId
            accessTokenStore.userEmail = result.userEmail
            accessTokenStore.accessToken = result.accessToken
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == 401 || e.code() == 400) "Wrong email or password" else "Server error (HTTP ${e.code()})"
        } catch (e: CleartextNotAllowedException) {
            e.message ?: "Use https://"
        } catch (e: IOException) {
            "Can't reach the server"
        } catch (e: SerializationException) {
            "Login failed: ${e.message}"
        } catch (e: IllegalStateException) {
            "Login failed: ${e.message}"
        }
        _state.update {
            if (error == null) it.copy(signedIn = true, password = "", step = SetupStep.Idle)
            else it.copy(loginError = error, step = SetupStep.Idle)
        }
        return error == null
    }

    private fun ping(baseUrl: String): String? = try {
        val request = Request.Builder().url("$baseUrl/api/server/ping").build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (response.isSuccessful && "pong" in body) null
            else "No Immich server found at $baseUrl (HTTP ${response.code})"
        }
    } catch (e: CleartextNotAllowedException) {
        e.message
    } catch (e: IOException) {
        "Can't connect to $baseUrl"
    } catch (e: IllegalArgumentException) {
        "That doesn't look like a server address"
    }
}
