package com.github.ljarka.picfort.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.screenshots.WithSamplePhotos
import com.github.ljarka.picfort.ui.theme.PicfortTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test

/** Lives in the timeline package because TimeLineContent is internal. */
class TimelineScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private class FakeGrid(selected: Set<String> = emptySet()) : TimelineGridModel {
        override val selectedAssets: StateFlow<Set<String>> = MutableStateFlow(selected)
        override fun getAssetLoadingState(bucket: Long): StateFlow<AssetLoadingState> =
            MutableStateFlow(AssetLoadingState.LOADED)
        override fun fetchAssets(bucket: Long) = Unit
        override fun isFixedSpan(bucket: Long) = false
        override fun setFixedSpan(bucket: Long) = Unit
        override fun selectAsset(asset: AssetUi, bucket: Long) = Unit
        override fun deselectAsset(assetId: String) = Unit
        override fun refresh() = Unit
    }

    /** A month of sample assets with a realistic mix of shapes, videos and phone-only items. */
    private fun month(timeStamp: Long, title: String, index: Int, seed: Int, count: Int): TimeBucketUi {
        val ratios = listOf(1.33, 0.75, 0.75, 1.78, 1.0, 0.75, 1.5, 0.75, 1.33, 1.33, 0.56, 0.75, 1.78)
        val raw = (0 until count).map { i ->
            val n = seed + i
            AssetUi(
                url = "photo-$n",
                span = calculateSpan(ratios[n % ratios.size]),
                id = "asset-$n",
                type = if (n % 5 == 2) AssetType.LOCAL else AssetType.REMOTE,
                isVideo = n % 6 == 4,
                durationMs = if (n % 6 == 4) 8_000L + n * 3_000 else null,
            )
        }
        val items = raw.zip(adjustSpans(raw.map { it.span })) { a, span -> a.copy(span = span) }
        return TimeBucketUi(
            timeStamp = timeStamp, count = count, formattedDate = title, index = index,
            numberOfRows = rowCount(items.map { it.span }), items = items,
        )
    }

    @OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
    @Test
    fun timeline() {
        val buckets = listOf(
            month(3L, "July, 2025", 0, seed = 0, count = 11),
            month(2L, "June, 2025", 11, seed = 20, count = 9),
            month(1L, "May, 2025", 20, seed = 40, count = 8),
        )
        val state = BucketsState(loadingState = LoadingState.LOADED, items = buckets)
        paparazzi.snapshot {
            PicfortTheme(dynamicColor = false) {
                WithSamplePhotos {
                    SharedTransitionLayout {
                        AnimatedVisibility(visible = true) {
                            Box {
                                val gridState = rememberLazyGridState()
                                Scaffold(
                                    topBar = {
                                        TimelineAppBar(
                                            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
                                            onSettingsClick = {}, onSearchClick = {}, onAlbumsClick = {},
                                        )
                                    },
                                ) { padding ->
                                    TimeLineContent(
                                        viewModel = FakeGrid(),
                                        state = state,
                                        gridState = gridState,
                                        onImageClick = { _, _, _ -> },
                                        onExpandMenuRequest = {},
                                        onCollapseMenuRequest = {},
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                        modifier = Modifier.padding(padding),
                                    )
                                }
                                FastScrollComponent(buckets = buckets, gridState = gridState)
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
    @Test
    fun timeline_empty() {
        val state = BucketsState(loadingState = LoadingState.LOADED, items = emptyList())
        paparazzi.snapshot {
            PicfortTheme(dynamicColor = false) {
                SharedTransitionLayout {
                    AnimatedVisibility(visible = true) {
                        Scaffold(
                            topBar = {
                                TimelineAppBar(
                                    scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
                                    onSettingsClick = {}, onSearchClick = {}, onAlbumsClick = {},
                                )
                            },
                        ) { padding ->
                            TimeLineContent(
                                viewModel = FakeGrid(emptyList()),
                                state = state,
                                gridState = rememberLazyGridState(),
                                onImageClick = { _, _, _ -> },
                                onExpandMenuRequest = {},
                                onCollapseMenuRequest = {},
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedVisibility,
                                modifier = Modifier.padding(padding),
                                account = TimelineAccount("test@example.com", "https://photos.example.com"),
                            )
                        }
                    }
                }
            }
        }
    }
}
