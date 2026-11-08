package com.github.ljarka.immich.android.ui.server

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ColumnScope.ServerConfigurationContent(
    onServerConfigured: () -> Unit = {},
) {
    val viewModel: ServerConfigurationViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var serverUrl by rememberSaveable { mutableStateOf("") }
    val checking = state == ServerCheckState.Checking

    LaunchedEffect(state) {
        if (state == ServerCheckState.Saved) onServerConfigured()
    }

    TextField(
        label = { Text(text = "Server URL") },
        placeholder = { Text("192.168.0.10:2283 or photos.example.com") },
        value = serverUrl,
        singleLine = true,
        enabled = !checking,
        isError = state is ServerCheckState.Error,
        supportingText = (state as? ServerCheckState.Error)?.let { { Text(it.message) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { viewModel.setServerUrl(serverUrl) }),
        onValueChange = {
            serverUrl = it
            viewModel.clearError()
        },
    )
    Button(
        modifier = Modifier.imePadding(),
        enabled = serverUrl.isNotBlank() && !checking,
        onClick = { viewModel.setServerUrl(serverUrl) },
    ) {
        if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text(text = "Connect")
    }
}
