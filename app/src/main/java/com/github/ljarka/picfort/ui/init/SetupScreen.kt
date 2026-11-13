package com.github.ljarka.picfort.ui.init

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.ljarka.picfort.core.ui.R as CoreUiR

/** The first screen (T-058): Immich and the Picfort server on one page; done → [onDone]. */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val viewModel: SetupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.step) { if (state.step == SetupStep.Done) onDone() }
    SetupContent(
        state = state,
        onImmichAddress = viewModel::onImmichAddress,
        onEmail = viewModel::onEmail,
        onPassword = viewModel::onPassword,
        onPicfortAddress = viewModel::onPicfortAddress,
        onPicfortApiKey = viewModel::onPicfortApiKey,
        onShowApiKey = viewModel::showApiKey,
        onConnect = viewModel::connect,
        onChangeImmich = viewModel::changeImmich,
        onSkipPicfort = viewModel::skipPicfort,
    )
}

@Composable
fun SetupContent(
    state: SetupUi,
    onImmichAddress: (String) -> Unit = {},
    onEmail: (String) -> Unit = {},
    onPassword: (String) -> Unit = {},
    onPicfortAddress: (String) -> Unit = {},
    onPicfortApiKey: (String) -> Unit = {},
    onShowApiKey: () -> Unit = {},
    onConnect: () -> Unit = {},
    onChangeImmich: () -> Unit = {},
    onSkipPicfort: () -> Unit = {},
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(CoreUiR.drawable.icon), contentDescription = null, modifier = Modifier.size(56.dp))
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("Welcome to Picfort", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Your family's photos, wall, lists and events.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            SectionTitle("Immich", "Your photo library and your account in it.")
            if (state.signedIn) {
                SignedIn(state, onChangeImmich)
            } else {
                AddressField(
                    value = state.immichAddress,
                    onValueChange = onImmichAddress,
                    label = "Immich address",
                    placeholder = "192.168.0.10:2283 or photos.example.com",
                    target = immichTarget(state.immichAddress),
                    error = state.immichError,
                    enabled = !state.busy,
                )
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmail,
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPassword,
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !state.busy,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = state.loginError != null,
                    supportingText = state.loginError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (state.askPicfort) ImeAction.Next else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConnect() }),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            if (state.askPicfort) {
                HorizontalDivider(Modifier.padding(vertical = 24.dp))
                SectionTitle(
                    "Picfort server · optional",
                    "Family wall, shopping lists, events and the picture frame. You can add it later in Settings.",
                )
                AddressField(
                    value = state.picfortAddress,
                    onValueChange = onPicfortAddress,
                    label = "Picfort server address",
                    placeholder = "192.168.0.10:5000 or picfort.example.com",
                    target = picfortTarget(state.picfortAddress),
                    error = state.picfortError,
                    enabled = !state.busy,
                )
                val suggestion = picfortSuggestion(state.immichAddress)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (state.picfortAddress.isBlank() && suggestion != null) {
                        AssistChip(
                            onClick = { onPicfortAddress(suggestion) },
                            enabled = !state.busy,
                            label = { Text("Same computer: $suggestion") },
                        )
                    }
                    if (!state.showApiKey) {
                        TextButton(onClick = onShowApiKey, enabled = !state.busy) { Text("Add API key") }
                    }
                }
                if (state.showApiKey) {
                    OutlinedTextField(
                        value = state.picfortApiKey,
                        onValueChange = onPicfortApiKey,
                        label = { Text("API key") },
                        singleLine = true,
                        enabled = !state.busy,
                        supportingText = { Text("Only if the server has PICFORT_API_KEY set, e.g. behind a tunnel") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onConnect, enabled = state.canConnect, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                if (state.busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(state.step.label)
                } else {
                    Text(if (state.signedIn) "Try again" else "Connect")
                }
            }
            if (state.signedIn && state.picfortError != null) {
                TextButton(onClick = onSkipPicfort, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Skip the Picfort server for now")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )
}

/** An address field that says what it will connect to (https:// unless on the local network). */
@Composable
private fun AddressField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    target: String?,
    error: String?,
    enabled: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = when {
            error != null -> ({ Text(error) })
            target != null -> ({ Text("Will connect to $target") })
            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SignedIn(state: SetupUi, onChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Signed in as ${state.email.trim()}", style = MaterialTheme.typography.bodyLarge)
            Text(
                immichTarget(state.immichAddress).orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onChange, enabled = !state.busy) { Text("Change") }
    }
}
