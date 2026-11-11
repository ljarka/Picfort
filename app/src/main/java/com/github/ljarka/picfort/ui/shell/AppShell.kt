package com.github.ljarka.picfort.ui.shell

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.ljarka.picfort.R
import com.github.ljarka.picfort.ui.frame.FrameSettingsDialog
import com.github.ljarka.picfort.ui.frame.FrameSettingsStore
import com.github.ljarka.picfort.ui.frame.normalizeFrameUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** The five bottom-bar tabs (T-017), in bar order. Daily family things first, photos last. */
enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    WALL("Wall", R.drawable.ic_forum),
    SHOPPING("Shopping", R.drawable.ic_shopping_cart),
    EVENTS("Events", R.drawable.ic_event),
    SCHOOL("School", R.drawable.ic_school),
    PHOTOS("Photos", R.drawable.ic_photo_library),
}

/** M3 navigation bar height, without the system navigation bar below it. */
private val TAB_BAR_HEIGHT = 80.dp

/**
 * The bottom bar. Labels are always shown and read by TalkBack (icons have no description);
 * the collection semantics make TalkBack say "tab, 2 of 5".
 */
@Composable
fun TabBar(selected: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(
        modifier.semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = Tab.entries.size) },
    ) {
        Tab.entries.forEachIndexed { index, tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(ImageVector.vectorResource(tab.icon), contentDescription = null) },
                // labelSmall (11 sp) instead of the default 12 sp: "Shopping" must fit a fifth of
                // a 360 dp screen at font scale 1.3.
                label = { Text(tab.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelSmall) },
                alwaysShowLabel = true,
                modifier = Modifier.semantics {
                    collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1)
                },
            )
        }
    }
}

/**
 * Wraps a tab root. The bar is drawn over the screens, so a tab root leaves room for it: its
 * FAB, snackbars and last row stay above the bar, and the bar's insets are consumed so inner
 * Scaffolds don't add the navigation bar a second time. The room is kept while the bar is
 * hidden (photo viewer), so the timeline doesn't resize during the shared-element transition.
 */
@Composable
fun TabRoot(content: @Composable () -> Unit) {
    val systemBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val room = PaddingValues(bottom = TAB_BAR_HEIGHT + systemBar)
    Box(
        Modifier
            .fillMaxSize()
            .padding(room)
            .consumeWindowInsets(room)
    ) { content() }
}

/** Settings action for every tab root's top bar. */
@Composable
fun SettingsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Default.Settings, contentDescription = "Settings")
    }
}

/** Shown by the family features when no Picfort server address is set. */
@Composable
fun ServerRequiredContent(message: String, onSetUpServer: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSetUpServer) { Text("Set up server") }
    }
}

@HiltViewModel
class ServerStatusViewModel @Inject constructor(
    private val store: FrameSettingsStore,
) : ViewModel() {
    var configured by mutableStateOf(isConfigured())
        private set

    fun recheck() {
        configured = isConfigured()
    }

    private fun isConfigured() = normalizeFrameUrl(store.frameUrl) != null
}

/** Shopping, Events and School until T-019, T-023 and T-029 replace them. */
@Composable
fun ComingSoonScreen(title: String, message: String, serverMessage: String, onSettingsClick: () -> Unit) {
    val viewModel = hiltViewModel<ServerStatusViewModel>()
    // Back from Settings, where the address may have changed.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.recheck() }
    var showServerDialog by remember { mutableStateOf(false) }
    if (showServerDialog) {
        FrameSettingsDialog(onDismiss = { showServerDialog = false }, onSaved = viewModel::recheck)
    }
    ComingSoonContent(
        title = title,
        configured = viewModel.configured,
        message = message,
        serverMessage = serverMessage,
        onSettingsClick = onSettingsClick,
        onSetUpServer = { showServerDialog = true },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComingSoonContent(
    title: String,
    configured: Boolean,
    message: String,
    serverMessage: String,
    onSettingsClick: () -> Unit = {},
    onSetUpServer: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title) }, actions = { SettingsAction(onSettingsClick) })
        },
    ) { padding ->
        if (configured) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    message,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            ServerRequiredContent(serverMessage, onSetUpServer, Modifier.padding(padding))
        }
    }
}
