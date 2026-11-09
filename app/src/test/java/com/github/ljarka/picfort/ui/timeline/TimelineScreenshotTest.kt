package com.github.ljarka.picfort.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.github.ljarka.picfort.db.AssetType
import com.github.ljarka.picfort.screenshots.WithSamplePhotos
import com.github.ljarka.picfort.ui.theme.PicfortTheme
import com.github.ljarka.picfort.ui.timeline.layout.TimelineLayout
import com.github.ljarka.picfort.ui.timeline.layout.Viewport
import com.github.ljarka.picfort.ui.timeline.layout.layoutTimeline
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
        override fun selectAsset(asset: TimelineAsset, month: Long) = Unit
        override fun deselectAsset(assetId: String) = Unit
        override fun refresh() = Unit
        override fun setViewport(viewport: Viewport) = Unit
    }

    /** A month with a realistic mix of shapes: portrait, landscape, square, a panorama, videos, phone-only. */
    private fun month(key: Long, start: Int, seed: Int, count: Int): TimelineMonth {
        val ratios = listOf(4f / 3, 3f / 4, 3f / 4, 16f / 9, 1f, 3f / 4, 3f, 3f / 4, 4f / 3, 4f / 3, 9f / 16, 3f / 4)
        return TimelineMonth(key, start, List(count) { i ->
            val n = seed + i
            TimelineAsset(
                id = "photo-$n",
                type = if (n % 5 == 2) AssetType.LOCAL else AssetType.REMOTE,
                dateTaken = key,
                ratio = ratios[n % ratios.size],
                isVideo = n % 6 == 4,
                durationMs = if (n % 6 == 4) 8_000L + n * 3_000 else null,
            )
        })
    }

    /** Pixel 6: 1080 px wide at 2.625 px/dp, with the list's 4 dp margins and gaps. */
    private fun layoutOf(vararg months: TimelineMonth): TimelineLayout {
        val spacing = 4 * 2.625f
        return layoutTimeline(
            TimelineIndex(months.toList()),
            Viewport(width = 1080f - 2 * spacing, height = 2400f, spacing = spacing),
            titleOf = { mapOf(3L to "July, 2025", 2L to "June, 2025", 1L to "May, 2025")[it].orEmpty() },
            urlOf = { it.id },
        )
    }

    @OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
    private fun snapshot(state: TimelineState, syncLines: Int? = null, account: TimelineAccount? = null) {
        paparazzi.snapshot {
            PicfortTheme(dynamicColor = false) {
                WithSamplePhotos {
                    SharedTransitionLayout {
                        AnimatedVisibility(visible = true) {
                            Box {
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
                                        listState = rememberLazyListState(),
                                        onImageClick = { _, _, _ -> },
                                        onExpandMenuRequest = {},
                                        onCollapseMenuRequest = {},
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = this@AnimatedVisibility,
                                        modifier = Modifier.padding(padding),
                                        account = account,
                                        syncLines = syncLines,
                                    )
                                }
                                FastScroll(state)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FastScroll(state: TimelineState) {
        val months = state.layout?.months.orEmpty()
        if (months.isNotEmpty()) FastScrollComponent(months = months, listState = rememberLazyListState())
    }

    @Test
    fun timeline() {
        val layout = layoutOf(
            month(3L, start = 0, seed = 0, count = 11),
            month(2L, start = 11, seed = 20, count = 9),
            month(1L, start = 20, seed = 40, count = 8),
        )
        snapshot(TimelineState(LoadingState.LOADED, layout))
    }

    @Test
    fun timeline_empty() {
        snapshot(
            TimelineState(LoadingState.LOADED, TimelineLayout.EMPTY),
            account = TimelineAccount("test@example.com", "https://photos.example.com"),
        )
    }

    @Test
    fun timeline_syncing() {
        snapshot(TimelineState(LoadingState.REFRESHING, TimelineLayout.EMPTY), syncLines = 1500)
    }
}
