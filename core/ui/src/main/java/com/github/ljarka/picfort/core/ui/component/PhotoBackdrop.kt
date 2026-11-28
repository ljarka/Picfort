package com.github.ljarka.picfort.core.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * A screen's background tinted by a photo, like album art behind a music player (T-106, T-107):
 * the photo stretched over the screen (a small one already looks soft on any Android), blurred
 * further on Android 12+, under a veil of the theme background so text and cards stay readable in
 * light and dark mode. Crossfades when [url] changes; without one, the plain theme background.
 * Put it first in a Box, under a Scaffold with a transparent container.
 */
@Composable
fun PhotoBackdrop(url: String?, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize().background(base)) {
        Crossfade(targetState = url, animationSpec = tween(durationMillis = 900), label = "backdrop") { current ->
            if (current != null) {
                AsyncImage(
                    model = current,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(BACKDROP_BLUR, BlurredEdgeTreatment.Rectangle),
                )
            }
        }
        if (url != null) {
            Box(Modifier.fillMaxSize().background(base.copy(alpha = BACKDROP_VEIL)))
        }
    }
}

private val BACKDROP_BLUR = 40.dp

/** How much of the theme background covers the photo: enough for text, little enough for colour. */
private const val BACKDROP_VEIL = 0.62f
