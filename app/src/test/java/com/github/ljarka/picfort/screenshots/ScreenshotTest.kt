package com.github.ljarka.picfort.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.github.ljarka.picfort.backup.BackupQueueState
import com.github.ljarka.picfort.backup.BackupStatus
import com.github.ljarka.picfort.backup.QueuedMedia
import com.github.ljarka.picfort.core.model.EventsSegment
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.ShoppingItem
import com.github.ljarka.picfort.core.model.ShoppingList
import com.github.ljarka.picfort.core.ui.avatar.avatarColor
import com.github.ljarka.picfort.core.ui.theme.AccentTheme
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import com.github.ljarka.picfort.ui.events.EventsListContent
import com.github.ljarka.picfort.ui.events.EventsListUi
import com.github.ljarka.picfort.ui.init.SetupContent
import com.github.ljarka.picfort.ui.init.SetupStep
import com.github.ljarka.picfort.ui.init.SetupUi
import com.github.ljarka.picfort.ui.settings.BackupSettingsUi
import com.github.ljarka.picfort.ui.settings.SettingsContent
import com.github.ljarka.picfort.ui.shell.ComingSoonContent
import com.github.ljarka.picfort.ui.shell.Tab
import com.github.ljarka.picfort.ui.shell.TabBar
import com.github.ljarka.picfort.ui.shell.TabRoot
import com.github.ljarka.picfort.ui.shopping.ShoppingListsContent
import com.github.ljarka.picfort.ui.shopping.ShoppingListsUi
import com.github.ljarka.picfort.ui.wall.FamilyWallContent
import com.github.ljarka.picfort.ui.wall.FamilyWallUiState
import com.github.ljarka.picfort.ui.wall.WallPost
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.Rule
import org.junit.Test

