package com.github.ljarka.picfort.ui.school

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.github.ljarka.picfort.core.ui.theme.AccentTheme
import com.github.ljarka.picfort.core.ui.theme.BrandAccent
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime
import java.util.Locale
import org.junit.Rule
import org.junit.Test

class SchoolScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private fun snap(dark: Boolean = false, content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(
            DeviceConfig.PIXEL_6.copy(nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT),
        )
        paparazzi.snapshot {
            PicfortTheme(darkTheme = dark, dynamicColor = false) {
                // As in the app: the School tab wears the sky blade.
                AccentTheme(BrandAccent.Sky) { content() }
            }
        }
    }

    private fun sheet(content: @Composable () -> Unit) = snap {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { content() }
    }

    private fun t(text: String) = LocalTime.parse(text)
    private val bells = fillBells(t("08:00"), 45, 10, 6)

    private fun lesson(id: Long, day: DayOfWeek, period: Int, title: String, room: String, teacher: String? = null) =
        bells[period - 1].let {
            SchoolEntry(id, EntryKind.LESSON, day, it.start, it.end, title, period, room, teacher)
        }

    private val ola = Child(
        id = 1,
        name = "Ola",
        color = "pink",
        periods = bells,
        entries = listOf(
            lesson(1, MONDAY, 1, "Maths", "12", "Mrs Nowak"),
            lesson(2, MONDAY, 2, "Polish", "7"),
            lesson(3, MONDAY, 3, "English", "21", "Mr Smith"),
            lesson(4, MONDAY, 4, "PE", "Gym"),
            lesson(5, MONDAY, 5, "Science", "14"),
            SchoolEntry(
                6,
                EntryKind.ACTIVITY,
                MONDAY,
                t("15:00"),
                t("16:00"),
                "Swimming",
                place = "Aquapark",
                note = "bring swimsuit",
            ),
            lesson(7, TUESDAY, 1, "Art", "3"),
            SchoolEntry(8, EntryKind.ACTIVITY, SATURDAY, t("10:00"), t("11:00"), "Football", place = "Orlik"),
        ),
    )
    private val stas = Child(id = 2, name = "Staś", color = "blue")

    private fun day(now: String, day: DayOfWeek = MONDAY, editing: Boolean = false, offline: String? = null) = SchoolUi(
        children = listOf(ola, stas),
        selectedId = 1,
        day = day,
        today = day,
        now = t(now),
        editing = editing,
        offlineSince = offline,
    )

    @Test
    fun before_school() = snap { SchoolContent(day("07:20")) }

    @Test
    fun during_school() = snap { SchoolContent(day("09:10")) }

    @Test
    fun after_school() = snap { SchoolContent(day("14:20")) }

    @Test
    fun weekend_with_activity() = snap { SchoolContent(day("09:00", SATURDAY)) }

    @Test
    fun empty_day() = snap { SchoolContent(day("09:00", WEDNESDAY)) }

    @Test
    fun no_children() = snap { SchoolContent(SchoolUi()) }

    @Test
    fun edit_mode() = snap { SchoolContent(day("09:10", editing = true)) }

    @Test
    fun offline() = snap { SchoolContent(day("09:10", offline = "07:45")) }

    @Test
    fun during_school_dark() = snap(dark = true) { SchoolContent(day("09:10")) }

    @Test
    fun twelve_hour_clock() = snap { SchoolContent(day("07:20"), Locale.US, is24Hour = false) }

    @Test
    fun entry_sheet_lesson() = sheet {
        EntryForm(ola, null, MONDAY, Locale.ENGLISH, timeFormatter(true, Locale.ENGLISH), false, { _, _, _ -> }, {}, {})
    }

    @Test
    fun entry_sheet_activity() = sheet {
        EntryForm(ola, ola.entries[5], MONDAY, Locale.ENGLISH, timeFormatter(true, Locale.ENGLISH), false, { _, _, _ ->
        }, {}, {})
    }

    @Test
    fun entry_sheet_no_bells() = sheet {
        EntryForm(stas, null, MONDAY, Locale.ENGLISH, timeFormatter(true, Locale.ENGLISH), false, { _, _, _ ->
        }, {}, {})
    }

    @Test
    fun bell_schedule() = sheet {
        BellsForm(ola, listOf(stas), timeFormatter(true, Locale.ENGLISH), false, {}, {})
    }

    @Test
    fun child_sheet() = sheet {
        ChildForm(ola, listOf("pink", "blue"), false, { _, _ -> }, {}, {})
    }
}
