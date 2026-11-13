package com.github.ljarka.picfort.ui.events

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.github.ljarka.picfort.core.model.DayPhoto
import com.github.ljarka.picfort.core.model.EventRepeat
import com.github.ljarka.picfort.core.model.EventsSegment
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.theme.AccentTheme
import com.github.ljarka.picfort.core.ui.theme.BrandAccent
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test

class EventsScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private fun snap(dark: Boolean = false, content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(
            DeviceConfig.PIXEL_6.copy(
                nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT,
            ),
        )
        paparazzi.snapshot {
            PicfortTheme(darkTheme = dark, dynamicColor = false) {
                // As in the app: the Events tab wears the amber blade.
                AccentTheme(BrandAccent.Amber) { content() }
            }
        }
    }

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

    private val samplePastEvents = listOf(
        FamilyEvent(
            id = 10,
            title = "All Saints' Day visit",
            startDate = LocalDate.of(2026, 11, 1),
            location = "Kraków",
        ),
        FamilyEvent(
            id = 11,
            title = "Staś's 7th birthday",
            startDate = LocalDate.of(2026, 10, 18),
            startTime = LocalTime.of(15, 0),
            location = "Home",
        ),
        FamilyEvent(
            id = 12,
            title = "Baltic seaside holiday",
            startDate = LocalDate.of(2026, 8, 12),
            endDate = LocalDate.of(2026, 8, 22),
            location = "Jastarnia",
        ),
    )

    @Test
    fun events_no_server() = snap {
        EventsListContent(EventsListUi(configured = false, isLoading = false))
    }

    @Test
    fun events_upcoming() = snap {
        EventsListContent(
            EventsListUi(
                today = sampleToday,
                segment = EventsSegment.UPCOMING,
                upcomingEvents = sampleUpcomingEvents,
            ),
        )
    }

    @Test
    fun events_upcoming_dark() = snap(dark = true) {
        EventsListContent(
            EventsListUi(
                today = sampleToday,
                segment = EventsSegment.UPCOMING,
                upcomingEvents = sampleUpcomingEvents,
            ),
        )
    }

    @Test
    fun events_past() = snap {
        EventsListContent(
            EventsListUi(
                today = sampleToday,
                segment = EventsSegment.PAST,
                pastEvents = samplePastEvents,
            ),
        )
    }

    @Test
    fun events_empty_upcoming() = snap {
        EventsListContent(
            EventsListUi(today = sampleToday, segment = EventsSegment.UPCOMING),
        )
    }

    @Test
    fun events_empty_past() = snap {
        EventsListContent(
            EventsListUi(today = sampleToday, segment = EventsSegment.PAST),
        )
    }

    @Test
    fun event_detail() = snap {
        EventDetailContent(
            EventDetailUi(
                event = sampleUpcomingEvents[0],
                today = sampleToday,
                authorName = "Mama",
                canDelete = true,
            ),
        )
    }

    @Test
    fun event_form() = snap {
        EventFormContent(
            EventFormUi(
                isEditing = false,
                title = "Skiing in Białka",
                startDate = LocalDate.of(2027, 1, 3),
                endDate = LocalDate.of(2027, 1, 10),
                startTime = LocalTime.of(8, 30),
                location = "Białka Tatrzańska",
                note = "Book ski school for Staś and Ola before December.",
                today = sampleToday,
            ),
        )
    }

    // --- T-037: repeat and birthdays ---

    private val members = mapOf("ola" to "Ola", "tata" to "Tata")

    private val swimming = FamilyEvent(
        id = 20,
        title = "Swimming lessons",
        startDate = LocalDate.of(2026, 11, 13),
        startTime = LocalTime.of(16, 0),
        location = "Aquapark Fala",
        createdBy = "tata",
        repeat = EventRepeat.WEEKLY,
        repeatUntil = LocalDate.of(2027, 6, 25),
        seriesStartDate = LocalDate.of(2026, 9, 4),
    )

    private val olaBirthday = FamilyEvent(
        id = 21,
        title = "Ola's birthday",
        startDate = LocalDate.of(2026, 11, 16),
        repeat = EventRepeat.YEARLY,
        seriesStartDate = LocalDate.of(2017, 11, 16),
        birthdayOf = "ola",
        turns = 9,
    )

    private val tataBirthday = FamilyEvent(
        id = 22,
        title = "Piotr's birthday",
        startDate = LocalDate.of(2026, 12, 2),
        repeat = EventRepeat.YEARLY,
        seriesStartDate = LocalDate.of(1988, 12, 2),
        birthdayOf = "tata",
        turns = 38,
    )

    private val withRepeats = (sampleUpcomingEvents + swimming + olaBirthday + tataBirthday)

    @Test
    fun events_upcoming_repeat_birthdays() = snap {
        EventsListContent(
            EventsListUi(today = sampleToday, upcomingEvents = withRepeats, memberNames = members),
        )
    }

    @Test
    fun events_upcoming_repeat_birthdays_dark() = snap(dark = true) {
        EventsListContent(
            EventsListUi(today = sampleToday, upcomingEvents = withRepeats, memberNames = members),
        )
    }

    @Test
    fun event_detail_birthday() = snap {
        EventDetailContent(EventDetailUi(event = olaBirthday, today = sampleToday, memberNames = members))
    }

    @Test
    fun event_detail_repeating() = snap {
        EventDetailContent(
            EventDetailUi(event = swimming, today = sampleToday, authorName = "Tata", canDelete = true),
        )
    }

    @Test
    fun event_form_repeat() = snap {
        EventFormContent(
            EventFormUi(
                isEditing = true,
                title = "Swimming lessons",
                startDate = LocalDate.of(2026, 9, 4),
                startTime = LocalTime.of(16, 0),
                location = "Aquapark Fala",
                repeat = EventRepeat.WEEKLY,
                repeatUntil = LocalDate.of(2027, 6, 25),
                editsSeries = true,
                today = sampleToday,
            ),
        )
    }

    // --- T-053: who the event is for ---

    private val family = listOf(
        FamilyMember("mama", "Mama", null, Color(0xFFD9708F)),
        FamilyMember("tata", "Tata", null, Color(0xFF5B8DD6)),
        FamilyMember("ola", "Ola", null, Color(0xFF4FAF7A)),
        FamilyMember("stas", "Staś", null, Color(0xFFE08A3C), isImmichUser = false),
        FamilyMember("kuba", "Kuba", null, Color(0xFF8C7AE6), isImmichUser = false),
    )

    private val withWho = listOf(
        sampleUpcomingEvents[0].copy(memberIds = listOf("mama", "tata", "ola", "stas", "kuba")),
        sampleUpcomingEvents[1].copy(memberIds = listOf("ola")),
        swimming.copy(memberIds = listOf("ola", "stas")),
        olaBirthday,
        sampleUpcomingEvents[2],
    )

    @Test
    fun events_upcoming_who() = snap {
        EventsListContent(
            EventsListUi(today = sampleToday, upcomingEvents = withWho, memberNames = members, members = family),
        )
    }

    @Test
    fun events_upcoming_who_dark() = snap(dark = true) {
        EventsListContent(
            EventsListUi(today = sampleToday, upcomingEvents = withWho, memberNames = members, members = family),
        )
    }

    @Test
    fun event_detail_who() = snap {
        EventDetailContent(
            EventDetailUi(
                event = withWho[2],
                today = sampleToday,
                authorName = "Tata",
                canDelete = true,
                members = family,
            ),
        )
    }

    @Test
    fun event_form_who() = snap {
        EventFormContent(
            EventFormUi(
                isEditing = true,
                title = "Swimming lessons",
                startDate = LocalDate.of(2026, 9, 4),
                startTime = LocalTime.of(16, 0),
                location = "Aquapark Fala",
                repeat = EventRepeat.WEEKLY,
                editsSeries = true,
                memberIds = listOf("ola", "stas"),
                members = family,
                today = sampleToday,
            ),
        )
    }

    // --- T-054: filter by member ---

    @Test
    fun events_upcoming_filter_ola() = snap {
        EventsListContent(
            EventsListUi(
                today = sampleToday,
                upcomingEvents = withWho,
                memberNames = members,
                members = family,
                selectedMemberId = "ola",
            ),
        )
    }

    @Test
    fun events_upcoming_filter_ola_dark() = snap(dark = true) {
        EventsListContent(
            EventsListUi(
                today = sampleToday,
                upcomingEvents = withWho,
                memberNames = members,
                members = family,
                selectedMemberId = "ola",
            ),
        )
    }

    @Test
    fun events_upcoming_filter_empty() = snap {
        EventsListContent(
            EventsListUi(
                today = sampleToday,
                upcomingEvents = withWho.drop(1),
                memberNames = members,
                members = family,
                selectedMemberId = "tata",
            ),
        )
    }

    // --- T-055: photos from past birthdays ---

    private fun photo(id: String, day: LocalDate, ratio: Float, video: Boolean = false) =
        DayPhoto(id, day, ratio, "https://immich.test/$id", video)

    private val birthdayYearsSample: List<BirthdayYear> = run {
        val d25 = LocalDate.of(2025, 11, 16)
        val d24 = LocalDate.of(2024, 11, 16)
        val d22 = LocalDate.of(2022, 11, 16)
        val d17 = LocalDate.of(2017, 11, 16)
        birthdayYears(
            birthdayDays(olaBirthday, sampleToday),
            listOf(
                photo("a1", d25, 1.5f), photo("a2", d25, 0.75f), photo("a3", d25, 1.33f),
                photo("a4", d25, 0.75f, video = true), photo("a5", d25, 1.5f),
                photo("b1", d24, 1.33f), photo("b2", d24, 0.75f), photo("b3", d24, 1.5f),
                photo("c1", d22, 0.75f), photo("c2", d22, 1.5f), photo("c3", d22, 1.0f), photo("c4", d22, 1.33f),
                photo("d1", d17, 1.33f), photo("d2", d17, 0.75f),
            ),
        )
    }

    private fun birthdayWithPhotos(photos: BirthdayPhotosUi) = EventDetailUi(
        event = olaBirthday.copy(memberIds = listOf("ola")),
        today = sampleToday,
        memberNames = members,
        members = family,
        birthdayPhotos = photos,
    )

    @Test
    fun event_detail_birthday_photos() = snap {
        WithSamplePhotos { EventDetailContent(birthdayWithPhotos(BirthdayPhotosUi(false, birthdayYearsSample))) }
    }

    @Test
    fun event_detail_birthday_photos_long() {
        paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_6.copy(screenHeight = 4600))
        paparazzi.snapshot {
            PicfortTheme(darkTheme = false, dynamicColor = false) {
                AccentTheme(BrandAccent.Amber) {
                    WithSamplePhotos {
                        EventDetailContent(birthdayWithPhotos(BirthdayPhotosUi(false, birthdayYearsSample)))
                    }
                }
            }
        }
    }

    @Test
    fun event_detail_birthday_photos_dark() = snap(dark = true) {
        WithSamplePhotos { EventDetailContent(birthdayWithPhotos(BirthdayPhotosUi(false, birthdayYearsSample))) }
    }

    @Test
    fun event_detail_birthday_no_photos() = snap {
        EventDetailContent(birthdayWithPhotos(BirthdayPhotosUi(loading = false)))
    }
}