/**
 * Screens rendered with sample data. Record after intended UI changes with
 * `./gradlew recordPaparazziDebug`; CI runs `verifyPaparazziDebug`.
 * The images are also used in the README.
 */
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private fun snap(dark: Boolean = false, content: @Composable () -> Unit) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        paparazzi.unsafeUpdateConfig(
            DeviceConfig.PIXEL_6.copy(
                nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT,
            ),
        )
        try {
            paparazzi.snapshot {
                PicfortTheme(darkTheme = dark, dynamicColor = false) {
                    WithSamplePhotos(content)
                }
            }
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    private val members = listOf(
        FamilyMember("mama", "Mama", avatarUrl = "avatar-mama", color = avatarColor("pink")),
        FamilyMember("tata", "Tata", avatarUrl = null, color = avatarColor("blue")),
        FamilyMember("ola", "Ola", avatarUrl = "avatar-ola", color = avatarColor("green")),
        FamilyMember("babcia", "Babcia Zosia", avatarUrl = null, color = avatarColor("amber")),
    )

    private fun wallPost(
        id: Int,
        author: String,
        minutesAgo: Long,
        text: String?,
        photo: Pair<Int, Int>? = null,
        pinned: Boolean = false,
    ) = WallPost(
        id = id,
        authorId = author,
        authorName = author,
        text = text,
        photoUrl = photo?.let { "photo-$id" },
        photoWidth = photo?.first,
        photoHeight = photo?.second,
        pinned = pinned,
        created = Instant.ofEpochMilli(NOW - minutesAgo * 60_000),
    )

    private val wallPosts = listOf(
        wallPost(1, "mama", 25, "Pierwsze pomidory z ogródka! 🍅"),
        wallPost(2, "tata", 90, "Kto odbiera Olę z treningu w czwartek?", pinned = true),
        wallPost(3, "babcia", 300, "Dziękuję za odwiedziny, było cudownie ❤️"),
        wallPost(4, "ola", 600, "Mam piątkę z matmy!!!"),
        wallPost(5, "mama", 40, "Wycieczka w Tatry", photo = 1920 to 1280),
        wallPost(6, "tata", 120, null, photo = 1080 to 1440),
        wallPost(7, "ola", 200, "Burek", photo = 1440 to 1440),
        wallPost(8, "babcia", 500, null, photo = 1440 to 1080),
        wallPost(9, "mama", 900, null, photo = 1080 to 1620),
    )

    private val groceries = ShoppingList(
        id = 1,
        title = "Biedronka",
        createdBy = "mama",
        items = listOf(
            ShoppingItem(1, "Mleko 2x", position = 1.0),
            ShoppingItem(2, "Chleb", position = 2.0),
            ShoppingItem(3, "Jajka", position = 3.0),
            ShoppingItem(4, "Pomidory", position = 4.0),
            ShoppingItem(5, "Masło", checked = true, checkedBy = "tata", checkedAt = "2026-11-10T12:05:00Z"),
            ShoppingItem(6, "Ser żółty", checked = true, checkedBy = "ola", checkedAt = "2026-11-10T12:04:00Z"),
            ShoppingItem(7, "Kawa", checked = true, checkedBy = "mama", checkedAt = "2026-11-10T12:01:00Z"),
        ),
    )

    private val shoppingLists = listOf(
        groceries,
        ShoppingList(
            2,
            "Weekend: grill",
            items = listOf(
                ShoppingItem(10, "Węgiel", position = 1.0),
                ShoppingItem(11, "Kiełbasa", position = 2.0),
                ShoppingItem(12, "Musztarda", position = 3.0),
            ),
        ),
        ShoppingList(
            3,
            "Apteka",
            items = listOf(
                ShoppingItem(20, "Witamina D", checked = true, checkedAt = "x"),
            ),
        ),
        ShoppingList(
            4,
            "Rossmann",
            items = (1..9).map {
                ShoppingItem(
                    30L + it,
                    listOf(
                        "Pasta do zębów",
                        "Szampon",
                        "Płyn do naczyń",
                        "Ręczniki papierowe",
                        "Mydło",
                        "Chusteczki",
                        "Krem",
                        "Gąbki",
                        "Worki",
                    )[it - 1],
                    position = it.toDouble(),
                )
            },
        ),
    )

    private val sampleToday = LocalDate.of(2026, 11, 11)

    private val sampleUpcomingEvents = listOf(
        FamilyEvent(
            id = 1,
            title = "Mazury trip",
            startDate = LocalDate.of(2026, 11, 10),
            endDate = LocalDate.of(2026, 11, 14),
            location = "Giżycko",
            note = "Cottage by the lake. Remember life jackets for the kids and raincoats.",
            createdBy = "mama",
        ),
        FamilyEvent(
            id = 2,
            title = "Ola's school concert",
            startDate = LocalDate.of(2026, 11, 12),
            startTime = LocalTime.of(17, 30),
            location = "SP nr 12, aula",
            createdBy = "mama",
        ),
        FamilyEvent(
            id = 3,
            title = "Christmas Eve",
            startDate = LocalDate.of(2026, 12, 24),
            startTime = LocalTime.of(18, 0),
            location = "Babcia Zosia's home",
            createdBy = "tata",
        ),
        FamilyEvent(
            id = 4,
            title = "Skiing in Białka",
            startDate = LocalDate.of(2027, 1, 3),
            endDate = LocalDate.of(2027, 1, 10),
            location = "Białka Tatrzańska",
            createdBy = "tata",
        ),
    )

    private val backup = BackupSettingsUi(
        enabled = true,
        wifiOnly = true,
        chargingOnly = false,
        backedUp = 2_418,
        onPhone = 2_546,
        lastResult = "Uploaded 37 · 4 were already in Immich",
        lastRunAt = NOW - 2 * 3_600_000,
    )

    private val setupFilled = SetupUi(
        immichAddress = "192.168.0.10:2283",
        email = "ola@example.com",
        password = "secret",
    )

    @Test
    fun setup() = snap { SetupContent(SetupUi()) }

    @Test
    fun setup_filled() = snap { SetupContent(setupFilled) }

    @Test
    fun setup_filled_dark() = snap(dark = true) {
        SetupContent(setupFilled.copy(picfortAddress = "picfort.example.com", showApiKey = true))
    }

    @Test
    fun setup_connecting() = snap {
        SetupContent(setupFilled.copy(picfortAddress = "192.168.0.10:5000", step = SetupStep.SigningIn))
    }

    @Test
    fun setup_picfort_failed() = snap {
        SetupContent(
            setupFilled.copy(
                immichAddress = "photos.example.com",
                password = "",
                signedIn = true,
                picfortAddress = "picfort.example.com",
                picfortError = "Can't reach https://picfort.example.com",
            ),
        )
    }

    @Test
    fun wall() = snap {
        WithTabBar(Tab.WALL) {
            FamilyWallContent(
                FamilyWallUiState(members = members, posts = wallPosts, myId = "mama", isLoading = false),
                now = NOW,
            )
        }
    }

    @Test
    fun wall_dark() = snap(dark = true) {
        WithTabBar(Tab.WALL) {
            FamilyWallContent(
                FamilyWallUiState(members = members, posts = wallPosts, myId = "mama", isLoading = false),
                now = NOW,
            )
        }
    }

    @Test
    fun shopping_lists() = snap {
        WithTabBar(Tab.SHOPPING) {
            ShoppingListsContent(ShoppingListsUi(lists = shoppingLists))
        }
    }

    @Test
    fun events_upcoming() = snap {
        WithTabBar(Tab.EVENTS) {
            EventsListContent(
                EventsListUi(
                    today = sampleToday,
                    segment = EventsSegment.UPCOMING,
                    upcomingEvents = sampleUpcomingEvents,
                ),
            )
        }
    }

    @Test
    fun events_upcoming_dark() = snap(dark = true) {
        WithTabBar(Tab.EVENTS) {
            EventsListContent(
                EventsListUi(
                    today = sampleToday,
                    segment = EventsSegment.UPCOMING,
                    upcomingEvents = sampleUpcomingEvents,
                ),
            )
        }
    }

    @Test
    fun events_no_server() = snap {
        WithTabBar(Tab.EVENTS) {
            EventsListContent(EventsListUi(configured = false, isLoading = false))
        }
    }

    @Test
    fun school_coming_soon_dark() = snap(dark = true) {
        WithTabBar(Tab.SCHOOL) {
            ComingSoonContent(
                "School",
                configured = true,
                message = "School timetables are coming soon.",
                serverMessage = "",
            )
        }
    }

    @Test
    fun settings() = snap {
        SettingsContent(
            backup = backup,
            status = BackupStatus.Running(done = 2_455, total = 2_546),
            queue = BackupQueueState(
                pending = (1L..8L).map { QueuedMedia(it, isVideo = it == 3L) },
                uploadingId = 2L,
                uploadProgress = 0.4f,
            ),
            serverUrl = "https://photos.example.com",
            picfortServer = "http://192.168.0.105:5000",
            wallpaperColors = false,
            now = NOW,
            relativeTimeSpan = { _, _ -> "2 hours ago" },
        )
    }

    @Test
    fun settings_dark() = snap(dark = true) {
        SettingsContent(
            backup = backup,
            status = BackupStatus.Idle,
            serverUrl = "https://photos.example.com",
            now = NOW,
        )
    }

    /** T-017: the five labels on one line each at 360 dp and font scale 1.3. */
    @Test
    fun tab_bar_narrow_large_font() = snap {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
            Box(Modifier.width(360.dp)) { TabBar(selected = Tab.SHOPPING, onSelect = {}) }
        }
    }

    /** A tab root with the bottom bar over it, as in the app. */
    @Composable
    private fun WithTabBar(tab: Tab, content: @Composable () -> Unit) {
        AccentTheme(tab.accent) {
            Box(Modifier.fillMaxSize()) {
                TabRoot(content)
                TabBar(selected = tab, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    private companion object {
        const val NOW = 1_752_500_000_000L
    }
}
