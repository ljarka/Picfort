package com.github.ljarka.picfort.ui.init

import androidx.lifecycle.ViewModel
import com.github.ljarka.picfort.core.network.AccessTokenStore
import com.github.ljarka.picfort.core.network.FrameSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class InitViewModel @Inject constructor(
    private val accessTokenStore: AccessTokenStore,
    private val frameSettingsStore: FrameSettingsStore,
) : ViewModel() {

    /** Signed in to Immich: straight to the app. */
    val signedIn: Boolean get() = accessTokenStore.hasData()

    /** After login: whether to show the one-time Picfort server step (installs from before T-058). */
    val needsPicfortServerStep: Boolean get() = frameSettingsStore.needsSetupStep
}
