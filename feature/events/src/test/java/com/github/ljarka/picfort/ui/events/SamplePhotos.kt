@file:Suppress("ktlint:standard:max-line-length") // one palette per line

package com.github.ljarka.picfort.ui.events

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler

/** Sky top, sky bottom, sun, far hills, near hills. */
private val PALETTES = listOf(
    intArrayOf(0xFF1E3C72.toInt(), 0xFFF7B267.toInt(), 0xFFFFE29A.toInt(), 0xFF6B4E71.toInt(), 0xFF2E2440.toInt()), // dusk
    intArrayOf(0xFF4FACFE.toInt(), 0xFFC2E9FB.toInt(), 0xFFFFFFFF.toInt(), 0xFF7FB77E.toInt(), 0xFF3B7A57.toInt()), // summer
    intArrayOf(0xFFFF7E5F.toInt(), 0xFFFEB47B.toInt(), 0xFFFFF1C1.toInt(), 0xFFB5525C.toInt(), 0xFF5E2A3B.toInt()), // sunset
    intArrayOf(0xFF0F2027.toInt(), 0xFF2C5364.toInt(), 0xFFE0E0E0.toInt(), 0xFF203A43.toInt(), 0xFF0B1418.toInt()), // night
    intArrayOf(0xFF83A4D4.toInt(), 0xFFB6FBFF.toInt(), 0xFFFFF7D6.toInt(), 0xFFA3B18A.toInt(), 0xFF588157.toInt()), // spring
    intArrayOf(0xFFE0C3FC.toInt(), 0xFF8EC5FC.toInt(), 0xFFFFFFFF.toInt(), 0xFF9D8DF1.toInt(), 0xFF5D4E9E.toInt()), // lavender
    intArrayOf(0xFF56CCF2.toInt(), 0xFF2F80ED.toInt(), 0xFFFFF59D.toInt(), 0xFFE9C46A.toInt(), 0xFFF4A261.toInt()), // beach
    intArrayOf(0xFFD3CCE3.toInt(), 0xFFE9E4F0.toInt(), 0xFFFFFFFF.toInt(), 0xFF9FB4C7.toInt(), 0xFF5C7A99.toInt()), // winter
)

/** A deterministic, photo-like landscape for [seed]. */
fun samplePhoto(seed: Int, width: Int = 480, height: Int = 480): Bitmap {
    val p = PALETTES[Math.floorMod(seed, PALETTES.size)]
    val rnd = java.util.Random(seed.toLong())
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val w = width.toFloat()
    val h = height.toFloat()
    c.drawRect(
        0f,
        0f,
        w,
        h,
        Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h * 0.75f, p[0], p[1], Shader.TileMode.CLAMP)
        },
    )
    c.drawCircle(
        w * (0.2f + rnd.nextFloat() * 0.6f),
        h * (0.25f + rnd.nextFloat() * 0.25f),
        w * 0.09f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p[2] },
    )
    fun hills(base: Float, amp: Float, color: Int) {
        val path = Path().apply {
            moveTo(0f, h)
            lineTo(0f, base)
            val phase = rnd.nextFloat() * 6f
            var x = 0f
            while (x <= w) {
                lineTo(x, base - amp * kotlin.math.sin(phase + x / w * 5f) - amp * 0.4f * kotlin.math.sin(x / w * 13f))
                x += 8f
            }
            lineTo(w, h)
            close()
        }
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    }
    hills(h * 0.62f, h * 0.07f, p[3])
    hills(h * 0.8f, h * 0.05f, p[4])
    return bmp
}

fun samplePhotoImage(seed: Int, width: Int = 480, height: Int = 480): ImageBitmap =
    samplePhoto(seed, width, height).asImageBitmap()

/** Makes Coil's AsyncImage show sample photos (keyed by URL) instead of loading them. */
@OptIn(ExperimentalCoilApi::class)
@Composable
fun WithSamplePhotos(content: @Composable () -> Unit) {
    val handler = AsyncImagePreviewHandler { request -> samplePhoto(request.data.hashCode()).asImage() }
    // Coil only consults the preview handler in inspection mode.
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides handler,
        content = content,
    )
}
