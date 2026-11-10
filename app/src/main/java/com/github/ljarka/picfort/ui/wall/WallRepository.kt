package com.github.ljarka.picfort.ui.wall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.github.ljarka.picfort.db.UserEntity
import com.github.ljarka.picfort.db.UsersDao
import com.github.ljarka.picfort.ui.login.AccessTokenStore
import com.github.ljarka.picfort.ui.server.ServerUrlStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** A family member: an Immich user of the server (T-002). */
data class FamilyMember(
    val id: String,
    val name: String,
    /** Profile picture URL on Immich, or null to show initials. */
    val avatarUrl: String?,
    val color: Color,
)

/** Immich's avatar color names → ring colors. Unknown or missing → primary. */
private val AVATAR_COLORS = mapOf(
    "primary" to Color(0xFF4250AF),
    "pink" to Color(0xFFDE7FB3),
    "red" to Color(0xFFD04040),
    "yellow" to Color(0xFFE0B820),
    "blue" to Color(0xFF3B82F6),
    "green" to Color(0xFF22A06B),
    "purple" to Color(0xFF8B5CF6),
    "orange" to Color(0xFFF08A24),
    "gray" to Color(0xFF6B7280),
    "amber" to Color(0xFFD97706),
)

/** Ring color for authors who aren't Immich users (any more). */
val UNKNOWN_MEMBER_COLOR = Color(0xFF9E9E9E)

fun avatarColor(name: String?): Color = AVATAR_COLORS[name] ?: AVATAR_COLORS.getValue("primary")

/** "Anna Maria Kowalska" → "AK", "mama" → "M". */
fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}

/** The logged-in user first, then by name (the DAO already sorts by name). */
fun List<FamilyMember>.meFirst(myId: String): List<FamilyMember> =
    sortedBy { if (it.id == myId) 0 else 1 }

@Singleton
class WallRepository @Inject constructor(
    private val api: WallApi,
    private val usersDao: UsersDao,
    private val serverUrlStore: ServerUrlStore,
    private val accessTokenStore: AccessTokenStore,
    @ApplicationContext private val context: Context,
) {
    val isConfigured: Boolean get() = api.isConfigured
    val myId: String get() = accessTokenStore.userId

    val members: Flow<List<FamilyMember>> = usersDao.all().map { users ->
        users.map { it.toMember() }.meFirst(myId)
    }

    suspend fun posts(): List<WallPost> = api.posts()

    /** [photo] is compressed before upload (longest edge 1920 px, JPEG 85%). */
    suspend fun create(text: String?, photo: Uri?): WallPost =
        api.create(text?.trim()?.takeIf { it.isNotEmpty() }, photo?.let { compress(it) })

    suspend fun delete(id: Int) = api.delete(id)

    suspend fun togglePin(id: Int): WallPost = api.togglePin(id)

    private fun UserEntity.toMember() = FamilyMember(
        id = id,
        name = name,
        avatarUrl = if (hasProfileImage) {
            // profileChangedAt busts Coil's cache when the picture changes.
            "${serverUrlStore.serverUrl}/api/users/$id/profile-image?v=${profileChangedAt ?: 0}"
        } else null,
        color = avatarColor(avatarColor),
    )

    private suspend fun compress(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = max(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) throw WallException.Other("Couldn't read the photo")
        var sample = 1
        while (longest / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw WallException.Other("Couldn't read the photo")
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
