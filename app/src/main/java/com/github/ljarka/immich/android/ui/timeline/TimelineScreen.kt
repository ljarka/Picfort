package com.github.ljarka.immich.android.ui.timeline

import android.content.res.Configuration.ORIENTATION_PORTRAIT
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.github.ljarka.immich.android.R
import com.github.ljarka.immich.android.db.AssetType
import com.github.ljarka.immich.android.ui.timeline.row.CalculatedRows
import com.github.ljarka.immich.android.ui.timeline.row.calculateRowsSizes
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onImageClick: (String, AssetType, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        val viewModel: TimelineViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val gridState = rememberLazyGridState()
        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.Hidden,
                skipHiddenState = false,
            )
        )

        LaunchedEffect(viewModel) {
            viewModel.messages.collect { scaffoldState.snackbarHostState.showSnackbar(it) }
        }

        MenuClosedObserver(sheetState = scaffoldState.bottomSheetState) {
            viewModel.clearSelection()
        }
        val scrollBehavior = enterAlwaysScrollBehavior()
        BottomSheetScaffold(
            sheetPeekHeight = 0.dp,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            scaffoldState = scaffoldState,
            sheetContent = { TimelineMenuContent() },
            topBar = { TimelineAppBar(scrollBehavior = scrollBehavior) }
        ) { innerPadding ->

            if (state.loadingState == LoadingState.LOADING) {
                TimelineLoadingComponent()
            } else {
                val coroutineScope = rememberCoroutineScope()
                TimeLineContent(
                    state = state,
                    modifier = Modifier.padding(
                        innerPadding
                    ),
                    viewModel = viewModel,
                    gridState = gridState,
                    onImageClick = onImageClick,
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
                    }
                )
            }
        }

        if (state.items.isNotEmpty()) {
            FastScrollComponent(
                buckets = state.items,
                gridState = gridState,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun TimeLineContent(
    viewModel: TimelineViewModel,
    state: BucketsState,
    gridState: LazyGridState,
    onImageClick: (String, AssetType, Int) -> Unit,
    onExpandMenuRequest: () -> Unit,
    onCollapseMenuRequest: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = state.loadingState == LoadingState.REFRESHING,
        onRefresh = {
            viewModel.refresh()
        },
        modifier = modifier,
        state = pullToRefreshState,
        indicator = {
            Indicator(
                modifier = Modifier.align(Alignment.TopCenter),
                isRefreshing = state.loadingState == LoadingState.REFRESHING,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                state = pullToRefreshState
            )
        },
    ) {
        ImagesBucketsGrid(
            gridState = gridState,
            state = state,
            viewModel = viewModel,
            onImageClick = onImageClick,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            onExpandMenuRequest = onExpandMenuRequest,
            onCollapseMenuRequest = onCollapseMenuRequest,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ImagesBucketsGrid(
    gridState: LazyGridState,
    state: BucketsState,
    viewModel: TimelineViewModel,
    onImageClick: (String, AssetType, Int) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onExpandMenuRequest: () -> Unit,
    onCollapseMenuRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val orientation = LocalConfiguration.current.orientation
    LazyVerticalGrid(
        columns = if (orientation == ORIENTATION_PORTRAIT) GridCells.Fixed(4) else GridCells.Fixed(
            8
        ),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(4.dp)
    ) {
        state.items.forEach { bucket ->
            item(span = { GridItemSpan(maxLineSpan) }, key = bucket.timeStamp) {
                ImagesBucketTitle(bucket.formattedDate)
            }

            val rows: CalculatedRows? =
                if (bucket.numberOfRows != null && bucket.numberOfRows != 0) calculateRowsSizes(
                    bucket.numberOfRows,
                    bucket.count
                ) else null

            items(
                count = bucket.count,
                span = { index ->
                    val defaultSpan = GridItemSpan(2)
                    if (viewModel.isFixedSpan(bucket.timeStamp)) {
                        defaultSpan
                    } else {
                        val item = viewModel.getAsset(bucket.timeStamp, index)
                        calculateSpan(index, item, rows) ?: defaultSpan.also {
                            viewModel.setFixedSpan(bucket.timeStamp)
                        }
                    }
                },
                key = { index -> "${bucket.timeStamp}_$index".hashCode() }) { index ->

                val itemLifecycleOwner = remember { ItemLifecycleOwner() }
                DisposableEffect(Unit) {
                    itemLifecycleOwner.start()
                    onDispose {
                        itemLifecycleOwner.stop()
                    }
                }

                val asset = viewModel.getAsset(bucket.timeStamp, index)
                val assetLoading by viewModel.getAssetLoadingState(bucket.timeStamp)
                    .collectAsStateWithLifecycle(lifecycleOwner = itemLifecycleOwner)
                val selectedAssets by
                viewModel.selectedAssets.collectAsStateWithLifecycle(
                    lifecycleOwner = itemLifecycleOwner
                )
                ImagesBucketItem(
                    asset = asset,
                    assetLoading = assetLoading,
                    selectedAssets = selectedAssets,
                    bucket = bucket,
                    onSelectAssetClick = {
                        viewModel.selectAsset(it)
                        onExpandMenuRequest()
                    },
                    onDeselectAssetClick = {
                        viewModel.deselectAsset(it)
                        if (viewModel.selectedAssets.value.isEmpty()) {
                            onCollapseMenuRequest()
                        }
                    },
                    onImageClick = onImageClick,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    index = index,
                    onFetchRequest = {
                        viewModel.fetchAssets(it)
                    }
                )
            }
        }
    }
}

@Composable
private fun ImagesBucketTitle(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(horizontal = 8.dp, vertical = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ImagesBucketItem(
    asset: AssetUi?,
    assetLoading: AssetLoadingState,
    selectedAssets: Set<String>,
    bucket: TimeBucketUi,
    onSelectAssetClick: (String) -> Unit,
    onDeselectAssetClick: (String) -> Unit,
    onImageClick: (String, AssetType, Int) -> Unit,
    onFetchRequest: (Long) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    index: Int,
) {

    if (asset == null) {
        if (assetLoading == AssetLoadingState.DEFAULT || assetLoading == AssetLoadingState.LOADED) {
            LaunchedEffect(bucket.timeStamp) {
                onFetchRequest(bucket.timeStamp)
            }
        }
        PlaceHolder(modifier = Modifier.padding(4.dp))
    } else {
        GalleryItem(
            asset = asset,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            isSelectedItem = selectedAssets.contains(asset.id),
            onClick = {
                if (selectedAssets.isEmpty()) {
                    onImageClick(
                        asset.id,
                        asset.type,
                        bucket.index + index
                    )
                } else {
                    if (selectedAssets.contains(it)) {
                        onDeselectAssetClick(asset.id)
                    } else {
                        onSelectAssetClick(asset.id)
                    }
                }
            },
            isInEditMode = selectedAssets.isNotEmpty(),
            onLongClick = {
                onSelectAssetClick(asset.id)
            }
        )
    }
}

private fun calculateSpan(
    index: Int,
    item: AssetUi?,
    rows: CalculatedRows?,
): GridItemSpan? {
    return if (item != null) {
        GridItemSpan(item.span)
    } else if (rows != null) {
        if (index + 1 < rows.singleItemRowCount) {
            GridItemSpan(4)
        } else if (index + 1 < rows.singleItemRowCount + rows.doubleItemRowCount) {
            GridItemSpan(2)
        } else if (index + 1 < rows.singleItemRowCount + rows.doubleItemRowCount + rows.quadrupleItemRowCount) {
            GridItemSpan(1)
        } else {
            GridItemSpan(2)
        }
    } else {
        null
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun GalleryItem(
    asset: AssetUi,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit,
    isInEditMode: Boolean,
    isSelectedItem: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    with(sharedTransitionScope) {
        val scale by animateFloatAsState(
            targetValue = if (isSelectedItem) 0.9f else 1f,
            label = "scale"
        )

        Box(
            modifier = Modifier.scale(scale),
        ) {
            SubcomposeAsyncImage(
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .sharedElement(
                        state = rememberSharedContentState(key = asset.id),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                    .combinedClickable(
                        onClick = { onClick(asset.id) },
                        onLongClick = {
                            onLongClick(asset.id)
                        },
                    )
                    .padding(4.dp)
                    .height(170.dp)
                    .clip(RoundedCornerShape(8.dp)),
                model = asset.url,
                loading = { PlaceHolder() },
                error = {
                    PlaceHolder(
                        modifier = Modifier.background(Color.Red)
                    )
                },
                contentDescription = null,
            )

            if (asset.type == AssetType.LOCAL) {
                Icon(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                        .alpha(0.5f),
                    painter = painterResource(R.drawable.ic_cloud_off),
                    tint = Color.White,
                    contentDescription = null,
                )
            } else {
                Icon(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(vertical = 8.dp, horizontal = 12.dp)
                        .alpha(0.5f),
                    painter = painterResource(R.drawable.ic_cloud),
                    tint = Color.White,
                    contentDescription = null
                )
            }
            if (isInEditMode) {
                CheckBox(isSelectedItem)
            }
        }
    }
}

@Composable
private fun PlaceHolder(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .height(170.dp)
            .fillMaxWidth()
            .background(
                color = Color.LightGray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            )
    )
}

@Composable
fun IconTextButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 34.dp),
                onClick = onClick
            )
            .padding(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = text)
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text)
    }
}

@Composable
fun CheckBox(checked: Boolean) {
    Box(modifier = Modifier.padding(12.dp)) {
        if (checked) {
            Icon(
                modifier = Modifier
                    .border(width = 1.dp, Color.White, shape = CircleShape),
                imageVector = Icons.Default.CheckCircle, contentDescription = null,
                tint = Color.White,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(width = 1.dp, Color.White, shape = CircleShape)
            )
        }
    }
}
