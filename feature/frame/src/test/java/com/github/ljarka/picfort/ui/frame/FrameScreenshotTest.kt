package com.github.ljarka.picfort.ui.frame

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.github.ljarka.picfort.core.network.PicfortServerException
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import org.junit.Rule
import org.junit.Test

class FrameScreenshotTest {

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

    private fun samplePhotoImage(width: Int = 450, height: Int = 600): ImageBitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF2C5364.toInt())
        c.drawCircle(width * 0.5f, height * 0.4f, width * 0.25f, Paint().apply { color = 0xFFFFE29A.toInt() })
        return bmp.asImageBitmap()
    }

    @Test
    fun frame() = snap {
        FrameContent(
            FrameUi(
                address = "http://192.168.0.105:5000",
                loading = false,
                image = samplePhotoImage(),
                slideshow = SlideshowState(
                    mode = "album",
                    albumId = "a1",
                    intervalMinutes = 360,
                    quietStart = 22,
                    quietEnd = 7,
                    nextChangeAt = null,
                ),
                albums = listOf(FrameAlbum("a1", "Frame favourites"), FrameAlbum("a2", "Family")),
            ),
        )
    }

    @Test
    fun frame_browser_link() = snap {
        FrameContent(
            FrameUi(
                address = "https://picfort.example.com",
                hasKey = true,
                loading = false,
                image = samplePhotoImage(),
                slideshow = SlideshowState(mode = "off", nextChangeAt = null),
            ),
        )
    }

    @Test
    fun frame_not_configured() = snap {
        FrameContent(FrameUi(configured = false, loading = false))
    }

    @Test
    fun picfort_server_form() = snap {
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
                        address = "192.168.0.105:5000",
                        onAddressChange = {},
                        apiKey = "",
                        onApiKeyChange = {},
                        test = test,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    @Test
    fun picfort_server_setup() = snap {
        PicfortServerSetupContent(
            address = "192.168.0.105:5000",
            onAddressChange = {},
            test = wallTestResult(null),
        )
    }
}
