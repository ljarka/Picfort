package com.github.ljarka.picfort.core.ui.avatar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.core.model.FamilyMember

private val AVATAR_SIZE = 56.dp

/**
 * The family's avatar row (Wall, Photos): tap selects one member (tap again to clear, the caller
 * decides), long-press offers "Edit". Members in [dimmedIds] are drawn faded (e.g. not linked).
 * With [onOpenFamily], a last "+" circle (labelled [addLabel], "Family" by default) opens Family
 * settings, or whatever the screen adds people with (School: pick a child).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemberCarousel(
    members: List<FamilyMember>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    /** Long-press "Edit"; null: no menu (e.g. Immich people in Search). */
    onEditMember: ((String) -> Unit)? = {},
    dimmedIds: Set<String> = emptySet(),
    onOpenFamily: (() -> Unit)? = null,
    addLabel: String = "Family",
    addClickLabel: String = "Family settings",
    /** Several selected at once (Search, people together); overrides [selectedId]. */
    selectedIds: Set<String> = setOfNotNull(selectedId),
    /** For scrolling from outside, e.g. to a person who just joined the row. */
    state: LazyListState = rememberLazyListState(),
) {
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(members, key = { it.id }) { member ->
            val selected = member.id in selectedIds
            var menu by remember { mutableStateOf(false) }
            Box {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) member.color.copy(alpha = 0.18f) else Color.Transparent)
                        .combinedClickable(
                            onClick = { onSelect(member.id) },
                            onLongClick = if (onEditMember != null) ({ menu = true }) else null,
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .alpha(if (member.id in dimmedIds) 0.4f else 1f),
                ) {
                    MemberAvatar(member, size = AVATAR_SIZE, ring = if (selected) 3.dp else 2.dp)
                    Text(
                        member.name,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontWeight = if (selected) FontWeight.Bold else null,
                        modifier = Modifier
                            .width(AVATAR_SIZE + 8.dp)
                            .padding(top = 4.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menu = false
                            onEditMember?.invoke(member.id)
                        },
                    )
                }
            }
        }
        if (onOpenFamily != null) {
            item(key = "picfort:family-settings") { AddCircle(addLabel, addClickLabel, onOpenFamily) }
        }
    }
}

@Composable
private fun AddCircle(label: String, clickLabel: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = clickLabel, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AVATAR_SIZE)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(AVATAR_SIZE + 8.dp)
                .padding(top = 4.dp),
        )
    }
}
