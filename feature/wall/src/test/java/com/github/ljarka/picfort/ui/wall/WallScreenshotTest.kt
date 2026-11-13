package com.github.ljarka.picfort.ui.wall

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.avatar.avatarColor
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test

class WallScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private fun snap(dark: Boolean = false, content: @Composable () -> Unit) {
        if (dark) paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
        paparazzi.snapshot {
            PicfortTheme(darkTheme = dark, dynamicColor = false) {
                content()
            }
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

    private val panelDay = LocalDate.of(2026, 11, 11)
    private val panelNow = LocalTime.of(12, 0)

    private fun panelEvent(
        id: Long,
        title: String,
        start: LocalDate = panelDay,
        end: LocalDate? = null,
        time: LocalTime? = null,
        location: String? = null,
    ) = FamilyEvent(
        id = id,
        title = title,
        startDate = start,
        endDate = end,
        startTime = time,
        location = location,
        created = "2026-11-0${id}T10:00:00Z",
    )

    private fun wallWithPanel(vararg events: FamilyEvent) = FamilyWallUiState(
        members = members,
        posts = wallPosts,
        myId = "mama",
        isLoading = false,
        panel = todayPanel(events.toList(), panelDay, panelNow),
    )

    @Test
    fun wall() = snap {
        FamilyWallContent(
            FamilyWallUiState(members = members, posts = wallPosts, myId = "mama", isLoading = false),
            now = NOW,
        )
    }

    @Test
    fun wall_dark() = snap(dark = true) {
        FamilyWallContent(
            FamilyWallUiState(members = members, posts = wallPosts, myId = "mama", isLoading = false),
            now = NOW,
        )
    }

    @Test
    fun wall_filtered() = snap {
        FamilyWallContent(
            FamilyWallUiState(
                members = members,
                posts = wallPosts,
                myId = "mama",
                isLoading = false,
                selectedMemberId = "tata",
            ),
            now = NOW,
        )
    }

    @Test
    fun wall_panel_event_today() = snap {
        FamilyWallContent(
            wallWithPanel(panelEvent(1, "Dentist", time = LocalTime.of(18, 0), location = "Przychodnia Zdrowie")),
            now = NOW,
        )
    }

    @Test
    fun wall_panel_trip_ongoing() = snap {
        FamilyWallContent(
            wallWithPanel(
                panelEvent(
                    1,
                    "Mazury trip",
                    start = panelDay.minusDays(1),
                    end = panelDay.plusDays(3),
                    location = "Giżycko",
                ),
            ),
            now = NOW,
        )
    }

    @Test
    fun wall_panel_several_events() = snap {
        FamilyWallContent(
            wallWithPanel(
                panelEvent(
                    1,
                    "Mazury trip",
                    start = panelDay.minusDays(1),
                    end = panelDay.plusDays(3),
                    location = "Giżycko",
                ),
                panelEvent(2, "Ola's football match", time = LocalTime.of(17, 30), location = "Orlik"),
                panelEvent(3, "Grandma's birthday"),
            ),
            now = NOW,
        )
    }

    @Test
    fun wall_panel_next_event() = snap {
        FamilyWallContent(
            wallWithPanel(panelEvent(1, "Grandpa's 80th birthday", start = panelDay.plusDays(12))),
            now = NOW,
        )
    }

    @Test
    fun wall_panel_date_only() = snap {
        FamilyWallContent(wallWithPanel(), now = NOW)
    }

    @Test
    fun wall_panel_empty_wall() = snap {
        FamilyWallContent(
            FamilyWallUiState(
                members = members,
                myId = "mama",
                isLoading = false,
                panel = todayPanel(listOf(panelEvent(1, "Dentist", time = LocalTime.of(18, 0))), panelDay, panelNow),
            ),
            now = NOW,
        )
    }

    @Test
    fun wall_panel_dark() = snap(dark = true) {
        FamilyWallContent(
            wallWithPanel(
                panelEvent(
                    1,
                    "Mazury trip",
                    start = panelDay.minusDays(1),
                    end = panelDay.plusDays(3),
                    location = "Giżycko",
                ),
                panelEvent(2, "Ola's football match", time = LocalTime.of(17, 30)),
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
        FamilyWallContent(FamilyWallUiState(configured = false, isLoading = false), now = NOW)
    }

    private companion object {
        const val NOW = 1_752_500_000_000L
    }
}
