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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
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

/** Floating navigation bar height and outer vertical spacing. */
private val FLOATING_BAR_HEIGHT = 64.dp
private val FLOATING_BAR_MARGIN_BOTTOM = 8.dp
private val TAB_BAR_TOTAL_HEIGHT = FLOATING_BAR_HEIGHT + (FLOATING_BAR_MARGIN_BOTTOM * 2)

/**
 * The floating bottom navigation bar.
 * Designed as a capsule hovering above the bottom edge with rounded corners,
 * subtle surface elevation/shadow, and an animated selection indicator for the active tab.
 */
@Composable
fun TabBar(selected: Tab?, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = FLOATING_BAR_MARGIN_BOTTOM)
            .fillMaxWidth()
            .height(FLOATING_BAR_HEIGHT)
            .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = Tab.entries.size) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEachIndexed { index, tab ->
                val isSelected = tab == selected
                val interactionSource = remember { MutableInteractionSource() }

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    animationSpec = tween(durationMillis = 200),
                    label = "tabContainerColor",
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(durationMillis = 200),
                    label = "tabContentColor",
                )

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = containerColor,
                    contentColor = contentColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelect(tab) },
                        )
                        .semantics {
                            this.selected = isSelected
                            collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1)
                        },
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(tab.icon),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wraps a tab root. Content draws edge-to-edge behind the floating navigation bar.
 */
@Composable
fun TabRoot(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}

/** Settings action for every tab root's top bar. */
@Composable
fun SettingsAction(onClick: () -> Unit) =
    com.github.ljarka.picfort.core.ui.component.SettingsAction(onClick)

/** Shown by the family features when no Picfort server address is set. */
@Composable
fun ServerRequiredContent(message: String, onSetUpServer: () -> Unit, modifier: Modifier = Modifier) =
    com.github.ljarka.picfort.core.ui.component.ServerRequiredContent(message, onSetUpServer, modifier)

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
