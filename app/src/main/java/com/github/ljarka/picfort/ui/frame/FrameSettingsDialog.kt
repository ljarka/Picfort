package com.github.ljarka.picfort.ui.frame

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Running : ConnectionTest
    data object Ok : ConnectionTest
    data class Failed(val message: String) : ConnectionTest
}

@HiltViewModel
class FrameSettingsViewModel @Inject constructor(
    private val store: FrameSettingsStore,
    private val repository: FrameRepository,
) : ViewModel() {

    private val _test = MutableStateFlow<ConnectionTest>(ConnectionTest.Idle)
    val test = _test.asStateFlow()

    val savedAddress: String get() = store.frameUrl

    fun isValid(address: String) = normalizeFrameUrl(address) != null

    fun resetTest() {
        _test.value = ConnectionTest.Idle
    }

    fun test(address: String) {
        _test.value = ConnectionTest.Running
        viewModelScope.launch {
            _test.value = when (val result = repository.testConnection(address)) {
                FrameResult.Sent -> ConnectionTest.Ok
                is FrameResult.Error -> ConnectionTest.Failed(result.message)
                else -> ConnectionTest.Failed("Unexpected response")
            }
        }
    }

    fun save(address: String) {
        store.frameUrl = normalizeFrameUrl(address)?.toString()?.trimEnd('/') ?: ""
    }
}

@Composable
fun FrameSettingsDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
) {
    val viewModel = hiltViewModel<FrameSettingsViewModel>()
    var address by remember { mutableStateOf(viewModel.savedAddress) }
    val test by viewModel.test.collectAsState()
    val valid = viewModel.isValid(address)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Picture frame") },
        text = {
            Column {
                Text(
                    "Address of the ImageFrameServer on your network.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        viewModel.resetTest()
                    },
                    singleLine = true,
                    label = { Text("Frame address") },
                    placeholder = { Text("192.168.0.105:5000") },
                    isError = address.isNotBlank() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(8.dp))
                val (message, color) = when (val t = test) {
                    ConnectionTest.Idle -> "" to MaterialTheme.colorScheme.onSurfaceVariant
                    ConnectionTest.Running -> "Connecting…" to MaterialTheme.colorScheme.onSurfaceVariant
                    ConnectionTest.Ok -> "Connected" to MaterialTheme.colorScheme.primary
                    is ConnectionTest.Failed -> t.message to MaterialTheme.colorScheme.error
                }
                Text(message, color = color, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    viewModel.save(address)
                    onSaved()
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(
                enabled = valid && test != ConnectionTest.Running,
                onClick = { viewModel.test(address) },
            ) { Text("Test") }
        },
    )
}
