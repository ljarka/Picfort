package com.github.ljarka.picfort.ui.timeline

import androidx.compose.ui.graphics.asImageBitmap
import kotlin.io.encoding.Base64
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A decoded ThumbHash: [width] × [height] pixels as ARGB ints, row by row. */
class ThumbHashImage(val width: Int, val height: Int, val argb: IntArray)

/**
 * Decodes an Immich `thumbhash` (base64 of a ThumbHash, https://evanw.github.io/thumbhash/)
 * into a tiny blurred preview, at most 32 px on the long side. Returns null for bad input.
 *
 * Port of the reference `thumbHashToRGBA`.
 */
fun decodeThumbHash(base64: String): ThumbHashImage? {
    val hash = try {
        Base64.decode(base64).map { it.toInt() and 0xFF }
    } catch (e: IllegalArgumentException) {
        return null
    }
    if (hash.size < 5) return null
    return try {
        decode(hash)
    } catch (e: IndexOutOfBoundsException) {
        null
    }
}

private fun decode(hash: List<Int>): ThumbHashImage {
    val header24 = hash[0] or (hash[1] shl 8) or (hash[2] shl 16)
    val header16 = hash[3] or (hash[4] shl 8)
    val lDc = (header24 and 63) / 63.0
    val pDc = ((header24 shr 6) and 63) / 31.5 - 1.0
    val qDc = ((header24 shr 12) and 63) / 31.5 - 1.0
    val lScale = ((header24 shr 18) and 31) / 31.0
    val hasAlpha = (header24 shr 23) != 0
    val pScale = ((header16 shr 3) and 63) / 63.0
    val qScale = ((header16 shr 9) and 63) / 63.0
    val isLandscape = (header16 shr 15) != 0
    val lx = max(3, if (isLandscape) (if (hasAlpha) 5 else 7) else header16 and 7)
    val ly = max(3, if (isLandscape) header16 and 7 else (if (hasAlpha) 5 else 7))
    val aDc = if (hasAlpha) (hash[5] and 15) / 15.0 else 1.0
    val aScale = (hash[5] shr 4) / 15.0

    // Varying factors; saturation boosted 1.25x to make up for quantization.
    val acStart = if (hasAlpha) 6 else 5
    var acIndex = 0
    fun channel(nx: Int, ny: Int, scale: Double): DoubleArray {
        val ac = ArrayList<Double>()
        for (cy in 0 until ny) {
            var cx = if (cy > 0) 0 else 1
            while (cx * ny < nx * (ny - cy)) {
                val nibble = (hash[acStart + (acIndex shr 1)] shr ((acIndex and 1) shl 2)) and 15
                ac += (nibble / 7.5 - 1.0) * scale
                acIndex++
                cx++
            }
        }
        return ac.toDoubleArray()
    }
    val lAc = channel(lx, ly, lScale)
    val pAc = channel(3, 3, pScale * 1.25)
    val qAc = channel(3, 3, qScale * 1.25)
    val aAc = if (hasAlpha) channel(5, 5, aScale) else DoubleArray(0)

    val ratio = approximateAspectRatio(hash)
    val w = (if (ratio > 1) 32.0 else 32.0 * ratio).roundToInt().coerceAtLeast(1)
    val h = (if (ratio > 1) 32.0 / ratio else 32.0).roundToInt().coerceAtLeast(1)
    val argb = IntArray(w * h)
    val fx = DoubleArray(max(lx, if (hasAlpha) 5 else 3))
    val fy = DoubleArray(max(ly, if (hasAlpha) 5 else 3))
    for (y in 0 until h) {
        for (cy in fy.indices) fy[cy] = cos(PI / h * (y + 0.5) * cy)
        for (x in 0 until w) {
            for (cx in fx.indices) fx[cx] = cos(PI / w * (x + 0.5) * cx)
            var l = lDc
            var p = pDc
            var q = qDc
            var a = aDc

            var j = 0
            for (cy in 0 until ly) {
                val fy2 = fy[cy] * 2
                var cx = if (cy > 0) 0 else 1
                while (cx * ly < lx * (ly - cy)) {
                    l += lAc[j++] * fx[cx] * fy2
                    cx++
                }
            }
            j = 0
            for (cy in 0 until 3) {
                val fy2 = fy[cy] * 2
                var cx = if (cy > 0) 0 else 1
                while (cx < 3 - cy) {
                    val f = fx[cx] * fy2
                    p += pAc[j] * f
                    q += qAc[j] * f
                    j++
                    cx++
                }
            }
            if (hasAlpha) {
                j = 0
                for (cy in 0 until 5) {
                    val fy2 = fy[cy] * 2
                    var cx = if (cy > 0) 0 else 1
                    while (cx < 5 - cy) {
                        a += aAc[j++] * fx[cx] * fy2
                        cx++
                    }
                }
            }

            val b = l - 2.0 / 3.0 * p
            val r = (3 * l - b + q) / 2
            val g = r - q
            argb[y * w + x] = (channelByte(a) shl 24) or (channelByte(r) shl 16) or
                (channelByte(g) shl 8) or channelByte(b)
        }
    }
    return ThumbHashImage(w, h, argb)
}

private fun channelByte(v: Double): Int = (255 * min(1.0, max(0.0, v))).toInt()

private fun approximateAspectRatio(hash: List<Int>): Double {
    val header = hash[3]
    val hasAlpha = (hash[2] and 0x80) != 0
    val isLandscape = (hash[4] and 0x80) != 0
    val lx = if (isLandscape) (if (hasAlpha) 5 else 7) else header and 7
    val ly = if (isLandscape) header and 7 else (if (hasAlpha) 5 else 7)
    return lx.toDouble() / ly
}

/** [decodeThumbHash] as a bitmap for Compose; null for bad input. */
fun thumbHashBitmap(base64: String): androidx.compose.ui.graphics.ImageBitmap? {
    val image = decodeThumbHash(base64) ?: return null
    return android.graphics.Bitmap
        .createBitmap(image.argb, image.width, image.height, android.graphics.Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}
