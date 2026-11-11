package com.github.ljarka.picfort.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.ljarka.picfort.ui.shell.ComingSoonContent
import com.github.ljarka.picfort.ui.shopping.ShoppingItem
import com.github.ljarka.picfort.ui.shopping.ShoppingList
import com.github.ljarka.picfort.ui.shopping.ShoppingListContent
import com.github.ljarka.picfort.ui.shopping.ShoppingListUi
import com.github.ljarka.picfort.ui.shopping.ShoppingListsContent
import com.github.ljarka.picfort.ui.shopping.ShoppingListsUi
import com.github.ljarka.picfort.ui.shell.Tab
import com.github.ljarka.picfort.ui.shell.TabBar
import com.github.ljarka.picfort.ui.shell.TabRoot
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.github.ljarka.picfort.assets.AssetInfo
import com.github.ljarka.picfort.assets.ExifInfo
import com.github.ljarka.picfort.assets.PersonInfo
import com.github.ljarka.picfort.assets.toDetails
import com.github.ljarka.picfort.backup.BackupStatus
import com.github.ljarka.picfort.collections.AlbumContent
import com.github.ljarka.picfort.collections.AlbumDto
import com.github.ljarka.picfort.collections.AlbumsContent
import com.github.ljarka.picfort.collections.GridAsset
import com.github.ljarka.picfort.collections.LoadState
import com.github.ljarka.picfort.collections.SearchContent
import com.github.ljarka.picfort.collections.SearchUi
import com.github.ljarka.picfort.ui.frame.ConnectionTest
import com.github.ljarka.picfort.ui.frame.FrameAlbum
import com.github.ljarka.picfort.ui.frame.PicfortServerForm
import com.github.ljarka.picfort.ui.frame.WRONG_API_KEY
import com.github.ljarka.picfort.ui.frame.PicfortServerSetupContent
import com.github.ljarka.picfort.ui.frame.wallTestResult
import com.github.ljarka.picfort.ui.wall.FamilyMember
import com.github.ljarka.picfort.ui.wall.FamilyWallContent
import com.github.ljarka.picfort.ui.wall.FamilyWallUiState
import com.github.ljarka.picfort.network.PicfortServerException
import com.github.ljarka.picfort.ui.wall.WallPost
import com.github.ljarka.picfort.ui.wall.avatarColor
import java.time.Instant
import com.github.ljarka.picfort.ui.frame.FrameContent
import com.github.ljarka.picfort.ui.frame.FrameUi
import com.github.ljarka.picfort.ui.frame.SlideshowState
import com.github.ljarka.picfort.ui.image.AssetDetailsList
import com.github.ljarka.picfort.ui.image.DetailsState
import com.github.ljarka.picfort.ui.family.FamilyMemberEditUi
import com.github.ljarka.picfort.ui.family.FamilyMemberSheetContent
import com.github.ljarka.picfort.ui.family.FamilySettingsContent
import com.github.ljarka.picfort.ui.family.FamilySettingsUi
import com.github.ljarka.picfort.ui.family.FamilyUserUi
import com.github.ljarka.picfort.ui.family.ImmichPersonItem
import com.github.ljarka.picfort.ui.family.PeoplePickerSheetContent
import com.github.ljarka.picfort.ui.family.PeoplePickerUi
import com.github.ljarka.picfort.ui.settings.BackupSettingsUi
import com.github.ljarka.picfort.ui.settings.SettingsContent
import com.github.ljarka.picfort.ui.theme.PicfortTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/**
 * Screens rendered with sample data. Record after intended UI changes with
 * `./gradlew recordPaparazziDebug`; CI runs `verifyPaparazziDebug`.
 * The images are also used in the README.
 */
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private fun snap(dark: Boolean = false, content: @Composable () -> Unit) {
        if (dark) paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
        paparazzi.snapshot {
            PicfortTheme(darkTheme = dark, dynamicColor = false) {
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
    fun albums() = snap {
        val albums = listOf(
            "Summer in Kraków" to 214, "Family" to 1_032, "Hiking" to 87,
            "Frame favourites" to 46, "Winter 2024" to 158, "Garden" to 23,
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

    private val backup = BackupSettingsUi(
        enabled = true, wifiOnly = true, chargingOnly = false, backedUp = 2_418, onPhone = 2_546,
        lastResult = "Uploaded 37 · 4 were already in Immich", lastRunAt = NOW - 2 * 3_600_000,
    )

    @Test
    fun settings() = snap {
        SettingsContent(
            backup = backup, status = BackupStatus.Running(done = 2_455, total = 2_546),
            serverUrl = "https://photos.example.com", picfortServer = "http://192.168.0.105:5000", now = NOW,
        )
    }

    @Test
    fun settings_dark() = snap(dark = true) {
        SettingsContent(backup = backup, status = BackupStatus.Idle, serverUrl = "https://photos.example.com", now = NOW)
    }

    @Test
    fun frame() = snap {
        FrameContent(
            FrameUi(
                address = "http://192.168.0.105:5000",
                loading = false,
                image = samplePhotoImage(seed = 2, width = 450, height = 600),
                slideshow = SlideshowState(
                    mode = "album", albumId = "a1", intervalMinutes = 360,
                    quietStart = 22, quietEnd = 7, nextChangeAt = null,
                ),
                albums = listOf(FrameAlbum("a1", "Frame favourites"), FrameAlbum("a2", "Family")),
            )
        )
    }

    @Test
    fun frame_not_configured() = snap {
        FrameContent(FrameUi(configured = false, loading = false))
    }

    private val members = listOf(
        FamilyMember("mama", "Mama", avatarUrl = "avatar-mama", color = avatarColor("pink")),
        FamilyMember("tata", "Tata", avatarUrl = null, color = avatarColor("blue")),
        FamilyMember("ola", "Ola", avatarUrl = "avatar-ola", color = avatarColor("green")),
        FamilyMember("babcia", "Babcia Zosia", avatarUrl = null, color = avatarColor("amber")),
    )

    private fun wallPost(id: Int, author: String, minutesAgo: Long, text: String?, photo: Pair<Int, Int>? = null,
                         pinned: Boolean = false) = WallPost(
        id = id, authorId = author, authorName = author, text = text,
        photoUrl = photo?.let { "photo-$id" }, photoWidth = photo?.first, photoHeight = photo?.second,
        pinned = pinned, created = Instant.ofEpochMilli(NOW - minutesAgo * 60_000),
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

    @Test
    fun wall() = snap {
        WithTabBar(Tab.WALL) { FamilyWallContent(
            FamilyWallUiState(members = members, posts = wallPosts, myId = "mama", isLoading = false), now = NOW,
        ) }
    }

    @Test
    fun wall_dark() = snap(dark = true) {
        WithTabBar(Tab.WALL) { FamilyWallContent(
            FamilyWallUiState(members = members, posts = wallPosts, myId = "mama", isLoading = false), now = NOW,
        ) }
    }

    @Test
    fun wall_filtered() = snap {
        FamilyWallContent(
            FamilyWallUiState(
                members = members, posts = wallPosts, myId = "mama", isLoading = false, selectedMemberId = "tata",
            ),
            now = NOW,
        )
    }

    @Test
    fun wall_empty() = snap {
        FamilyWallContent(FamilyWallUiState(members = members, myId = "mama", isLoading = false), now = NOW)
    }

    @Test
    fun wall_no_server() = snap {
        WithTabBar(Tab.WALL) { FamilyWallContent(FamilyWallUiState(configured = false, isLoading = false), now = NOW) }
    }

    @Test
    fun shopping_lists_archived() = snap {
        ShoppingListsContent(ShoppingListsUi(lists = shoppingLists.take(2).map { it.copy(archived = true) }), archived = true)
    }

    @Test
    fun events_no_server() = snap {
        WithTabBar(Tab.EVENTS) {
            ComingSoonContent("Events", configured = false, message = "",
                serverMessage = "Connect your Picfort server to use events.")
        }
    }

    @Test
    fun school_coming_soon_dark() = snap(dark = true) {
        WithTabBar(Tab.SCHOOL) {
            ComingSoonContent("School", configured = true, message = "School timetables are coming soon.",
                serverMessage = "")
        }
    }

    /** T-017: the five labels on one line each at 360 dp and font scale 1.3. */
    @Test
    fun tab_bar_narrow_large_font() = snap {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.3f)) {
            Box(Modifier.width(360.dp)) { TabBar(selected = Tab.SHOPPING, onSelect = {}) }
        }
    }

    private val groceries = ShoppingList(
        id = 1, title = "Biedronka", createdBy = "mama",
        items = listOf(
            ShoppingItem(1, "Mleko 2x", position = 1.0), ShoppingItem(2, "Chleb", position = 2.0),
            ShoppingItem(3, "Jajka", position = 3.0), ShoppingItem(4, "Pomidory", position = 4.0),
            ShoppingItem(5, "Masło", checked = true, checkedBy = "tata", checkedAt = "2026-11-10T12:05:00Z"),
            ShoppingItem(6, "Ser żółty", checked = true, checkedBy = "ola", checkedAt = "2026-11-10T12:04:00Z"),
            ShoppingItem(7, "Kawa", checked = true, checkedBy = "mama", checkedAt = "2026-11-10T12:01:00Z"),
        ),
    )
    private val shoppingLists = listOf(
        groceries,
        ShoppingList(2, "Weekend: grill", items = listOf(ShoppingItem(10, "Węgiel", position = 1.0),
            ShoppingItem(11, "Kiełbasa", position = 2.0), ShoppingItem(12, "Musztarda", position = 3.0))),
        ShoppingList(3, "Apteka", items = listOf(ShoppingItem(20, "Witamina D", checked = true, checkedAt = "x"))),
        ShoppingList(4, "Rossmann", items = (1..9).map { ShoppingItem(30L + it, listOf("Pasta do zębów", "Szampon",
            "Płyn do naczyń", "Ręczniki papierowe", "Mydło", "Chusteczki", "Krem", "Gąbki", "Worki")[it - 1], position = it.toDouble()) }),
    )

    @Test
    fun shopping_lists() = snap { WithTabBar(Tab.SHOPPING) { ShoppingListsContent(ShoppingListsUi(lists = shoppingLists)) } }

    @Test
    fun shopping_lists_empty() = snap { WithTabBar(Tab.SHOPPING) { ShoppingListsContent(ShoppingListsUi()) } }

    @Test
    fun shopping_lists_offline_dark() = snap(dark = true) {
        WithTabBar(Tab.SHOPPING) { ShoppingListsContent(ShoppingListsUi(lists = shoppingLists, offlineSince = "14:32")) }
    }

    @Test
    fun shopping_list() = snap {
        ShoppingListContent(ShoppingListUi(list = groceries, members = members, myId = "mama"))
    }

    @Test
    fun shopping_list_checked_open() = snap {
        ShoppingListContent(ShoppingListUi(list = groceries, members = members, myId = "mama"), checkedOpen = true)
    }

    @Test
    fun shopping_list_offline() = snap {
        ShoppingListContent(ShoppingListUi(list = groceries, members = members, myId = "mama", offlineSince = "14:32"),
            checkedOpen = true)
    }

    /** A tab root with the bottom bar over it, as in the app. */
    @Composable
    private fun WithTabBar(tab: Tab, content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            TabRoot(content)
            TabBar(selected = tab, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    @Test
    fun picfort_server_form() = snap {
        // The dialog's content for each kind of test result (dialogs don't render in Paparazzi).
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp)) {
                listOf(
                    wallTestResult(null),
                    wallTestResult(PicfortServerException.NotAccepted()),
                    wallTestResult(PicfortServerException.NoWall()),
                    ConnectionTest.Failed("Can't reach the Picfort server at 192.168.0.105"),
                    ConnectionTest.Failed(WRONG_API_KEY),
                ).forEach { test ->
                    PicfortServerForm(
                        address = "192.168.0.105:5000", onAddressChange = {},
                        apiKey = "", onApiKeyChange = {}, test = test,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    @Test
    fun picfort_server_setup() = snap {
        PicfortServerSetupContent(
            address = "192.168.0.105:5000", onAddressChange = {}, test = wallTestResult(null),
        )
    }

    private val familyUsers = listOf(
        FamilyUserUi("mama", name = "Mama", immichName = "Anna Kowalska", avatarUrl = "avatar-mama",
            hasCustomPhoto = true, color = avatarColor("pink"), isMe = true),
        FamilyUserUi("tata", name = "Tata", immichName = "Tata", avatarUrl = null, color = avatarColor("blue")),
        FamilyUserUi("ola", name = "Ola", immichName = "Aleksandra K.", avatarUrl = "avatar-ola",
            hasCustomPhoto = true, color = avatarColor("green")),
        FamilyUserUi("babcia", name = "Babcia Zosia", immichName = "Zofia Nowak", avatarUrl = null, color = avatarColor("amber")),
        FamilyUserUi("test", name = "LukaszTest", immichName = "LukaszTest", avatarUrl = null,
            color = avatarColor("gray"), hidden = true),
    )

    @Test
    fun family_settings() = snap {
        FamilySettingsContent(FamilySettingsUi(users = familyUsers))
    }

    @Test
    fun family_settings_dark() = snap(dark = true) {
        FamilySettingsContent(FamilySettingsUi(users = familyUsers))
    }

    @Composable
    private fun WithSheet(background: @Composable () -> Unit, sheet: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            background()
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .padding(vertical = 12.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                    sheet()
                }
            }
        }
    }

    @Test
    fun family_member_sheet() = snap {
        WithSheet(
            background = { FamilySettingsContent(FamilySettingsUi(users = familyUsers)) },
            sheet = {
                FamilyMemberSheetContent(
                    FamilyMemberEditUi(
                        user = familyUsers[2], // Ola (Aleksandra K.), so the switch is enabled
                        displayName = "Ola",
                        previewAvatarUrl = "avatar-ola",
                        hasCustomPhoto = true,
                        showInFamily = true,
                    )
                )
            },
        )
    }

    @Test
    fun people_picker() = snap {
        val people = listOf("Ola", "Staś", "Mama", "Tata", "Babcia Zosia", "Dziadek Jan", "Ciocia Kasia", "Wujek Marek")
            .mapIndexed { i, name -> ImmichPersonItem(id = "p-$i", name = name, thumbnailUrl = "person-$i") }
        WithSheet(
            background = { FamilySettingsContent(FamilySettingsUi(users = familyUsers)) },
            sheet = { PeoplePickerSheetContent(PeoplePickerUi(people = people)) },
        )
    }

    private companion object {
        const val NOW = 1_752_500_000_000L
    }
}

