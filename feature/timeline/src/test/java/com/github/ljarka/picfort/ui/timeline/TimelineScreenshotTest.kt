package com.github.ljarka.picfort.ui.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.github.ljarka.picfort.assets.AssetInfo
import com.github.ljarka.picfort.assets.ExifInfo
import com.github.ljarka.picfort.assets.PersonInfo
import com.github.ljarka.picfort.assets.toDetails
import com.github.ljarka.picfort.collections.AlbumContent
import com.github.ljarka.picfort.collections.AlbumDeleteUi
import com.github.ljarka.picfort.collections.AlbumDto
import com.github.ljarka.picfort.collections.AlbumsContent
import com.github.ljarka.picfort.collections.GridAsset
import com.github.ljarka.picfort.collections.LoadState
import com.github.ljarka.picfort.collections.MediaFilter
import com.github.ljarka.picfort.collections.PeoplePickerContent
import com.github.ljarka.picfort.collections.SearchContent
import com.github.ljarka.picfort.collections.SearchPerson
import com.github.ljarka.picfort.collections.SearchUi
import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.avatar.avatarColor
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import com.github.ljarka.picfort.ui.image.AssetDetailsList
import com.github.ljarka.picfort.ui.image.DetailsState
import com.github.ljarka.picfort.ui.people.PeopleFilterRow
import com.github.ljarka.picfort.ui.people.PeopleFilterUi
import com.github.ljarka.picfort.ui.people.PersonPhotosContent
import com.github.ljarka.picfort.ui.timeline.layout.TimelineLayout
import com.github.ljarka.picfort.ui.timeline.layout.Viewport
import com.github.ljarka.picfort.ui.timeline.layout.layoutTimeline
import java.time.ZoneId
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
        return TimelineMonth(
            key,
            start,
            List(count) { i ->
                val n = seed + i
                TimelineAsset(
                    id = "photo-$n",
                    type = if (n % 5 == 2) AssetType.LOCAL else AssetType.REMOTE,
                    dateTaken = key,
                    ratio = ratios[n % ratios.size],
                    isVideo = n % 6 == 4,
                    durationMs = if (n % 6 == 4) 8_000L + n * 3_000 else null,
                )
            },
        )
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
    private fun snapshot(
        state: TimelineState,
        syncLines: Int? = null,
        account: TimelineAccount? = null,
        header: (@Composable () -> Unit)? = null,
        overlay: @Composable BoxScope.() -> Unit = {},
    ) {
        paparazzi.snapshot {
            PicfortTheme(dynamicColor = false) {
                WithSamplePhotos {
                    SharedTransitionLayout {
                        AnimatedVisibility(visible = true) {
                            Box(Modifier.fillMaxSize()) {
                                Scaffold(
                                    topBar = {
                                        TimelineAppBar(
                                            scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
                                            onSettingsClick = {},
                                            onSearchClick = {},
                                            onAlbumsClick = {},
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
                                        header = header,
                                    )
                                }
                                FastScroll(state, withHeader = header != null)
                                overlay()
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FastScroll(state: TimelineState, withHeader: Boolean = false) {
        val months = state.layout?.months.orEmpty()
        if (months.isNotEmpty()) {
            FastScrollComponent(
                months = months,
                listState = rememberLazyListState(),
                firstRowIndex = if (withHeader) 1 else 0,
                extraTop = if (withHeader) 104.dp else 0.dp,
            )
        }
    }

    private fun snap(content: @Composable () -> Unit) {
        paparazzi.snapshot {
            PicfortTheme(dynamicColor = false) {
                WithSamplePhotos(content)
            }
        }
    }

    private val photos = (1..30).map { i ->
        GridAsset(id = "asset-$i", isVideo = i % 7 == 3, durationMs = if (i % 7 == 3) 15_000L * i else null)
    }

    @Test
    fun album() = snap {
        AlbumContent(name = "Summer in Kraków", assets = LoadState.Loaded(photos), thumbnail = { it.id })
    }

    @Test
    fun album_delete() = snap {
        AlbumContent(
            name = "Summer in Kraków",
            assets = LoadState.Loaded(photos),
            thumbnail = { it.id },
            canDelete = true,
            confirmingDelete = true,
            delete = AlbumDeleteUi(error = "Only the album's owner can delete it"),
        )
    }

    @Test
    fun albums() = snap {
        val albums = listOf(
            "Summer in Kraków" to 214,
            "Family" to 1_032,
            "Hiking" to 87,
            "Frame favourites" to 46,
            "Winter 2024" to 158,
            "Garden" to 23,
        ).mapIndexed { i, (name, count) -> AlbumDto("album-$i", name, "cover-$i", count) }
        AlbumsContent(albums = LoadState.Loaded(albums), thumbnail = { it.albumThumbnailAssetId })
    }

    @Test
    fun search() = snap {
        SearchContent(
            state = SearchUi(query = "sunset over the hills", results = photos.drop(4), searched = true),
            thumbnail = { it.id },
        )
    }

    @Test
    fun search_videos() = snap {
        SearchContent(
            state = SearchUi(results = photos.filter { it.isVideo }, searched = true, media = MediaFilter.Videos),
            thumbnail = { it.id },
        )
    }

    private val searchPeople = listOf(
        SearchPerson("p1", "Ola"),
        SearchPerson("p2", "Kuba"),
        SearchPerson("p3", "Monika"),
        SearchPerson("p4", "Łukasz"),
        SearchPerson("p5", "Babcia Ela"),
        SearchPerson("p6", "Dziadek"),
    ) + listOf(
        "Ania", "Bartek", "Celina", "Darek", "Ewa Nowak", "Franek", "Gosia", "Henryk", "Iza", "Janek",
        "Kasia z przedszkola", "Lena", "Marek", "Natalia", "Oskar", "Paweł", "Róża", "Staś", "Tomek", "Wiktoria",
    ).mapIndexed { i, name -> SearchPerson("q$i", name) }

    @Test
    fun search_all_people() = snap {
        androidx.compose.material3.Surface(
            color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            PeoplePickerContent(
                people = searchPeople,
                selected = setOf("p1", "p5"),
                personThumbnail = {
                    null
                },
                onToggle = {},
                onDone = {},
            )
        }
    }

    @Test
    fun search_people() = snap {
        SearchContent(state = SearchUi(people = searchPeople), thumbnail = { it.id })
    }

    @Test
    fun search_people_selected() = snap {
        SearchContent(
            state = SearchUi(
                results = photos,
                searched = true,
                people = searchPeople,
                selectedPeople = setOf("p1", "p2"),
            ),
            thumbnail = { it.id },
        )
    }

    @Test
    fun search_people_with_text() = snap {
        SearchContent(
            state = SearchUi(
                query = "beach",
                results = photos.drop(6),
                searched = true,
                people = searchPeople,
                selectedPeople = setOf("p1"),
            ),
            thumbnail = { it.id },
        )
    }

    @Test
    fun search_people_nothing() = snap {
        SearchContent(
            state = SearchUi(searched = true, people = searchPeople, selectedPeople = setOf("p1", "p5")),
            thumbnail = { it.id },
        )
    }

    @Test
    fun details() = snap {
        val details = AssetInfo(
            id = "x",
            originalFileName = "IMG_2041.HEIC",
            exifInfo = ExifInfo(
                make = "Apple", model = "iPhone 15 Pro", lensModel = "Main Camera — 24 mm",
                fNumber = 1.8, exposureTime = "1/250", focalLength = 6.86, iso = 64.0,
                exifImageWidth = 4032.0, exifImageHeight = 3024.0, fileSizeInByte = 2_870_000,
                dateTimeOriginal = "2025-07-14T18:42:00.000Z", timeZone = "Europe/Warsaw",
                city = "Kraków", state = "Lesser Poland", country = "Poland",
                latitude = 50.0614, longitude = 19.9366,
            ),
            people = listOf(PersonInfo("Ala"), PersonInfo("Tomek")),
        ).toDetails(ZoneId.of("Europe/Warsaw"))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(top = 24.dp)) { AssetDetailsList(DetailsState.Loaded(details)) }
        }
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

    // --- T-052: family carousel on Photos -------------------------------------------------

    private val family = listOf(
        FamilyMember("mama", "Mama", null, avatarColor("pink")),
        FamilyMember("tata", "Tata", null, avatarColor("blue")),
        FamilyMember("ola", "Ola", null, avatarColor("green")),
        FamilyMember("stas", "Staś", null, avatarColor("orange"), isImmichUser = false),
        FamilyMember("kuba", "Kuba", null, avatarColor("purple"), isImmichUser = false),
    )
    private val linked = mapOf("mama" to "p1", "tata" to "p2", "ola" to "p3", "stas" to "p4")

    private fun timelineLayout() = layoutOf(
        month(3L, start = 0, seed = 0, count = 11),
        month(2L, start = 11, seed = 20, count = 9),
        month(1L, start = 20, seed = 40, count = 8),
    )

    @Test
    fun timeline_people() {
        snapshot(
            TimelineState(LoadingState.LOADED, timelineLayout()),
            header = { PeopleFilterRow(PeopleFilterUi(family, linked), onSelect = {}, onOpenFamily = {}) },
        )
    }

    @Test
    fun timeline_people_not_linked() {
        snapshot(
            TimelineState(LoadingState.LOADED, timelineLayout()),
            header = { PeopleFilterRow(PeopleFilterUi(family, linked), onSelect = {}, onOpenFamily = {}) },
            overlay = {
                Snackbar(
                    action = {
                        TextButton(onClick = {}) { Text("Link", color = MaterialTheme.colorScheme.inversePrimary) }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                ) { Text("Kuba isn't linked to your Immich people") }
            },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun person_photos() = snap {
        Scaffold(
            topBar = {
                TimelineAppBar(
                    scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(),
                    onSettingsClick = {},
                    onSearchClick = {},
                    onAlbumsClick = {},
                )
            },
        ) { padding ->
            PersonPhotosContent(
                people = PeopleFilterUi(family, linked, selectedId = "ola"),
                photos = LoadState.Loaded(photos.drop(3)),
                thumbnail = { it.id },
                modifier = Modifier.padding(padding),
            )
        }
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
