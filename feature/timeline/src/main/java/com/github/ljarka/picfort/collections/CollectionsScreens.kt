package com.github.ljarka.picfort.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.avatar.MemberCarousel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions,
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
fun AlbumsScreen(onBack: () -> Unit, onAlbumClick: (id: String, name: String, canDelete: Boolean) -> Unit) {
    val viewModel: AlbumsViewModel = hiltViewModel()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    AlbumsContent(albums, viewModel::thumbnail, onBack, viewModel::reload) { album ->
        onAlbumClick(album.id, album.albumName, viewModel.canDelete(album))
    }
}

@Composable
fun AlbumsContent(
    albums: LoadState<List<AlbumDto>>,
    thumbnail: (AlbumDto) -> String?,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onAlbumClick: (AlbumDto) -> Unit = {},
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
                    Column(Modifier.clickable { onAlbumClick(album) }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.LightGray.copy(alpha = 0.3f)),
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
    val delete by viewModel.delete.collectAsStateWithLifecycle()
    LaunchedEffect(delete.deleted) { if (delete.deleted) onBack() }
    AlbumContent(
        name = viewModel.name,
        assets = assets,
        thumbnail = viewModel::thumbnail,
        onBack = onBack,
        onRetry = viewModel::reload,
        onAssetClick = { asset, index -> onAssetClick(asset, index, viewModel.source) },
        canDelete = viewModel.canDelete,
        delete = delete,
        onDelete = viewModel::deleteAlbum,
        onDeleteDismissed = viewModel::deleteErrorShown,
    )
}

@Composable
fun AlbumContent(
    name: String,
    assets: LoadState<List<GridAsset>>,
    thumbnail: (GridAsset) -> String,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onAssetClick: (GridAsset, Int) -> Unit = { _, _ -> },
    /** The menu with "Delete album": only for the album's owner (T-070). */
    canDelete: Boolean = false,
    delete: AlbumDeleteUi = AlbumDeleteUi(),
    onDelete: () -> Unit = {},
    onDeleteDismissed: () -> Unit = {},
    /** Opens the confirmation at once (screenshots). */
    confirmingDelete: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(confirmingDelete) }
    BackScaffold(
        name,
        onBack,
        actions = {
            if (canDelete) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Album options")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Delete album") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menu = false
                                confirm = true
                            },
                        )
                    }
                }
            }
        },
    ) { modifier ->
        LoadStateContent(assets, onRetry, modifier, { it.isEmpty() }, "This album is empty") { list ->
            AssetGrid(
                assets = list,
                thumbnailUrl = thumbnail,
                onClick = { index -> onAssetClick(list[index], index) },
                modifier = modifier,
            )
        }
    }
    if (confirm && !delete.deleted) {
        DeleteAlbumDialog(
            name = name,
            delete = delete,
            onConfirm = onDelete,
            onDismiss = {
                confirm = false
                onDeleteDismissed()
            },
        )
    }
}

@Composable
private fun DeleteAlbumDialog(name: String, delete: AlbumDeleteUi, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!delete.deleting) onDismiss() },
        title = { Text("Delete album “${name.ifBlank { "Untitled" }}”?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Its photos and videos stay in Immich and on the timeline. " +
                        "A shared album goes for everyone it's shared with.",
                )
                delete.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !delete.deleting) {
                Text(
                    if (delete.deleting) "Deleting…" else "Delete",
                    color = if (delete.deleting) Color.Unspecified else MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !delete.deleting) { Text("Cancel") }
        },
    )
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
        personThumbnail = viewModel::personThumbnail,
        onTogglePerson = viewModel::togglePerson,
        onAssetClick = { asset, index -> onAssetClick(asset, index, viewModel.source) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    state: SearchUi,
    thumbnail: (GridAsset) -> String,
    onBack: () -> Unit = {},
    onSearch: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onAssetClick: (GridAsset, Int) -> Unit = { _, _ -> },
    personThumbnail: (String) -> String? = { null },
    /** A person tapped above the results, with the text in the field. */
    onTogglePerson: (id: String, query: String) -> Unit = { _, _ -> },
) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    var showAllPeople by rememberSaveable { mutableStateOf(false) }
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
            if (state.people.isNotEmpty()) {
                val accent = MaterialTheme.colorScheme.primary
                // Many people in Immich: those in most photos, then the ones picked under "All people".
                val row = remember(state.people, state.addedToRow, accent) {
                    peopleRow(state.people, state.addedToRow)
                        .map { FamilyMember(it.id, it.name, personThumbnail(it.id), accent) }
                }
                // Someone picked under "All people" joins at the end: scroll there so they're seen.
                val rowState = rememberLazyListState()
                var addedSeen by rememberSaveable { mutableIntStateOf(state.addedToRow.size) }
                LaunchedEffect(state.addedToRow.size) {
                    if (state.addedToRow.size > addedSeen) rowState.animateScrollToItem(row.lastIndex)
                    addedSeen = state.addedToRow.size
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                ) {
                    Text(
                        "People",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.people.size > row.size) {
                        TextButton(onClick = {
                            keyboard?.hide()
                            showAllPeople = true
                        }) {
                            Text("All people (${state.people.size})")
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
                MemberCarousel(
                    members = row,
                    selectedId = null,
                    selectedIds = state.selectedPeople,
                    onSelect = { id ->
                        keyboard?.hide()
                        if (id != null) onTogglePerson(id, query)
                    },
                    onEditMember = null,
                    state = rowState,
                )
            }
            if (showAllPeople) {
                ModalBottomSheet(
                    onDismissRequest = { showAllPeople = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                ) {
                    PeoplePickerContent(
                        people = state.people,
                        selected = state.selectedPeople,
                        personThumbnail = personThumbnail,
                        onToggle = { onTogglePerson(it, query) },
                        onDone = { showAllPeople = false },
                    )
                }
            }
            when {
                state.error != null && state.results.isEmpty() ->
                    Message(checkNotNull(state.error), Modifier) { onSearch(state.query) }

                state.isLoading && state.results.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

                state.searched && state.results.isEmpty() ->
                    Message(state.nothingFound(), Modifier)

                !state.searched -> Message(
                    if (state.people.isEmpty()) {
                        "Search your Immich library by describing what's in the photo."
                    } else {
                        "Describe what's in the photo, or pick people above."
                    },
                    Modifier,
                )

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
