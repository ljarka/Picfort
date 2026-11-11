package com.github.ljarka.picfort.ui.frame

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.ui.wall.WallApi
import com.github.ljarka.picfort.core.network.PicfortServerException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Result of the Test button for the Picfort server (frame + Family Wall, T-002). */
sealed interface ConnectionTest {
    data object Idle : ConnectionTest
    data object Running : ConnectionTest
    /** Frame and Family Wall both work. */
    data class Ok(val message: String) : ConnectionTest
    /** The server answers, but the Family Wall doesn't work with this login. */
    data class Partial(val message: String) : ConnectionTest
    data class Failed(val message: String) : ConnectionTest
}

/** What to say once `/status` answered, given how `/wall` went (null = it worked). */
fun wallTestResult(error: Exception?): ConnectionTest = when (error) {
    null -> ConnectionTest.Ok("Connected: frame and Family Wall")
    is PicfortServerException.NotAccepted -> ConnectionTest.Partial(
        "Connected, but the server didn't accept your Immich login. " +
            "Check that it uses the same Immich server (IMMICH_URL)."
    )
    is PicfortServerException.NoWall -> ConnectionTest.Partial("Connected. This server has no Family Wall yet, update ImageFrameServer.")
    is PicfortServerException.ImmichUnreachable -> ConnectionTest.Partial("Connected, but the server can't reach Immich.")
    else -> ConnectionTest.Partial("Connected, but the Family Wall didn't answer: ${error.message}")
}

@HiltViewModel
class FrameSettingsViewModel @Inject constructor(
    private val store: FrameSettingsStore,
    private val repository: FrameRepository,
    private val wallApi: WallApi,
) : ViewModel() {

    private val _test = MutableStateFlow<ConnectionTest>(ConnectionTest.Idle)
    val test = _test.asStateFlow()

    val savedAddress: String get() = store.frameUrl
    val savedApiKey: String get() = store.apiKey

    fun isValid(address: String) = normalizeFrameUrl(address) != null

    fun resetTest() {
        _test.value = ConnectionTest.Idle
    }

    fun test(address: String, apiKey: String) {
        _test.value = ConnectionTest.Running
        viewModelScope.launch {
            _test.value = when (val status = repository.testConnection(address, apiKey)) {
                FrameResult.Sent -> try {
                    wallApi.posts(limit = 1, address = address, apiKey = apiKey)
                    wallTestResult(null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    wallTestResult(e)
                }
                is FrameResult.Error -> ConnectionTest.Failed(status.message)
                else -> ConnectionTest.Failed("Unexpected response")
            }
        }
    }

    fun save(address: String, apiKey: String) {
        store.frameUrl = normalizeFrameUrl(address)?.toString()?.trimEnd('/') ?: ""
        store.apiKey = apiKey
    }

    /** The first-run step was answered (Skip or Continue); don't show it again. */
    fun markPrompted() {
        store.serverPrompted = true
    }
}

/** Address and API key fields and the test result; shared by the dialog and the first-run step. */
@Composable
fun PicfortServerForm(
    address: String,
    onAddressChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    test: ConnectionTest,
    modifier: Modifier = Modifier,
) {
    val valid = normalizeFrameUrl(address) != null
    Column(modifier) {
        Text(
            "The Picfort server on your network keeps the family's wall, shopping lists, events and school " +
                "timetables, and runs the picture frame.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            singleLine = true,
            label = { Text("Server address") },
            placeholder = { Text("192.168.0.105:5000") },
            isError = address.isNotBlank() && !valid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            singleLine = true,
            label = { Text("API key (optional)") },
            supportingText = { Text("Only if the server has PICFORT_API_KEY set, e.g. behind a tunnel") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        val (message, color) = when (test) {
            ConnectionTest.Idle -> "" to MaterialTheme.colorScheme.onSurfaceVariant
            ConnectionTest.Running -> "Connecting…" to MaterialTheme.colorScheme.onSurfaceVariant
            is ConnectionTest.Ok -> test.message to MaterialTheme.colorScheme.primary
            is ConnectionTest.Partial -> test.message to MaterialTheme.colorScheme.tertiary
            is ConnectionTest.Failed -> test.message to MaterialTheme.colorScheme.error
        }
        Text(message, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun FrameSettingsDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
) {
    val viewModel = hiltViewModel<FrameSettingsViewModel>()
    var address by remember { mutableStateOf(viewModel.savedAddress) }
    var apiKey by remember { mutableStateOf(viewModel.savedApiKey) }
    val test by viewModel.test.collectAsState()
    PicfortServerDialog(
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
        onSave = {
            viewModel.save(address, apiKey)
            onSaved()
            onDismiss()
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun PicfortServerDialog(
    address: String,
    onAddressChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    test: ConnectionTest,
    onTest: () -> Unit = {},
    onSave: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val valid = normalizeFrameUrl(address) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Picfort server") },
        text = { PicfortServerForm(address, onAddressChange, apiKey, onApiKeyChange, test) },
        confirmButton = {
            TextButton(enabled = valid, onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(enabled = valid && test != ConnectionTest.Running, onClick = onTest) { Text("Test") }
        },
    )
}
