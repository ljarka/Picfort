package com.github.ljarka.picfort.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding -> content(Modifier.padding(padding)) }
}

@Composable
internal fun <T> LoadStateContent(
    state: LoadState<T>,
    onRetry: () -> Unit,
    modifier: Modifier,
    empty: (T) -> Boolean,
    emptyText: String,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        LoadState.Loading -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is LoadState.Error -> Message(state.message, modifier, onRetry)
        is LoadState.Loaded -> if (empty(state.value)) Message(emptyText, modifier) else content(state.value)
    }
}

@Composable
internal fun Message(text: String, modifier: Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        onRetry?.let { Button(onClick = it) { Text("Retry") } }
    }
}

@Composable
fun AlbumsScreen(onBack: () -> Unit, onAlbumClick: (id: String, name: String) -> Unit) {
    val viewModel: AlbumsViewModel = hiltViewModel()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    AlbumsContent(albums, viewModel::thumbnail, onBack, viewModel::reload, onAlbumClick)
}

@Composable
fun AlbumsContent(
    albums: LoadState<List<AlbumDto>>,
    thumbnail: (AlbumDto) -> String?,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onAlbumClick: (id: String, name: String) -> Unit = { _, _ -> },
) {
    BackScaffold("Albums", onBack) { modifier ->
        LoadStateContent(albums, onRetry, modifier, { it.isEmpty() }, "No albums yet") { list ->
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                modifier = modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(list, key = { it.id }) { album ->
                    Column(Modifier.clickable { onAlbumClick(album.id, album.albumName) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.LightGray.copy(alpha = 0.3f))
                        ) {
                            thumbnail(album)?.let {
                                AsyncImage(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            }
                        }
                        Text(
                            album.albumName.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            "${album.assetCount} item${if (album.assetCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumScreen(onBack: () -> Unit, onAssetClick: (asset: GridAsset, index: Int, source: String) -> Unit) {
    val viewModel: AlbumViewModel = hiltViewModel()
    val assets by viewModel.assets.collectAsStateWithLifecycle()
    AlbumContent(viewModel.name, assets, viewModel::thumbnail, onBack, viewModel::reload) { asset, index ->
        onAssetClick(asset, index, viewModel.source)
    }
}

@Composable
fun AlbumContent(
    name: String,
    assets: LoadState<List<GridAsset>>,
    thumbnail: (GridAsset) -> String,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onAssetClick: (GridAsset, Int) -> Unit = { _, _ -> },
) {
    BackScaffold(name, onBack) { modifier ->
        LoadStateContent(assets, onRetry, modifier, { it.isEmpty() }, "This album is empty") { list ->
            AssetGrid(
                assets = list,
                thumbnailUrl = thumbnail,
                onClick = { index -> onAssetClick(list[index], index) },
                modifier = modifier,
            )
        }
    }
}

@Composable
fun SearchScreen(onBack: () -> Unit, onAssetClick: (asset: GridAsset, index: Int, source: String) -> Unit) {
    val viewModel: SearchViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchContent(
        state = state,
        thumbnail = viewModel::thumbnail,
        onBack = onBack,
        onSearch = viewModel::search,
        onLoadMore = viewModel::loadMore,
        onAssetClick = { asset, index -> onAssetClick(asset, index, viewModel.source) },
    )
}

@Composable
fun SearchContent(
    state: SearchUi,
    thumbnail: (GridAsset) -> String,
    onBack: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onAssetClick: (GridAsset, Int) -> Unit = { _, _ -> },
) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) { if (!state.searched) focus.requestFocus() }
    // Load the next page when the user gets close to the end.
    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= gridState.layoutInfo.totalItemsCount - 12
        }
    }
    LaunchedEffect(nearEnd, state.results.size) { if (nearEnd && state.results.isNotEmpty()) onLoadMore() }

    BackScaffold("Search", onBack) { modifier ->
        Column(modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Try \"beach at sunset\" or \"dog\"") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboard?.hide()
                    onSearch(query)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .focusRequester(focus),
            )
            when {
                state.error != null && state.results.isEmpty() ->
                    Message(checkNotNull(state.error), Modifier) { onSearch(state.query) }
                state.isLoading && state.results.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.searched && state.results.isEmpty() ->
                    Message("Nothing found for \"${state.query}\"", Modifier)
                !state.searched ->
                    Message("Search your Immich library by describing what's in the photo.", Modifier)
                else -> AssetGrid(
                    assets = state.results,
                    thumbnailUrl = thumbnail,
                    onClick = { index -> onAssetClick(state.results[index], index) },
                    state = gridState,
                )
            }
        }
    }
}
