package com.github.ljarka.immich.android.ui.login

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ColumnScope.LoginContent(
    onLoggedIn: () -> Unit,
    onChangeServer: () -> Unit,
) {
    val viewModel: LoginViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val loggingIn = state == LoginState.LoggingIn
    val canSubmit = email.isNotBlank() && password.isNotEmpty() && !loggingIn

    LaunchedEffect(state) {
        if (state is LoginState.LoggedIn) onLoggedIn()
    }

    Text(viewModel.serverUrl, style = MaterialTheme.typography.bodySmall)
    TextField(
        value = email,
        label = { Text("Email") },
        singleLine = true,
        enabled = !loggingIn,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        onValueChange = {
            email = it
            viewModel.clearError()
        },
    )
    TextField(
        value = password,
        label = { Text("Password") },
        singleLine = true,
        enabled = !loggingIn,
        visualTransformation = PasswordVisualTransformation(),
        isError = state is LoginState.Error,
        supportingText = (state as? LoginState.Error)?.let { { Text(it.message) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (canSubmit) viewModel.performLogin(email, password) }),
        onValueChange = {
            password = it
            viewModel.clearError()
        },
    )
    Button(
        onClick = { viewModel.performLogin(email, password) },
        enabled = canSubmit,
        modifier = Modifier.imePadding(),
    ) {
        if (loggingIn) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text(text = "Login")
    }
    TextButton(
        enabled = !loggingIn,
        onClick = {
            viewModel.changeServer()
            onChangeServer()
        },
    ) { Text("Change server") }
}
