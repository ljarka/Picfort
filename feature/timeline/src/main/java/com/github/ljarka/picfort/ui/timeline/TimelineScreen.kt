package com.github.ljarka.picfort.ui.timeline

import android.app.Activity
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.collections.AddToAlbumDialog
import com.github.ljarka.picfort.collections.GridAsset
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.ui.R
import com.github.ljarka.picfort.core.ui.component.aboveFloatingTabBar
import com.github.ljarka.picfort.local.RequestMediaPermission
import com.github.ljarka.picfort.ui.people.PeopleFilterRow
import com.github.ljarka.picfort.ui.people.PeopleFilterViewModel
import com.github.ljarka.picfort.ui.people.PersonPhotosContent
import com.github.ljarka.picfort.ui.timeline.layout.MonthHeaderRow
import com.github.ljarka.picfort.ui.timeline.layout.Tile
import com.github.ljarka.picfort.ui.timeline.layout.TilesRow
import com.github.ljarka.picfort.ui.timeline.layout.TimelineLayout
import com.github.ljarka.picfort.ui.timeline.layout.Viewport
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onImageClick: (String, AssetType, Int) -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onAlbumsClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Bumped when the Photos tab is reselected: scroll back to the newest photos. */
    scrollToTop: Int = 0,
    /** A photo in "Photos of Ola" (T-052): the viewer pages through [source]. */
    onPersonAssetClick: (asset: GridAsset, index: Int, source: String) -> Unit = { _, _, _ -> },
    /**
     * "Link" on a member who isn't linked yet, and long-press "Edit": their sheet in Family settings.
     * Null without a Picfort server (Family settings need it): no menu, no "Link".
     */
    onEditMember: ((String) -> Unit)? = null,
    /** The "+ Family" circle at the end of the carousel; null without a Picfort server. */
    onOpenFamily: (() -> Unit)? = null,
    /** Opened on a day (a birthday's year, T-056): scrolled there once, when the timeline is laid out. */
    scrollToDate: LocalDate? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        val viewModel: TimelineViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val syncLines by viewModel.syncLines.collectAsStateWithLifecycle()
        // Back from the background (another app, the Immich web UI…): sync again.
        LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForeground() }
        val listState = rememberLazyListState()
        val peopleViewModel: PeopleFilterViewModel = hiltViewModel()
        val people by peopleViewModel.people.collectAsStateWithLifecycle()
        val personPhotos by peopleViewModel.photos.collectAsStateWithLifecycle()
        LaunchedEffect(scrollToTop) {
            if (scrollToTop > 0) {
                peopleViewModel.clear()
                listState.animateScrollToItem(0)
            }
        }
        BackHandler(enabled = people.selectedId != null) { peopleViewModel.clear() }
        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.Hidden,
                skipHiddenState = false,
            ),
        )

        LaunchedEffect(viewModel) {
            viewModel.messages.collect { scaffoldState.snackbarHostState.showSnackbar(it) }
        }
        LaunchedEffect(peopleViewModel) {
            peopleViewModel.notLinked.collect { member ->
                val result = scaffoldState.snackbarHostState.showSnackbar(
                    message = "${member.name} isn't linked to your Immich people",
                    actionLabel = if (onEditMember != null) "Link" else null,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) onEditMember?.invoke(member.id)
            }
        }
        val header: (@Composable () -> Unit)? = if (people.members.isEmpty()) {
            null
        } else {
            { PeopleFilterRow(people, peopleViewModel::select, onEditMember, onOpenFamily) }
        }
        // Saved, so coming back to this tab later doesn't jump again.
        var scrolledToDate by rememberSaveable { mutableStateOf(false) }
        val timelineLayout = state.layout
        LaunchedEffect(scrollToDate, timelineLayout, scrolledToDate) {
            if (scrollToDate == null || scrolledToDate || timelineLayout == null) return@LaunchedEffect
            val row = timelineLayout.rowIndexOf(scrollToDate) ?: return@LaunchedEffect
            peopleViewModel.clear()
            listState.requestScrollToItem(row + if (header != null) 1 else 0)
            scrolledToDate = true
        }

        val context = LocalContext.current
        val trashLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result -> viewModel.onLocalTrashResult(result.resultCode == Activity.RESULT_OK) }
        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is TimelineEvent.Launch -> context.startActivity(event.intent)

                    is TimelineEvent.ConfirmLocalTrash ->
                        trashLauncher.launch(IntentSenderRequest.Builder(event.request).build())
                }
            }
        }

        // Phone photos only appear with media access; re-sync once it's granted.
        RequestMediaPermission(onGranted = viewModel::refresh)

        val selection by viewModel.selection.collectAsStateWithLifecycle()
        val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
        var confirmDelete by remember { mutableStateOf(false) }
        var chooseAlbum by remember { mutableStateOf(false) }
        if (chooseAlbum) {
            AddToAlbumDialog(
                onDismiss = { chooseAlbum = false },
                onAlbumChosen = {
                    chooseAlbum = false
                    viewModel.addToAlbum(it.id, it.albumName)
                },
                onNewAlbum = {
                    chooseAlbum = false
                    viewModel.addToAlbum(null, it, newAlbumName = it)
                },
            )
        }
        LaunchedEffect(selection.isEmpty()) {
            if (selection.isEmpty() && scaffoldState.bottomSheetState.isVisible) {
                scaffoldState.bottomSheetState.hide()
            }
        }
        if (confirmDelete) {
            val remote = selection.values.count { it.type == AssetType.REMOTE }
            val local = selection.size - remote
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete ${selection.size} item${if (selection.size == 1) "" else "s"}?") },
                text = {
                    Text(
                        listOfNotNull(
                            "$remote from Immich will be moved to Immich's trash.".takeIf { remote > 0 },
                            "$local on this phone will be moved to the phone's trash.".takeIf { local > 0 },
                        ).joinToString("\n"),
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    }) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            )
        }

        MenuClosedObserver(sheetState = scaffoldState.bottomSheetState) {
            viewModel.clearSelection()
        }
        val scrollBehavior = enterAlwaysScrollBehavior()
        BottomSheetScaffold(
            sheetPeekHeight = 0.dp,
            // Messages sit above the floating tab bar, not under it.
            snackbarHost = { SnackbarHost(it, Modifier.aboveFloatingTabBar()) },
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            scaffoldState = scaffoldState,
            sheetContent = {
                // The sheet slides up under the floating tab bar: its actions sit above it.
                Box(Modifier.navigationBarsPadding().aboveFloatingTabBar()) {
                    TimelineMenuContent(
                        selectedCount = selection.size,
                        isBusy = isBusy,
                        canSendToFrame = selection.size == 1 && selection.values.single().type == AssetType.REMOTE,
                        onShare = viewModel::share,
                        onDownload = viewModel::download,
                        onSendToFrame = viewModel::sendToFrame,
                        onAddToAlbum = { chooseAlbum = true },
                        onDelete = { confirmDelete = true },
                    )
                }
            },
            topBar = {
                TimelineAppBar(
                    scrollBehavior = scrollBehavior,
                    onSettingsClick = onSettingsClick,
                    onSearchClick = onSearchClick,
                    onAlbumsClick = onAlbumsClick,
                )
            },
        ) { innerPadding ->

            if (people.selectedId != null) {
                PersonPhotosContent(
                    people = people,
                    photos = personPhotos,
                    thumbnail = peopleViewModel::thumbnail,
                    modifier = Modifier.padding(innerPadding),
                    onSelect = peopleViewModel::select,
                    onEditMember = onEditMember,
                    onOpenFamily = onOpenFamily,
                    onRetry = { peopleViewModel.retry() },
                    onLoadMore = peopleViewModel::loadMore,
                    onAssetClick = { asset, index -> onPersonAssetClick(asset, index, peopleViewModel.source) },
                )
            } else {
                run {
                    val coroutineScope = rememberCoroutineScope()
                    TimeLineContent(
                        state = state,
                        modifier = Modifier.padding(
                            innerPadding,
                        ),
                        viewModel = viewModel,
                        listState = listState,
                        onImageClick = onImageClick,
                        account = viewModel.account,
                        onSetUpBackupClick = onSettingsClick,
                        syncLines = syncLines,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onExpandMenuRequest = {
                            if (scaffoldState.bottomSheetState.currentValue != SheetValue.Expanded) {
                                coroutineScope.launch {
                                    scaffoldState.bottomSheetState.expand()
                                }
                            }
                        },
                        onCollapseMenuRequest = {
                            coroutineScope.launch {
                                scaffoldState.bottomSheetState.hide()
                            }
                        },
                        header = header,
                    )
                }
            }
        }

        val months = state.layout?.months.orEmpty()
        if (months.isNotEmpty() && people.selectedId == null) {
            FastScrollComponent(
                months = months,
                listState = listState,
                firstRowIndex = if (header != null) 1 else 0,
                extraTop = if (header != null) CAROUSEL_HEIGHT else 0.dp,
            )
        }
    }
}

