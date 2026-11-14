package com.github.ljarka.picfort.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.network.normalizeServerUrl

/**
 * Immich's address on the home network for backup (T-064). Empty clears it. Shows what will be
 * used, as the setup screen does.
 */
@Composable
fun HomeAddressDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    // Opens focused with the old address selected: typing replaces it.
    var field by remember { mutableStateOf(TextFieldValue(current, TextRange(0, current.length))) }
    val text = field.text
    val url = normalizeServerUrl(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Home network address") },
        text = {
            Column {
                Text(
                    "Immich's address at home, e.g. 192.168.0.10:2283. On Wi-Fi at home, backup uploads " +
                        "there: faster, and large videos aren't cut off by the public address's limit.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    placeholder = { Text("192.168.0.10:2283") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    supportingText = {
                        when {
                            text.isBlank() -> Text("Empty: always the public address")
                            url == null -> Text("Not an address")
                            else -> Text("Will connect to $url")
                        }
                    },
                    isError = text.isNotBlank() && url == null,
                    modifier = Modifier.padding(top = 12.dp).focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isBlank() || url != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
