package com.github.ljarka.picfort.ui.init

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.ui.frame.FrameSettingsStore
import com.github.ljarka.picfort.ui.login.AccessTokenStore
import com.github.ljarka.picfort.ui.server.ServerUrlStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InitViewModel @Inject constructor(
    private val serverUrlStore: ServerUrlStore,
    private val accessTokenStore: AccessTokenStore,
    private val frameSettingsStore: FrameSettingsStore,
) : ViewModel() {

    /** After login: whether to show the one-time Picfort server step before the timeline. */
    val needsPicfortServerStep: Boolean get() = frameSettingsStore.needsSetupStep

    private val initUpdates = MutableSharedFlow<InitState>()

    val initState = merge(initUpdates, flow {
        emit(checkInitState())
    }).stateIn(viewModelScope, SharingStarted.Lazily, InitState.UNKNOWN)

    fun updateState() {
        viewModelScope.launch {
            initUpdates.emit(checkInitState())
        }
    }

    private fun checkInitState() = if (accessTokenStore.hasData()) {
        InitState.INITIALIZED
    } else if (serverUrlStore.hasData()) {
        InitState.REQUIRES_LOGIN
    } else {
        InitState.REQUIRES_SERVER_CONFIGURATION
    }
}