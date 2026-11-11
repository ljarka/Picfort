package com.github.ljarka.picfort.ui.events

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.github.ljarka.picfort.core.model.EventsSegment
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EventsScreenshotTest {

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
            )
        )
    }

    @Test
    fun events_upcoming_dark() = snap(dark = true) {
        EventsListContent(
            EventsListUi(
                today = sampleToday,
                segment = EventsSegment.UPCOMING,
                upcomingEvents = sampleUpcomingEvents,
            )
        )
    }

    @Test
    fun events_past() = snap {
        EventsListContent(
            EventsListUi(
                today = sampleToday,
                segment = EventsSegment.PAST,
                pastEvents = samplePastEvents,
            )
        )
    }

    @Test
    fun events_empty_upcoming() = snap {
        EventsListContent(
            EventsListUi(today = sampleToday, segment = EventsSegment.UPCOMING)
        )
    }

    @Test
    fun events_empty_past() = snap {
        EventsListContent(
            EventsListUi(today = sampleToday, segment = EventsSegment.PAST)
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
            )
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
            )
        )
    }
}
