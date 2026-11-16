package com.github.ljarka.picfort.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.core.model.EventAlbum
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.isTestAccount

/** Immich user IDs of the rest of the family (excluding [myId] and test accounts). */
internal fun familyUserIds(immichUsers: List<FamilyMember>, myId: String): List<String> =
    immichUsers.filter { it.isImmichUser && it.id != myId && !isTestAccount(it.name) }.map { it.id }

internal fun missingFamilyMembers(album: EventAlbum, familyIds: List<String>): List<String> =
    familyIds.filter { it !in album.memberIds }

internal fun isSharedWithFamily(album: EventAlbum, familyIds: List<String>): Boolean =
    missingFamilyMembers(album, familyIds).isEmpty()

@Composable
internal fun AlbumCoverThumb(
    thumbnailUrl: String?,
    contentDescription: String?,
    sizeDp: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = contentDescription?.take(1)?.uppercase().orEmpty().ifEmpty { "A" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun EventAlbumDetailCard(album: EventAlbum?, unavailable: Boolean, onClick: () -> Unit) {
    if (album == null && !unavailable) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(if (album != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(14.dp),
    ) {
        if (album != null) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AlbumCoverThumb(album.thumbnailUrl, album.name, sizeDp = 56)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (album.assetCount == 1) "1 photo" else "${album.assetCount} photos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open album",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Text(
                text = "Album not available to you",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EventAlbumPickerSheet(
    eventTitle: String,
    selectedAlbumId: String?,
    createNewAlbum: Boolean,
    albums: List<EventAlbum>,
    isLoading: Boolean,
    error: String?,
    onSelectNone: () -> Unit,
    onSelectCreate: () -> Unit,
    onSelectAlbum: (EventAlbum) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(albums, query) {
        val q = query.trim()
        if (q.isEmpty()) albums else albums.filter { it.name.contains(q, ignoreCase = true) }
    }
    val newAlbumName = eventTitle.trim().ifEmpty { "New event album" }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = "Link Immich album",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            ListItem(
                modifier = Modifier.clickable {
                    onSelectNone()
                    onDismiss()
                },
                leadingContent = { Icon(Icons.Default.Close, contentDescription = null) },
                headlineContent = { Text("No album") },
                trailingContent = {
                    if (selectedAlbumId == null && !createNewAlbum) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
            )
            ListItem(
                modifier = Modifier.clickable {
                    onSelectCreate()
                    onDismiss()
                },
                leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                headlineContent = { Text("Create album \"$newAlbumName\"") },
                supportingContent = { Text("Shared with the whole family") },
                trailingContent = {
                    if (createNewAlbum) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                },
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (albums.size > 10) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search albums") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            when {
                isLoading && albums.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }

                error != null && albums.isEmpty() -> Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }

                else -> LazyColumn(Modifier.fillMaxWidth()) {
                    items(filtered, key = { it.id }) { album ->
                        ListItem(
                            modifier = Modifier.clickable {
                                onSelectAlbum(album)
                                onDismiss()
                            },
                            leadingContent = { AlbumCoverThumb(album.thumbnailUrl, album.name, sizeDp = 44) },
                            headlineContent = {
                                Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(if (album.assetCount == 1) "1 photo" else "${album.assetCount} photos")
                            },
                            trailingContent = {
                                if (!createNewAlbum && selectedAlbumId == album.id) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
