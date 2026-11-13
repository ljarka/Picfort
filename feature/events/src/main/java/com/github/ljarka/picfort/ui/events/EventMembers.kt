package com.github.ljarka.picfort.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.avatar.MemberAvatar

/** The members of [ids] that still exist, in that order (a removed member is left out). */
fun membersOf(ids: List<String>, members: List<FamilyMember>): List<FamilyMember> {
    val byId = members.associateBy { it.id }
    return ids.mapNotNull(byId::get)
}

/** Overlapping avatars, at most [max]; the rest as "+N" (T-053). */
@Composable
fun MemberStack(members: List<FamilyMember>, size: Dp = 28.dp, max: Int = 3) {
    if (members.isEmpty()) return
    val shown = members.take(if (members.size > max) max - 1 else max)
    val rest = members.size - shown.size
    val overlap = size / 3
    Row(horizontalArrangement = Arrangement.spacedBy(-overlap)) {
        shown.forEach { member ->
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                MemberAvatar(member, size = size, ring = 1.5.dp)
            }
        }
        if (rest > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            ) {
                Text("+$rest", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** "Who": one chip per family member; tap to add or remove (T-053). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhoField(members: List<FamilyMember>, selected: List<String>, onToggle: (String) -> Unit) {
    if (members.isEmpty()) return
    androidx.compose.foundation.layout.Column {
        Text(
            "Who is it for",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            members.forEach { member ->
                val isOn = member.id in selected
                FilterChip(
                    selected = isOn,
                    onClick = { onToggle(member.id) },
                    label = { Text(member.name) },
                    leadingIcon = {
                        if (isOn) {
                            Icon(Icons.Default.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize))
                        } else {
                            MemberAvatar(member, size = 24.dp, ring = 1.dp)
                        }
                    },
                )
            }
        }
    }
}

/** Detail line: avatars and "Ola, Kuba". */
@Composable
fun WhoLine(members: List<FamilyMember>) {
    if (members.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        MemberStack(members, size = 28.dp, max = 4)
        Spacer(Modifier.width(8.dp))
        Text(members.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Events about [memberId] (birthdays included, see [FamilyEvent.who]); all of them when null. */
fun List<FamilyEvent>.forMember(memberId: String?): List<FamilyEvent> =
    if (memberId == null) this else filter { memberId in it.who }
