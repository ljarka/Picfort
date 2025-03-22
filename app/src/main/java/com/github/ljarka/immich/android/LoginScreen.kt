package com.github.ljarka.immich.android

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var login by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }

        Image(
            painter = painterResource(R.drawable.immich_logo),
            contentDescription = null
        )
        TextField(
            value = login,
            label = {
                Text("Email")
            },
            onValueChange = {
                login = it
            })
        TextField(
            value = password,
            label = {
                Text("Password")
            },
            onValueChange = {
                password = it
            })
        Button(onClick = onAccept) {
            Text(text = "Login")
        }
    }
}