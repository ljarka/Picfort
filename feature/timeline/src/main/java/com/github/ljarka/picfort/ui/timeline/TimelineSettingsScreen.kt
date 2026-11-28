package com.github.ljarka.picfort.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.cleanup.PhotoInsightsClient
import com.github.ljarka.picfort.collections.CollectionsRepository
import com.github.ljarka.picfort.collections.TagDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimelineSettingsUi(
    val tags: List<TagDto> = emptyList(),
    val loadingTags: Boolean = true,
    val tagsError: String? = null,
    val deletingTagId: String? = null,
    val cleanupCount: Int? = null,
    val message: String? = null,
)

@HiltViewModel
class TimelineSettingsViewModel @Inject constructor(
    private val timelineRepository: TimelineRepository,
    private val collections: CollectionsRepository,
    private val photoInsights: PhotoInsightsClient,
) : ViewModel() {
    val hideNonPhotos: StateFlow<Boolean> = timelineRepository.hideNonPhotos

    private val _state = MutableStateFlow(TimelineSettingsUi())
    val state: StateFlow<TimelineSettingsUi> = _state.asStateFlow()

    init {
        loadTags()
        loadCleanupStatus()
    }

    fun loadCleanupStatus() {
        if (!photoInsights.isConfigured) return
        viewModelScope.launch {
            val status = photoInsights.status()
            _state.update { it.copy(cleanupCount = status?.cleanupCount) }
        }
    }

    fun setHideNonPhotos(enabled: Boolean) = timelineRepository.setHideNonPhotos(enabled)

    fun loadTags() {
        _state.update { it.copy(loadingTags = true, tagsError = null) }
        viewModelScope.launch {
            try {
                val tags = collections.allTags()
                _state.update { it.copy(tags = tags, loadingTags = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingTags = false, tagsError = e.message ?: "Could not load tags") }
            }
        }
    }

    fun deleteTag(tag: TagDto) {
        if (_state.value.deletingTagId != null) return
        _state.update { it.copy(deletingTagId = tag.id) }
        viewModelScope.launch {
            try {
                collections.deleteTag(tag.id)
                _state.update { s ->
                    s.copy(
                        tags = s.tags.filterNot { it.id == tag.id },
                        deletingTagId = null,
                        message = "Deleted tag \"${tag.value.ifBlank { tag.name }}\"",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(deletingTagId = null, message = e.message ?: "Could not delete tag")
                }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

/** Settings -> Timeline: photo filtering, AI cleanup entry, and Immich tag management (T-092). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineSettingsScreen(onBack: () -> Unit, onCleanupClick: (() -> Unit)? = null) {
    val viewModel: TimelineSettingsViewModel = hiltViewModel()
    val hideNonPhotos by viewModel.hideNonPhotos.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TimelineSettingsContent(
        hideNonPhotos = hideNonPhotos,
        state = state,
        onBack = onBack,
        onCleanupClick = onCleanupClick,
        onHideNonPhotos = viewModel::setHideNonPhotos,
        onReloadTags = viewModel::loadTags,
        onDeleteTag = viewModel::deleteTag,
        onMessageShown = viewModel::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineSettingsContent(
    hideNonPhotos: Boolean,
    state: TimelineSettingsUi,
    onBack: () -> Unit = {},
    onCleanupClick: (() -> Unit)? = null,
    onHideNonPhotos: (Boolean) -> Unit = {},
    onReloadTags: () -> Unit = {},
    onDeleteTag: (TagDto) -> Unit = {},
    onMessageShown: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    var confirmDelete by remember { mutableStateOf<TagDto?>(null) }
    confirmDelete?.let { tag ->
        val label = tag.value.ifBlank { tag.name }
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete tag \"$label\"?") },
            text = {
                Text(
                    if (label.startsWith("ai/")) {
                        "The tag is removed from all photos in Immich; your photos stay. " +
                            "The Picfort server creates it again for photos it analyses from now on."
                    } else {
                        "The tag is removed from Immich; your photos stay."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    onDeleteTag(tag)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Timeline") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item(key = "section-filter") {
                Text(
                    "Display",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                ListItem(
                    modifier = Modifier.clickable { onHideNonPhotos(!hideNonPhotos) },
                    headlineContent = { Text("Hide screenshots & documents") },
                    supportingContent = {
                        Text("Skip screenshots, saved images and OCR-detected receipts on your timeline")
                    },
                    trailingContent = { Switch(checked = hideNonPhotos, onCheckedChange = onHideNonPhotos) },
                )
                if (onCleanupClick != null) {
                    val subtitle = when (val n = state.cleanupCount) {
                        null -> "Review low-quality photos, screenshots and receipts"
                        0 -> "No photos flagged for cleanup yet"
                        1 -> "1 photo flagged for review"
                        else -> "$n photos flagged for review"
                    }
                    ListItem(
                        modifier = Modifier.clickable(onClick = onCleanupClick),
                        headlineContent = { Text("Clean up photos") },
                        supportingContent = { Text(subtitle) },
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    "Search tags",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                Text(
                    "Tags in your Immich library shown as chips in Search. Delete unwanted tags here without touching your photos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            when {
                state.loadingTags -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.tagsError != null -> item(key = "error") {
                    Column(
                        Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.tagsError, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onReloadTags, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
                    }
                }

                state.tags.isEmpty() -> item(key = "empty") {
                    Text(
                        "No tags in Immich yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }

                else -> items(state.tags, key = { it.id }) { tag ->
                    val label = tag.value.ifBlank { tag.name }
                    ListItem(
                        headlineContent = { Text(label) },
                        trailingContent = {
                            IconButton(
                                onClick = { confirmDelete = tag },
                                enabled = state.deletingTagId == null,
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete $label")
                            }
                        },
                    )
                }
            }
        }
    }
}