/** The family carousel above the timeline (T-052): the month pill starts below it. */
private val CAROUSEL_HEIGHT = 104.dp

/** Gap between tiles and rows, and the list's side margin. */
private val SPACING = 4.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun TimeLineContent(
    viewModel: TimelineGridModel,
    state: TimelineState,
    listState: LazyListState,
    onImageClick: (String, AssetType, Int) -> Unit,
    onExpandMenuRequest: () -> Unit,
    onCollapseMenuRequest: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    account: TimelineAccount? = null,
    onSetUpBackupClick: () -> Unit = {},
    syncLines: Int? = null,
    /** Scrolls with the timeline as its first item (the family carousel, T-052). */
    header: (@Composable () -> Unit)? = null,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.loadingState == LoadingState.REFRESHING,
        onRefresh = { viewModel.refresh() },
        modifier = modifier,
        state = pullToRefreshState,
        indicator = {
            Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = state.loadingState == LoadingState.REFRESHING,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                state = pullToRefreshState,
            )
        },
    ) {
        // The layout depends on the width: measure first, then lay out the whole timeline.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val viewport = with(density) {
                Viewport(width = (maxWidth - SPACING * 2).toPx(), height = maxHeight.toPx(), spacing = SPACING.toPx())
            }
            LaunchedEffect(viewport) { viewModel.setViewport(viewport) }
            val layout = state.layout
            when {
                layout == null -> TimelineLoadingComponent()

                layout.rows.isEmpty() && syncLines != null -> TimelineSyncProgress(syncLines)

                // first sync
                layout.rows.isEmpty() && account != null && state.loadingState == LoadingState.LOADED ->
                    TimelineEmptyComponent(account = account, onSetUpBackupClick = onSetUpBackupClick)

                else -> TimelineList(
                    layout = layout,
                    listState = listState,
                    viewModel = viewModel,
                    onImageClick = onImageClick,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onExpandMenuRequest = onExpandMenuRequest,
                    onCollapseMenuRequest = onCollapseMenuRequest,
                    header = header,
                )
            }
        }
    }
}

@Composable
fun IconTextButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 34.dp),
                onClick = onClick,
            )
            .padding(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = text)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text)
    }
}
