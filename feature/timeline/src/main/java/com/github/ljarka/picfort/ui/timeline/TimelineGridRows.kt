package com.github.ljarka.picfort.ui.timeline

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.ui.R
import com.github.ljarka.picfort.ui.timeline.layout.MonthHeaderRow
import com.github.ljarka.picfort.ui.timeline.layout.Tile
import com.github.ljarka.picfort.ui.timeline.layout.TilesRow
import com.github.ljarka.picfort.ui.timeline.layout.TimelineLayout

private val GRID_SPACING = 4.dp
private val PLACEHOLDER_COLOR = Color.LightGray.copy(alpha = 0.3f)

/**
 * Rows laid out ahead of time (T-016): every size is known before anything is drawn, so
 * rows never change shape while scrolling.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun TimelineList(
    layout: TimelineLayout,
    listState: LazyListState,
    viewModel: TimelineGridModel,
    onImageClick: (String, AssetType, Int) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onExpandMenuRequest: () -> Unit,
    onCollapseMenuRequest: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    // Collected once for the list, not per tile; tiles get plain booleans so they can skip.
    val selectedAssets by viewModel.selectedAssets.collectAsStateWithLifecycle()
    val isInEditMode = selectedAssets.isNotEmpty()
    val density = LocalDensity.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(GRID_SPACING),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
    ) {
        if (header != null) {
            item(key = "header", contentType = "header") { header() }
        }
        items(
            items = layout.rows,
            key = { it.key },
            contentType = { if (it is MonthHeaderRow) "month" else "row" },
        ) { row ->
            when (row) {
                is MonthHeaderRow -> ImagesBucketTitle(row.title)

                is TilesRow -> Row(horizontalArrangement = Arrangement.spacedBy(GRID_SPACING)) {
                    val height = with(density) { row.height.toDp() }
                    row.tiles.forEach { tile ->
                        val isSelected = tile.asset.id in selectedAssets
                        GalleryTile(
                            tile = tile,
                            width = with(density) { tile.width.toDp() },
                            height = height,
                            isSelected = isSelected,
                            isInEditMode = isInEditMode,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = {
                                when {
                                    !isInEditMode -> onImageClick(tile.asset.id, tile.asset.type, tile.index)

                                    isSelected -> {
                                        viewModel.deselectAsset(tile.asset.id)
                                        if (viewModel.selectedAssets.value.isEmpty()) onCollapseMenuRequest()
                                    }

                                    else -> {
                                        viewModel.selectAsset(tile.asset, row.month)
                                        onExpandMenuRequest()
                                    }
                                }
                            },
                            onLongClick = {
                                viewModel.selectAsset(tile.asset, row.month)
                                onExpandMenuRequest()
                            },
                        )
                    }
                }
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
            .padding(horizontal = 4.dp, vertical = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun GalleryTile(
    tile: Tile,
    width: Dp,
    height: Dp,
    isSelected: Boolean,
    isInEditMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val asset = tile.asset
    with(sharedTransitionScope) {
        val scale by animateFloatAsState(targetValue = if (isSelected) 0.9f else 1f, label = "scale")
        // scale() adds a graphics layer: only pay for it while selecting.
        Box(modifier = Modifier.size(width, height).then(if (isInEditMode) Modifier.scale(scale) else Modifier)) {
            // Decoded once per asset; ThumbHash previews are tiny (≤ 32 px). Dropped once the
            // thumbnail is in, so a loaded tile draws one image, not two.
            var loaded by remember(tile.url) { mutableStateOf(false) }
            val placeholder = remember(asset.thumbhash) {
                asset.thumbhash?.let(::thumbHashBitmap)?.let(::BitmapPainter)
            }.takeUnless { loaded }
            // AsyncImage, not SubcomposeAsyncImage: subcomposition is slow in lazy lists.
            AsyncImage(
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = asset.id),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    // Blurred ThumbHash (or grey) until the image arrives. Drawn behind the
                    // image rather than as a placeholder painter, which previews/screenshots
                    // would show instead of the image.
                    .background(PLACEHOLDER_COLOR)
                    .then(
                        if (placeholder != null) {
                            Modifier.paint(placeholder, contentScale = ContentScale.Crop)
                        } else {
                            Modifier
                        },
                    ),
                model = tile.url,
                onSuccess = { loaded = true },
                error = ColorPainter(MaterialTheme.colorScheme.errorContainer),
                contentDescription = null,
            )
            Icon(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(vertical = 8.dp, horizontal = 12.dp)
                    .alpha(0.5f),
                painter = painterResource(
                    if (asset.type == AssetType.LOCAL) R.drawable.ic_cloud_off else R.drawable.ic_cloud,
                ),
                tint = Color.White,
                contentDescription = null,
            )
            if (asset.isVideo) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    asset.durationMs?.let {
                        Text(formatDuration(it), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (isInEditMode) {
                CheckBox(isSelected)
            }
        }
    }
}

@Composable
private fun CheckBox(checked: Boolean) {
    Box(modifier = Modifier.padding(12.dp)) {
        if (checked) {
            Icon(
                modifier = Modifier
                    .border(width = 1.dp, Color.White, shape = CircleShape),
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(width = 1.dp, Color.White, shape = CircleShape),
            )
        }
    }
}
