package com.github.ljarka.picfort.ui.wall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The quick reactions under an opened post (T-084), Slack-like: every emoji of [WALL_REACTIONS],
 * with its count, highlighted when it is mine; a tap adds or removes mine. Who reacted is listed
 * below.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReactionBar(
    post: WallPost,
    myId: String,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    Column(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WALL_REACTIONS.forEach { emoji ->
                val reaction = post.reactions.firstOrNull { it.emoji == emoji }
                val mine = reaction?.isMine(myId) == true
                ReactionChip(
                    emoji = emoji,
                    count = reaction?.count ?: 0,
                    mine = mine,
                    onDark = onDark,
                    onClick = { onToggle(emoji) },
                )
            }
        }
        val who = reactedText(post.reactions)
        if (who != null) {
            Text(
                who,
                style = MaterialTheme.typography.bodySmall,
                color = if (onDark) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ReactionChip(emoji: String, count: Int, mine: Boolean, onDark: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        mine -> scheme.primaryContainer
        onDark -> Color.White.copy(alpha = 0.15f)
        else -> scheme.surfaceContainerHigh
    }
    val content = when {
        mine -> scheme.onPrimaryContainer
        onDark -> Color.White
        else -> scheme.onSurface
    }
    val label = if (count > 0) "$emoji $count" else emoji
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        border = if (mine) BorderStroke(1.dp, scheme.primary) else null,
        modifier = Modifier.semantics {
            role = Role.Checkbox
            selected = mine
            contentDescription = if (count > 0) "$emoji, $count" else "React $emoji"
        },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** "❤️ 2  👍 1" in one line, for cards and tiles; nothing when no one reacted. */
@Composable
internal fun ReactionSummary(
    reactions: List<WallReaction>,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    if (reactions.isEmpty()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            reactionSummaryText(reactions),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun reactionSummaryText(reactions: List<WallReaction>): String =
    reactions.joinToString("  ") { "${it.emoji} ${it.count}" }

/** "❤️ Tata, Mama · 👍 Ola", or null when no one reacted. */
internal fun reactedText(reactions: List<WallReaction>): String? =
    reactions.takeIf { it.isNotEmpty() }?.joinToString(" · ") { "${it.emoji} ${it.names.joinToString(", ")}" }
