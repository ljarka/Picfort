package com.github.ljarka.immich.android.ui.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.immich.android.network.CleartextNotAllowedException
import com.github.ljarka.immich.android.network.normalizeServerUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

sealed interface ServerCheckState {
    data object Idle : ServerCheckState
    data object Checking : ServerCheckState
    data object Saved : ServerCheckState
    data class Error(val message: String) : ServerCheckState
}

@HiltViewModel
class ServerConfigurationViewModel @Inject constructor(
    private val serverUrlStore: ServerUrlStore,
    private val okHttpClient: OkHttpClient,
) : ViewModel() {

    private val _state = MutableStateFlow<ServerCheckState>(ServerCheckState.Idle)
    val state = _state.asStateFlow()

    fun clearError() {
        if (_state.value is ServerCheckState.Error) _state.value = ServerCheckState.Idle
    }

    /** Saves the URL only after the server answers Immich's ping. */
    fun setServerUrl(input: String) {
        val url = normalizeServerUrl(input)
        if (url == null) {
            _state.value = ServerCheckState.Error("That doesn't look like a server address")
            return
        }
        _state.value = ServerCheckState.Checking
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { ping(url.toString().trimEnd('/')) }
            if (error == null) {
                serverUrlStore.serverUrl = url.toString().trimEnd('/')
                _state.value = ServerCheckState.Saved
            } else {
                _state.value = ServerCheckState.Error(error)
            }
        }
    }

    private fun ping(baseUrl: String): String? = try {
        val request = Request.Builder().url("$baseUrl/api/server/ping").build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            when {
                response.isSuccessful && "pong" in body -> null
                else -> "No Immich server found at $baseUrl (HTTP ${response.code})"
            }
        }
    } catch (e: CleartextNotAllowedException) {
        e.message
    } catch (e: IOException) {
        "Can't connect to $baseUrl"
    } catch (e: IllegalArgumentException) {
        "That doesn't look like a server address"
    }
}
