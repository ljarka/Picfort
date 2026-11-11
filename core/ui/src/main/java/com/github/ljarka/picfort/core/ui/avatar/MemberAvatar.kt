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

/** Immich's avatar color names → ring colors. Unknown or missing → primary. */
val AVATAR_COLORS = mapOf(
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
