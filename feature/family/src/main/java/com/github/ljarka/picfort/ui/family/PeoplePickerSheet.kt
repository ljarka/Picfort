package com.github.ljarka.picfort.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** A named person from the caller's Immich library (shared by T-032 and T-030). */
data class ImmichPersonItem(val id: String, val name: String, val thumbnailUrl: String)

data class PeoplePickerUi(
    val people: List<ImmichPersonItem> = emptyList(),
    /** The member's Immich account picture, offered as the first circle; null for none. */
    val profilePictureUrl: String? = null,
    val query: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val filtered: List<ImmichPersonItem>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return people
            return people.filter { it.name.contains(q, ignoreCase = true) }
        }

    /** The "Profile picture" circle shows while no search is typed. */
    val showsProfilePicture: Boolean get() = profilePictureUrl != null && query.isBlank()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeoplePickerSheet(
    state: PeoplePickerUi,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelect: (ImmichPersonItem) -> Unit,
    onSelectProfilePicture: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        PeoplePickerSheetContent(
            state = state,
            onQueryChange = onQueryChange,
            onSelect = onSelect,
            onSelectProfilePicture = onSelectProfilePicture,
        )
    }
}

@Composable
fun PeoplePickerSheetContent(
    state: PeoplePickerUi,
    onQueryChange: (String) -> Unit = {},
    onSelect: (ImmichPersonItem) -> Unit = {},
    onSelectProfilePicture: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(top = 8.dp, bottom = 16.dp),
    ) {
        Text("Choose from Immich", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (state.people.isNotEmpty() || state.query.isNotEmpty()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search names") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
        when {
            state.isLoading -> Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.error != null -> Text(
                state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 24.dp),
            )

            state.filtered.isEmpty() && !state.showsProfilePicture -> Text(
                if (state.people.isEmpty()) {
                    "No named people in your Immich library yet."
                } else {
                    "No people matching \"${state.query}\"."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.showsProfilePicture) {
                    item(key = "profile-picture") {
                        PickerCircle(state.profilePictureUrl, "Profile picture", onSelectProfilePicture, maxLines = 2)
                    }
                }
                items(state.filtered, key = { it.id }) { person ->
                    PickerCircle(person.thumbnailUrl, person.name, onClick = { onSelect(person) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Name people in Immich to see them here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PickerCircle(imageUrl: String?, label: String, onClick: () -> Unit, maxLines: Int = 1) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
