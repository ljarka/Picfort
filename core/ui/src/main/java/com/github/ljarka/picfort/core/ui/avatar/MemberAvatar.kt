package com.github.ljarka.picfort.core.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.core.model.FamilyMember

// Ring colors from the app icon's aperture blades; mid tones so rings read on cream and on night.
private val Coral = Color(0xFFE8604F)
private val Amber = Color(0xFFE0A030)
private val Mint = Color(0xFF3DAE78)
private val Sky = Color(0xFF3A9CC8)
private val Blue = Color(0xFF4F86D6)
private val Periwinkle = Color(0xFF6676E0)

/** Immich's avatar color names → ring colors, tuned to the aperture palette. */
val AVATAR_COLORS = mapOf(
    "pink" to Color(0xFFD9709E),
    "red" to Coral,
    "orange" to Color(0xFFEE7F3A),
    "yellow" to Amber,
    "amber" to Amber,
    "green" to Mint,
    "teal" to Sky,
    "blue" to Blue,
    "purple" to Periwinkle,
    "gray" to Color(0xFF7A808A),
)

/**
 * Colors handed out, in order, to members with no color of their own ("primary" is Immich's
 * default, so nobody chose it): five clearly different aperture blades.
 */
private val APERTURE = listOf(Coral, Mint, Periwinkle, Amber, Sky)

/** Whether [name] is a color the member picked in Immich (not the default "primary"). */
fun isChosenAvatarColor(name: String?): Boolean = name in AVATAR_COLORS

/** Ring color for authors who aren't Immich users (any more). */
val UNKNOWN_MEMBER_COLOR = Color(0xFF9E9E9E)

/**
 * The member's ring color: the Immich color they picked, else aperture blade number
 * [fallbackIndex] (the member's place among those without a color), so up to five members
 * who never picked a color all look different.
 */
fun avatarColor(name: String?, fallbackIndex: Int = 0): Color =
    AVATAR_COLORS[name] ?: APERTURE[Math.floorMod(fallbackIndex, APERTURE.size)]

/** "Anna Maria Kowalska" → "AK", "mama" → "M". */
fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}

/** Profile picture over initials: if the picture can't load, the initials stay visible. */
@Composable
fun MemberAvatar(member: FamilyMember, size: Dp, ring: Dp = 2.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .border(ring, member.color, CircleShape)
            .padding(ring + 1.dp)
            .clip(CircleShape)
            .background(member.color.copy(alpha = 0.25f)),
    ) {
        Text(
            initials(member.name),
            style = if (size >= 48.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (member.avatarUrl != null) {
            AsyncImage(
                model = member.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
