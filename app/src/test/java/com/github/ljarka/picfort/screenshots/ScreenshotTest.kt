package com.github.ljarka.picfort.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.ljarka.picfort.ui.shell.ComingSoonContent
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
import com.github.ljarka.picfort.backup.BackupStatus
import java.time.Instant
import com.github.ljarka.picfort.ui.settings.BackupSettingsUi
import com.github.ljarka.picfort.ui.settings.SettingsContent
import com.github.ljarka.picfort.ui.theme.PicfortTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
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


    /** A tab root with the bottom bar over it, as in the app. */
    @Composable
    private fun WithTabBar(tab: Tab, content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            TabRoot(content)
            TabBar(selected = tab, onSelect = {}, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }



    private companion object {
        const val NOW = 1_752_500_000_000L
    }
}

