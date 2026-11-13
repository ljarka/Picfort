package com.github.ljarka.picfort.ui.init

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/** Signed in → the app; otherwise the one-page setup (T-058): Immich and the Picfort server. */
@Composable
fun InitScreen(
    /** Logged in; true when the one-time Picfort server step should come before the timeline. */
    onInitialized: (picfortServerStep: Boolean) -> Unit,
) {
    val viewModel: InitViewModel = hiltViewModel()
    if (viewModel.signedIn) {
        LaunchedEffect(Unit) { onInitialized(viewModel.needsPicfortServerStep) }
    } else {
        SetupScreen(onDone = { onInitialized(viewModel.needsPicfortServerStep) })
    }
}
