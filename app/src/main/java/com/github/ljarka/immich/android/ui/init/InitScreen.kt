package com.github.ljarka.immich.android.ui.init

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.ljarka.immich.android.R
import com.github.ljarka.immich.android.ui.login.LoginContent
import com.github.ljarka.immich.android.ui.server.ServerConfigurationContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitScreen(
    onInitialized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Configuration")
                },
                navigationIcon = {
                    Image(
                        painter = painterResource(R.drawable.icon),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(40.dp),
                    )
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val viewModel: InitViewModel = hiltViewModel()
            Image(
                modifier = Modifier
                    .padding(16.dp)
                    .size(200.dp),
                painter = painterResource(R.drawable.icon),
                contentDescription = null
            )

            val state = viewModel.initState.collectAsStateWithLifecycle()

            when (state.value) {
                InitState.INITIALIZED -> {
                    LaunchedEffect(Unit) {
                        onInitialized()
                    }
                }

                InitState.REQUIRES_LOGIN -> {
                    LoginContent(
                        onLoggedIn = { viewModel.updateState() },
                        onChangeServer = { viewModel.updateState() },
                    )
                }

                InitState.REQUIRES_SERVER_CONFIGURATION -> {
                    ServerConfigurationContent(
                        onServerConfigured = {
                            viewModel.updateState()
                        }
                    )
                }

                InitState.UNKNOWN -> {
                    // Loading
                }
            }
        }
    }
}