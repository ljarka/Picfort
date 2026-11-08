package com.github.ljarka.picfort.ui.image

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.ui.frame.FrameSettingsDialog
import com.github.ljarka.picfort.ui.timeline.AssetIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ImageDetailsScreen(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDismissRequest: () -> Unit,
    assetId: String,
    assetType: AssetType,
    imageIndex: Int,
) {
    var coroutineScope = rememberCoroutineScope()
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    fun dismiss() = coroutineScope.launch {
        if (!sharedTransitionScope.isTransitionActive) {
            if (scale > 1f) {
                scale = 1f
                offset = Offset.Zero
                delay(200)
            }
            onDismissRequest()
        }
    }
    BackHandler(enabled = true) { dismiss() }
    val viewModel = hiltViewModel<ImageDetailsViewModel>()
    val pagerState = rememberPagerState(
        pageCount = { viewModel.getNumberOfPages() },
        initialPage = imageIndex,
    )
    // The asset on screen now, not the one the viewer was opened with.
    val currentPage = pagerState.currentPage
    val currentAsset by remember(currentPage) { viewModel.getAssetIndex(currentPage) }
        .collectAsState(
            initial = AssetIndex(imageIndex, assetId, assetType).takeIf { currentPage == imageIndex }
        )
    val isSending by viewModel.isSendingToFrame.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFrameSettings by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.frameEvents.collect { event ->
            when (event) {
                FrameEvent.OpenSettings -> showFrameSettings = true
                is FrameEvent.Message -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(event.text)
                }
            }
        }
    }

    if (showFrameSettings) {
        FrameSettingsDialog(onDismiss = { showFrameSettings = false })
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = {},
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(32.dp)
                            .clickable { dismiss() },
                    )
                },
                actions = {
                    IconButton(onClick = { showFrameSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Frame settings")
                    }
                    val asset = currentAsset?.takeIf { it.index == currentPage && it.assetId.isNotEmpty() }
                    IconButton(
                        enabled = asset != null && !isSending,
                        onClick = { asset?.let { viewModel.sendToImageFrame(it.assetId, it.assetType) } },
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Show on frame",
                            )
                        }
                    }
                }
            )
        },
    ) { innerPadding ->
        with(sharedTransitionScope) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 3,
                userScrollEnabled = scale == 1f && !sharedTransitionScope.isTransitionActive,
                pageSpacing = 16.dp,
                modifier = Modifier
                    .pointerInput(imageSize) {
                        detectTransformGestures { centroid: Offset, pan: Offset, zoom: Float, rotation: Float ->
                            val offsetX =
                                calculateOffset(offset.x, pan.x, imageSize.width / 2f, scale)
                            val offsetY =
                                calculateOffset(offset.y, pan.y, imageSize.height / 2f, scale)
                            offset = Offset(
                                x = offsetX,
                                y = offsetY,
                            )
                            scale = max(
                                ZoomStatus.Zoom1().scale,
                                minOf(scale * zoom, ZoomStatus.Zoom4().scale)
                            )
                        }
                    }
            ) { page ->

                val isCurrentPage = page == pagerState.currentPage

                if (!(scale > 1f && !isCurrentPage)) {
                    Page(
                        modifier = Modifier.padding(innerPadding),
                        isCurrentPage = isCurrentPage,
                        initialAssetId = assetId,
                        initialAssetType = assetType,
                        scale = scale,
                        offset = offset,
                        page = page,
                        dismissRequest = { dismiss() },
                        onScaleChanged = { scale = it },
                        onOffsetChanged = { offset = it },
                        onImageSizeChanged = { imageSize = it },
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.Page(
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean,
    initialAssetId: String,
    initialAssetType: AssetType,
    scale: Float,
    offset: Offset,
    page: Int,
    onScaleChanged: (scale: Float) -> Unit,
    onOffsetChanged: (offset: Offset) -> Unit,
    onImageSizeChanged: (size: IntSize) -> Unit,
    dismissRequest: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val viewModel = hiltViewModel<ImageDetailsViewModel>()
    PinchToZoom(
        modifier = modifier,
        onScaleChanged = onScaleChanged,
        onOffsetChanged = onOffsetChanged,
        scale = if (isCurrentPage) scale else 1f,
        offset = if (isCurrentPage) offset else Offset.Zero,
        onActionUp = { scale, offset ->
            if ((abs(offset.x) > 0 || abs(offset.y) > 0) && scale == ZoomStatus.Zoom1().scale && isCurrentPage) {
                dismissRequest()
            }
        }
    ) {
        val assetIndex = viewModel.getAssetIndex(page)
            .collectAsState(AssetIndex(page, initialAssetId, initialAssetType))
        SubcomposeAsyncImage(
            modifier = Modifier
                .fillMaxWidth()
                .onPlaced { onImageSizeChanged(it.size) }
                .then(
                    if (isCurrentPage) Modifier.sharedElement(
                        state = rememberSharedContentState(key = assetIndex.value.assetId),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ) else Modifier
                )
                .clip(RoundedCornerShape(8.dp)),
            model = viewModel.getPreview(
                assetId = assetIndex.value.assetId,
                assetType = assetIndex.value.assetType
            ),
            contentDescription = null,
            loading = {
                AsyncImage(
                    modifier = Modifier
                        .fillMaxWidth(),
                    model = viewModel.getThumbnail(
                        assetId = assetIndex.value.assetId,
                        assetType = assetIndex.value.assetType,
                    ),
                    contentDescription = null,
                )
            }
        )
    }
}

private fun calculateOffset(value: Float, change: Float, center: Float, scale: Float): Float {
    val newValue = value + change
    return if (value < 0) {
        maxOf(newValue, -center * scale)
    } else {
        minOf(newValue, center * scale)
    }
}
