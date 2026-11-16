package com.github.ljarka.picfort.core.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes [uri] upright (applying EXIF orientation), scales it down so the longest edge is at most
 * [maxEdge] pixels, and compresses it to JPEG with [jpegQuality].
 */
suspend fun compressImageUri(context: Context, uri: Uri, maxEdge: Int = 1920, jpegQuality: Int = 85): ByteArray =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) throw PicfortServerException.Other("Couldn't read the photo")
        var sample = 1
        while (longest / (sample * 2) >= maxEdge) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw PicfortServerException.Other("Couldn't read the photo")
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val upright = scaleAndRotate(decoded, orientation, maxEdge)
        ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
            out.toByteArray()
        }
    }

private fun scaleAndRotate(bitmap: Bitmap, orientation: Int, maxEdge: Int): Bitmap {
    val scale = maxEdge.toFloat() / max(bitmap.width, bitmap.height)
    val matrix = Matrix()
    if (scale < 1f) matrix.postScale(scale, scale)
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)

        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)

        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)

        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)

        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)

        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }

        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
    }
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
