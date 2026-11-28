package com.github.ljarka.picfort.memories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.collections.GridAsset

private val PLACEHOLDER = Color.LightGray.copy(alpha = 0.3f)

/** Immich memories, opened from the frame card on the Family Wall (T-096). */
@Composable
fun MemoriesScreen(onBack: () -> Unit, onAssetClick: (asset: GridAsset, index: Int, source: String) -> Unit) {
    val viewModel: MemoriesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    MemoriesContent(
        state = state,
        thumbnail = viewModel::thumbnail,
        preview = viewModel::preview,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onOpen = { memory, index -> onAssetClick(memory.assets[index], index, memory.source) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoriesContent(
    state: MemoriesUi,
    thumbnail: (id: String, isVideo: Boolean) -> String,
    preview: (id: String, isVideo: Boolean) -> String,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpen: (memory: MemoryUi, index: Int) -> Unit = { _, _ -> },
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Memories") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !state.loading) { Icon(Icons.Default.Refresh, "Refresh") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.sections.isEmpty() -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.error != null && state.sections.isEmpty() -> Message(
                    title = "Couldn't load memories",
                    text = state.error,
                    action = "Retry",
                    onAction = onRefresh,
                )

                state.sections.isEmpty() -> Message(
                    title = "No memories right now",
                    text = "Immich puts together \"On this day\" memories every night from photos taken on " +
                        "this date in past years. Check back tomorrow.",
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    state.sections.forEach { section ->
                        item(key = "section-${section.title}") {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        section.memories.forEach { memory ->
                            item(key = memory.id) {
                                MemoryCard(memory, thumbnail, preview, onOpen = { index -> onOpen(memory, index) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryUi,
    thumbnail: (id: String, isVideo: Boolean) -> String,
    preview: (id: String, isVideo: Boolean) -> String,
    onOpen: (index: Int) -> Unit,
) {
    val cover = memory.assets.first()
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(24.dp))
                .background(PLACEHOLDER)
                .clickable { onOpen(0) },
        ) {
            AsyncImage(
                model = preview(cover.id, cover.isVideo),
                contentDescription = memory.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text(
                    memory.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    memory.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            if (memory.isSaved) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = "Saved",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(18.dp),
                    )
                }
            }
        }
        if (memory.assets.size > 1) {
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(memory.assets.drop(1), key = { _, a -> a.id }) { i, asset ->
                    Box(
                        Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PLACEHOLDER)
                            .clickable { onOpen(i + 1) },
                    ) {
                        AsyncImage(
                            model = thumbnail(asset.id, asset.isVideo),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (asset.isVideo) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center).size(28.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}
