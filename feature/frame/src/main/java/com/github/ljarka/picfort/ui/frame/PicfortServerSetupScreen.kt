package com.github.ljarka.picfort.ui.frame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/** One-time, optional step after login when no Picfort server is set (T-002). */
@Composable
fun PicfortServerSetupScreen(onDone: () -> Unit) {
    val viewModel = hiltViewModel<FrameSettingsViewModel>()
    var address by remember { mutableStateOf(viewModel.savedAddress) }
    var apiKey by remember { mutableStateOf(viewModel.savedApiKey) }
    val test by viewModel.test.collectAsState()
    PicfortServerSetupContent(
        address = address,
        onAddressChange = {
            address = it
            viewModel.resetTest()
        },
        apiKey = apiKey,
        onApiKeyChange = {
            apiKey = it
            viewModel.resetTest()
        },
        test = test,
        onTest = { viewModel.test(address, apiKey) },
        onSkip = {
            viewModel.markPrompted()
            onDone()
        },
        onContinue = {
            viewModel.save(address, apiKey)
            viewModel.markPrompted()
            onDone()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PicfortServerSetupContent(
    address: String,
    onAddressChange: (String) -> Unit,
    test: ConnectionTest,
    apiKey: String = "",
    onApiKeyChange: (String) -> Unit = {},
    onTest: () -> Unit = {},
    onSkip: () -> Unit = {},
    onContinue: () -> Unit = {},
) {
    val valid = normalizeFrameUrl(address) != null
    Scaffold(topBar = { TopAppBar(title = { Text("Picfort server") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                "Enter your Picfort server's address. You can also set it later in Settings.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.padding(top = 16.dp))
            PicfortServerForm(address, onAddressChange, apiKey, onApiKeyChange, test)
            Spacer(Modifier.padding(top = 16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = valid && test != ConnectionTest.Running, onClick = onTest) { Text("Test") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSkip) { Text("Skip") }
                Button(enabled = valid, onClick = onContinue) { Text("Continue") }
            }
        }
    }
}
