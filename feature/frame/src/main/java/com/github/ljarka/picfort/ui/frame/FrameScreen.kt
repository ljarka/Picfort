package com.github.ljarka.picfort.ui.frame

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun FrameScreen(onBack: () -> Unit) {
    val viewModel: FrameViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAddress by remember { mutableStateOf(false) }
    if (showAddress) {
        FrameSettingsDialog(onDismiss = { showAddress = false }, onSaved = viewModel::refresh)
    }
    FrameContent(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onEditAddress = { showAddress = true },
        onNext = viewModel::next,
        onMode = viewModel::setMode,
        onAlbum = viewModel::setAlbum,
        onInterval = viewModel::setInterval,
        onQuietHours = viewModel::setQuietHours,
        onMessageShown = viewModel::messageShown,
    )
}

private val MODES = listOf("off" to "Off", "album" to "Album", "memories" to "On this day", "random" to "Random")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameContent(
    state: FrameUi,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onEditAddress: () -> Unit = {},
    onNext: () -> Unit = {},
    onMode: (String) -> Unit = {},
    onAlbum: (String) -> Unit = {},
    onInterval: (Int) -> Unit = {},
    onQuietHours: (Int?, Int?) -> Unit = { _, _ -> },
    onMessageShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Picture frame") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Refresh") }
                    IconButton(onClick = onEditAddress) { Icon(Icons.Default.Settings, "Picfort server address") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                !state.configured -> Centered(
                    "Connect Picfort to your e-paper frame to send photos and run a slideshow.",
                    "Set server address",
                    onEditAddress,
                )

                state.loading && state.image == null -> Box(Modifier.padding(64.dp)) { CircularProgressIndicator() }

                state.error != null -> Centered(state.error, "Retry", onRefresh)

                else -> Dashboard(state, onNext, onMode, onAlbum, onInterval, onQuietHours)
            }
        }
    }
}

@Composable
private fun Centered(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAction) { Text(action) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    state: FrameUi,
    onNext: () -> Unit,
    onMode: (String) -> Unit,
    onAlbum: (String) -> Unit,
    onInterval: (Int) -> Unit,
    onQuietHours: (Int?, Int?) -> Unit,
) {
    val slideshow = state.slideshow
    // The panel as it hangs on the wall: a white mat around the picture.
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF2B2B2B),
        shadowElevation = 6.dp,
        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp).widthIn(max = 260.dp).fillMaxWidth(0.62f),
    ) {
        Box(
            Modifier
                .padding(10.dp)
                .background(Color(0xFFF2F0EA))
                .padding(14.dp)
                .aspectRatio(3f / 4f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            state.image?.let {
                Image(
                    it,
                    "On the frame",
                    Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)),
                    contentScale = ContentScale.Fit,
                )
            } ?: Text("Nothing shown yet", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (state.busy) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.6f)) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        Text("  Refreshing…", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    Text(
        listOfNotNull(
            nextChangeLabel(slideshow),
            "Preview only, no panel attached".takeIf { !state.hardware },
        ).joinToString(" · ").ifEmpty { if (slideshow.mode == "off") "Slideshow off" else "" },
        style = MaterialTheme.typography.bodyMedium,
    )
    state.panelError?.let {
        Text(
            it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(8.dp),
        )
    }
    OutlinedButton(
        onClick = onNext,
        enabled = slideshow.mode != "off" && !state.busy,
        modifier = Modifier.padding(8.dp),
    ) { Text("Next photo") }

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Slideshow", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            MODES.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = slideshow.mode == mode,
                    onClick = { onMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, MODES.size),
                    label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
    }

    if (slideshow.mode == "album") {
        var open by remember { mutableStateOf(false) }
        val current = state.albums.firstOrNull { it.id == slideshow.albumId }
        Box(Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Album") },
                supportingContent = { Text(current?.name ?: "Choose an album") },
                trailingContent = { TextButton(onClick = { open = true }) { Text("Change") } },
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                state.albums.forEach { album ->
                    DropdownMenuItem(text = { Text(album.name) }, onClick = {
                        open = false
                        onAlbum(album.id)
                    })
                }
            }
        }
    }

    if (slideshow.mode != "off") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Change every", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                slideshow.intervals.filter { it in listOf(15, 60, 360, 1440) || it == slideshow.intervalMinutes }
                    .forEach { minutes ->
                        FilterChip(
                            selected = slideshow.intervalMinutes == minutes,
                            onClick = { onInterval(minutes) },
                            label = { Text(intervalLabel(minutes)) },
                        )
                    }
            }
        }
        val quietStart = slideshow.quietStart
        val quietEnd = slideshow.quietEnd
        val quiet = quietStart != null && quietEnd != null
        val quietText = if (quietStart != null && quietEnd != null) {
            "No changes ${hourLabel(quietStart)}–${hourLabel(quietEnd)}"
        } else {
            "Change photos around the clock"
        }
        ListItem(
            headlineContent = { Text("Quiet hours") },
            supportingContent = { Text(quietText) },
            trailingContent = {
                Switch(checked = quiet, onCheckedChange = { on ->
                    if (on) onQuietHours(22, 7) else onQuietHours(null, null)
                })
            },
        )
        if (quietStart != null && quietEnd != null) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                HourPicker("From", quietStart) { onQuietHours(it, quietEnd) }
                HourPicker("to", quietEnd) { onQuietHours(quietStart, it) }
            }
        }
    }
    Box(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun HourPicker(label: String, hour: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    Box {
        TextButton(onClick = { open = true }) { Text(hourLabel(hour)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (0..23).forEach { h ->
                DropdownMenuItem(text = { Text(hourLabel(h)) }, onClick = {
                    open = false
                    onChange(h)
                })
            }
        }
    }
}
