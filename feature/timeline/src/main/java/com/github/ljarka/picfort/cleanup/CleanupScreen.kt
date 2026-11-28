package com.github.ljarka.picfort.cleanup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.collections.GridAsset

@Composable
fun CleanupScreen(onBack: () -> Unit, onAssetClick: (asset: GridAsset, index: Int, source: String) -> Unit) {
    val viewModel: CleanupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CleanupContent(
        state = state,
        thumbnail = viewModel::thumbnail,
        onBack = onBack,
        onFilter = viewModel::setFilter,
        onRefresh = viewModel::refresh,
        onOpenAsset = { index, assetId ->
            onAssetClick(GridAsset(assetId), index, CLEANUP_VIEWER_SOURCE)
        },
        onToggleSelect = viewModel::toggleSelect,
        onClearSelection = viewModel::clearSelection,
        onKeep = viewModel::keep,
        onTrashOne = { viewModel.trash(listOf(it)) },
        onTrashSelected = viewModel::trashSelected,
        onMessageShown = viewModel::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CleanupContent(
    state: CleanupUi,
    thumbnail: (String) -> String,
    onBack: () -> Unit,
    onFilter: (CleanupFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenAsset: (index: Int, assetId: String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onClearSelection: () -> Unit,
    onKeep: (String) -> Unit,
    onTrashOne: (String) -> Unit,
    onTrashSelected: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    var confirmBulkTrash by remember { mutableStateOf(false) }
    if (confirmBulkTrash) {
        AlertDialog(
            onDismissRequest = { confirmBulkTrash = false },
            title = { Text("Move ${state.selectedIds.size} photos to Immich trash?") },
            text = { Text("They stay in Immich's trash until you empty it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmBulkTrash = false
                    onTrashSelected()
                }) { Text("Move to trash") }
            },
            dismissButton = { TextButton(onClick = { confirmBulkTrash = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (state.selectedIds.isNotEmpty()) {
                TopAppBar(
                    title = { Text("${state.selectedIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) { Icon(Icons.Default.Close, "Clear selection") }
                    },
                    actions = {
                        IconButton(onClick = { confirmBulkTrash = true }, enabled = !state.busy) {
                            Icon(Icons.Default.Delete, "Move selected to trash")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Clean up") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CleanupFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { onFilter(filter) },
                        label = { Text(filter.label) },
                    )
                }
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRefresh) { Text("Retry") }
                    }
                }

                state.items.isEmpty() -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Nothing to clean up — your library looks great!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.assetId }) { index, item ->
                        CleanupCandidateCard(
                            item = item,
                            thumbnailUrl = thumbnail(item.assetId),
                            selected = item.assetId in state.selectedIds,
                            busy = state.busy,
                            onPreview = { onOpenAsset(index, item.assetId) },
                            onToggleSelect = { onToggleSelect(item.assetId) },
                            onKeep = { onKeep(item.assetId) },
                            onTrash = { onTrashOne(item.assetId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CleanupCandidateCard(
    item: CleanupCandidateDto,
    thumbnailUrl: String,
    selected: Boolean,
    busy: Boolean,
    onPreview: () -> Unit,
    onToggleSelect: () -> Unit,
    onKeep: () -> Unit,
    onTrash: () -> Unit,
) {
    val border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleSelect),
        border = border,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = item.scene.ifBlank { "Photo candidate" },
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onPreview),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.scene.ifBlank { item.category.replace('_', ' ') },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(
                    "Quality ${item.quality}/5",
                    item.cleanupReason.takeIf { it.isNotBlank() } ?: item.category.takeIf { it != "photo" },
                ).joinToString(" · ")
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onKeep, enabled = !busy) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Keep")
                    }
                    FilledTonalButton(onClick = onTrash, enabled = !busy) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Trash")
                    }
                }
            }
        }
    }
}
