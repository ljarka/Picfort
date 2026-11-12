package com.github.ljarka.picfort.ui.wall

import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.avatar.MemberAvatar

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.core.ui.R
import com.github.ljarka.picfort.core.ui.component.ServerRequiredContent
import com.github.ljarka.picfort.core.ui.component.SettingsAction
import com.github.ljarka.picfort.core.ui.component.aboveFloatingTabBar
import java.io.File
import java.util.UUID

private val AVATAR_SIZE = 56.dp
private val CARD_WIDTH = 200.dp
/** Room for the header and two lines of text (90 dp from the spec fits only one). */
private val CARD_HEIGHT = 104.dp
private val HERO_HEIGHT = 220.dp
private val GRID_GUTTER = 12.dp
private val SPACING = 4.dp

@Composable
fun FamilyWallScreen(
    onSettingsClick: () -> Unit,
    onEditMember: (String) -> Unit = {},
    onOpenEvent: (Long) -> Unit = {},
    onOpenEvents: () -> Unit = {},
    onSetUpServer: () -> Unit = onSettingsClick,
) {
    val viewModel = hiltViewModel<FamilyWallViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() }
    }
    FamilyWallContent(
        state = state,
        onSettingsClick = onSettingsClick,
        onRefresh = viewModel::refreshNow,
        onSelect = viewModel::select,
        onEditMember = onEditMember,
        onOpenEvent = onOpenEvent,
        onOpenEvents = onOpenEvents,
        onPost = viewModel::post,
        onDelete = viewModel::delete,
        onTogglePin = viewModel::togglePin,
        onSetUpServer = onSetUpServer,
        onMessageShown = viewModel::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyWallContent(
    state: FamilyWallUiState,
    onSettingsClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onSelect: (String?) -> Unit = {},
    onEditMember: (String) -> Unit = {},
    onOpenEvent: (Long) -> Unit = {},
    onOpenEvents: () -> Unit = {},
    onPost: (String, Uri?) -> Unit = { _, _ -> },
    onDelete: (WallPost) -> Unit = {},
    onTogglePin: (WallPost) -> Unit = {},
    onSetUpServer: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    /** Fixed "now" for screenshot tests. */
    now: Long = System.currentTimeMillis(),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    var composing by rememberSaveable { mutableStateOf(false) }
    var openMessage by remember { mutableStateOf<WallPost?>(null) }
    var openPhoto by remember { mutableStateOf<WallPost?>(null) }
    var confirmDelete by remember { mutableStateOf<WallPost?>(null) }

    openMessage?.let { post ->
        val author = state.authorOf(post)
        AlertDialog(
            onDismissRequest = { openMessage = null },
            icon = { MemberAvatar(author, size = 40.dp) },
            title = { Text(author.name) },
            text = { Text(post.text.orEmpty()) },
            confirmButton = { TextButton(onClick = { openMessage = null }) { Text("Close") } },
        )
    }
    openPhoto?.let { post -> PhotoViewer(post, onDismiss = { openPhoto = null }) }
    confirmDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this post?") },
            text = { Text("It disappears from the wall for the whole family.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; onDelete(post) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
    if (composing) {
        ComposeSheet(
            onDismiss = { composing = false },
            onSend = { text, photo ->
                composing = false
                onPost(text, photo)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family Wall") },
                actions = { SettingsAction(onSettingsClick) },
            )
        },
        floatingActionButton = {
            if (state.configured) {
                FloatingActionButton(
                    onClick = { composing = true },
                    modifier = Modifier.aboveFloatingTabBar(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New post")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !state.configured -> ServerRequiredContent(
                    "Connect your Picfort server to use the Family Wall.", onSetUpServer,
                )
                state.isLoading && state.posts.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.loadError != null && state.posts.isEmpty() -> Centered(state.loadError, "Retry", onRefresh)
                else -> {
                    if (state.members.isNotEmpty()) {
                        AvatarRow(state.members, state.selectedMemberId, onSelect, onEditMember)
                    }
                    PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
                        WallGrid(
                            state = state,
                            now = now,
                            onOpenEvent = onOpenEvent,
                            onOpenEvents = onOpenEvents,
                            onMessageClick = { openMessage = it },
                            onPhotoClick = { openPhoto = it },
                            onDelete = { confirmDelete = it },
                            onTogglePin = onTogglePin,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WallGrid(
    state: FamilyWallUiState,
    now: Long,
    onOpenEvent: (Long) -> Unit,
    onOpenEvents: () -> Unit,
    onMessageClick: (WallPost) -> Unit,
    onPhotoClick: (WallPost) -> Unit,
    onDelete: (WallPost) -> Unit,
    onTogglePin: (WallPost) -> Unit,
) {
    val messages = state.messages
    val photos = state.photos
    // One scrolling container (T-002): the carousel and the hero photo are full-line items.
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = GRID_GUTTER, end = GRID_GUTTER, top = 8.dp, bottom = 88.dp),
        verticalItemSpacing = SPACING,
        horizontalArrangement = Arrangement.spacedBy(SPACING),
    ) {
        state.panel?.let { panel ->
            item(key = "today", span = StaggeredGridItemSpan.FullLine) {
                TodayPanel(
                    panel = panel,
                    onEventClick = onOpenEvent,
                    onMoreClick = onOpenEvents,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        if (messages.isNotEmpty()) {
            item(key = "messages", span = StaggeredGridItemSpan.FullLine) {
                MessageCarousel(
                    messages = messages,
                    state = state,
                    now = now,
                    onClick = onMessageClick,
                    onDelete = onDelete,
                    onTogglePin = onTogglePin,
                    modifier = Modifier
                        .bleed(GRID_GUTTER)
                        .padding(bottom = 12.dp),
                )
            }
        }
        if (messages.isEmpty() && photos.isEmpty()) {
            item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                Text(
                    if (state.selectedMemberId == null) "No posts yet. Write the first one!"
                    else "No posts from ${state.members.firstOrNull { it.id == state.selectedMemberId }?.name ?: "them"} yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp, horizontal = 16.dp),
                )
            }
        }
        itemsIndexed(
            photos,
            key = { _, post -> post.id },
            span = { index, _ -> if (index == 0) StaggeredGridItemSpan.FullLine else StaggeredGridItemSpan.SingleLane },
        ) { index, post ->
            PhotoTile(
                post = post,
                hero = index == 0,
                mine = state.isMine(post),
                onClick = { onPhotoClick(post) },
                onDelete = { onDelete(post) },
                onTogglePin = { onTogglePin(post) },
            )
        }
    }
}

/** Lets a full-line item draw edge to edge through the grid's side padding. */
private fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AvatarRow(
    members: List<FamilyMember>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onEditMember: (String) -> Unit = {},
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(members, key = { it.id }) { member ->
            val selected = member.id == selectedId
            var menu by remember { mutableStateOf(false) }
            Box {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) member.color.copy(alpha = 0.18f) else Color.Transparent)
                        .combinedClickable(
                            onClick = { onSelect(member.id) },
                            onLongClick = { menu = true },
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    MemberAvatar(member, size = AVATAR_SIZE, ring = if (selected) 3.dp else 2.dp)
                    Text(
                        member.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontWeight = if (selected) FontWeight.Bold else null,
                        modifier = Modifier
                            .width(AVATAR_SIZE + 8.dp)
                            .padding(top = 4.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menu = false; onEditMember(member.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCarousel(
    messages: List<WallPost>,
    state: FamilyWallUiState,
    now: Long,
    onClick: (WallPost) -> Unit,
    onDelete: (WallPost) -> Unit,
    onTogglePin: (WallPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages, key = { it.id }) { post ->
            MessageCard(
                post = post,
                author = state.authorOf(post),
                mine = state.isMine(post),
                now = now,
                onClick = { onClick(post) },
                onDelete = { onDelete(post) },
                onTogglePin = { onTogglePin(post) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageCard(
    post: WallPost,
    author: FamilyMember,
    mine: Boolean,
    now: Long,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .size(CARD_WIDTH, CARD_HEIGHT)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .background(author.color.copy(alpha = 0.15f))
                .combinedClickable(onClick = onClick, onLongClick = { if (mine) menu = true })
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MemberAvatar(author, size = 32.dp, ring = 1.5.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    author.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (post.pinned) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.ic_push_pin),
                        contentDescription = "Pinned",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    relativeTime(post, now),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                post.text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PostMenu(menu, post.pinned, onDismiss = { menu = false }, onDelete = onDelete, onTogglePin = onTogglePin)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    post: WallPost,
    hero: Boolean,
    mine: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (hero) Modifier.height(HERO_HEIGHT) else Modifier.aspectRatio(post.aspectRatio ?: 1f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = { if (mine) menu = true }),
    ) {
        AsyncImage(
            model = post.photoUrl,
            contentDescription = post.text,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (!post.text.isNullOrBlank()) {
            Text(
                post.text,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        if (post.pinned) {
            Icon(
                ImageVector.vectorResource(R.drawable.ic_push_pin),
                contentDescription = "Pinned",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    .padding(4.dp)
                    .size(14.dp),
            )
        }
        if (post.id < 0) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White)
        }
        PostMenu(menu, post.pinned, onDismiss = { menu = false }, onDelete = onDelete, onTogglePin = onTogglePin)
    }
}

@Composable
private fun PostMenu(
    expanded: Boolean,
    pinned: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (pinned) "Unpin" else "Pin") },
            leadingIcon = { Icon(ImageVector.vectorResource(R.drawable.ic_push_pin), null) },
            onClick = { onDismiss(); onTogglePin() },
        )
        DropdownMenuItem(
            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDelete() },
        )
    }
}

private fun relativeTime(post: WallPost, now: Long): String = when {
    post.id < 0 -> "Sending…"
    now - post.created.toEpochMilli() < DateUtils.MINUTE_IN_MILLIS -> "Just now"
    else -> DateUtils.getRelativeTimeSpanString(post.created.toEpochMilli(), now, DateUtils.MINUTE_IN_MILLIS).toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeSheet(onDismiss: () -> Unit, onSend: (String, Uri?) -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingCamera by rememberSaveable { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photo = uri
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) photo = pendingCamera
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
        ) {
            Text("New post", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Write something to the family…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            photo?.let { uri ->
                Spacer(Modifier.height(12.dp))
                Box {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Attached photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    IconButton(
                        onClick = { photo = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(ImageVector.vectorResource(R.drawable.ic_image), contentDescription = "Choose a photo")
                }
                IconButton(onClick = {
                    val dir = File(context.cacheDir, "wall").apply { mkdirs() }
                    val file = File(dir, "${UUID.randomUUID()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    pendingCamera = uri
                    camera.launch(uri)
                }) {
                    Icon(ImageVector.vectorResource(R.drawable.ic_photo_camera), contentDescription = "Take a photo")
                }
                Spacer(Modifier.weight(1f))
                Button(enabled = text.isNotBlank() || photo != null, onClick = { onSend(text, photo) }) {
                    Text("Post")
                }
            }
        }
    }
}

@Composable
private fun PhotoViewer(post: WallPost, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.clickable(onClick = onDismiss)) {
                AsyncImage(
                    model = post.photoUrl,
                    contentDescription = post.text,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                if (!post.text.isNullOrBlank()) {
                    Text(
                        post.text,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .navigationBarsPadding()
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Centered(text: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) { Text(action) }
    }
}
