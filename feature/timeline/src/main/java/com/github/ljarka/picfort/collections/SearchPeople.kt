package com.github.ljarka.picfort.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.avatar.MemberAvatar
import java.text.Normalizer

/** People shown in the row under the search field; the rest are under "All people". */
const val PEOPLE_IN_ROW = 12

/**
 * The row: the selected people first (so they stay visible), then the others in Immich's order
 * (most photos first), [limit] in all.
 */
fun peopleRow(people: List<SearchPerson>, selected: Set<String>, limit: Int = PEOPLE_IN_ROW): List<SearchPerson> {
    val (picked, others) = people.partition { it.id in selected }
    return picked + others.take((limit - picked.size).coerceAtLeast(0))
}

/** "All people" filter: [text] anywhere in the name, ignoring case and accents ("lukasz" finds "Łukasz"). */
fun filterPeople(people: List<SearchPerson>, text: String): List<SearchPerson> {
    val needle = fold(text.trim())
    if (needle.isEmpty()) return people
    return people.filter { fold(it.name).contains(needle) }
}

private fun fold(s: String): String =
    Normalizer.normalize(s.lowercase().replace('ł', 'l'), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

/**
 * "All people": every named person in Immich as a grid of faces, with a name filter. Tapping
 * picks or drops a person right away (the results behind update); Done closes it.
 */
@Composable
fun PeoplePickerContent(
    people: List<SearchPerson>,
    selected: Set<String>,
    personThumbnail: (String) -> String?,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    val shown = remember(people, filter) { filterPeople(people, filter) }
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
        ) {
            Text(
                if (selected.isEmpty()) "All people" else "All people · ${selected.size} picked",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDone) { Text("Done") }
        }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            singleLine = true,
            placeholder = { Text("Find a person") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (shown.isEmpty()) {
            Text(
                "No one called \"${filter.trim()}\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(84.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
        ) {
            items(shown, key = { it.id }) { person ->
                val isSelected = person.id in selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) accent.copy(alpha = 0.18f) else Color.Transparent)
                        .toggleable(value = isSelected, role = Role.Checkbox) { onToggle(person.id) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    MemberAvatar(
                        FamilyMember(person.id, person.name, personThumbnail(person.id), accent),
                        size = 64.dp,
                        ring = if (isSelected) 3.dp else 1.dp,
                    )
                    Text(
                        person.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
