package com.github.ljarka.picfort.ui.wall

import com.github.ljarka.picfort.core.model.isTestAccount
import com.github.ljarka.picfort.core.model.meFirst

import com.github.ljarka.picfort.core.network.PicfortServerException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.github.ljarka.picfort.core.ui.avatar.UNKNOWN_MEMBER_COLOR
import com.github.ljarka.picfort.core.ui.avatar.avatarColor
import com.github.ljarka.picfort.core.ui.avatar.initials
import com.github.ljarka.picfort.core.network.AccessTokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.github.ljarka.picfort.core.model.BirthDate
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** A family member: an Immich user or manual member (T-002, T-034). */
typealias FamilyMember = com.github.ljarka.picfort.core.model.FamilyMember

@Singleton
class WallRepository @Inject constructor(
    private val api: WallApi,
    private val accessTokenStore: AccessTokenStore,
    @ApplicationContext private val context: Context,
) {
    val isConfigured: Boolean get() = api.isConfigured
    val myId: String get() = accessTokenStore.userId

    suspend fun posts(): List<WallPost> = api.posts()

    /** [photo] is compressed before upload (longest edge 1920 px, JPEG 85%). */
    suspend fun create(text: String?, photo: Uri?): WallPost =
        api.create(text?.trim()?.takeIf { it.isNotEmpty() }, photo?.let { compress(it) })

    suspend fun delete(id: Int) = api.delete(id)

    suspend fun togglePin(id: Int): WallPost = api.togglePin(id)

    private suspend fun compress(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) throw PicfortServerException.Other("Couldn't read the photo")
        var sample = 1
        while (longest / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw PicfortServerException.Other("Couldn't read the photo")
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val upright = scaleAndRotate(decoded, orientation)
        ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    private fun scaleAndRotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val scale = MAX_EDGE.toFloat() / max(bitmap.width, bitmap.height)
        val matrix = Matrix()
        if (scale < 1f) matrix.postScale(scale, scale)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private companion object {
        const val MAX_EDGE = 1920
        const val JPEG_QUALITY = 85
    }
}
