package com.github.ljarka.picfort.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Pick an album, or name a new one. */
@Composable
fun AddToAlbumDialog(
    onDismiss: () -> Unit,
    onAlbumChosen: (AlbumDto) -> Unit,
    onNewAlbum: (name: String) -> Unit,
) {
    val viewModel: AlbumsViewModel = hiltViewModel()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to album") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("New album") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.heightIn(min = 80.dp, max = 320.dp).padding(top = 8.dp)) {
                    when (val s = albums) {
                        LoadState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        is LoadState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                        is LoadState.Loaded -> LazyColumn {
                            items(s.value, key = { it.id }) { album ->
                                ListItem(
                                    modifier = Modifier.clickable { onAlbumChosen(album) },
                                    headlineContent = { Text(album.albumName.ifBlank { "Untitled" }) },
                                    supportingContent = { Text("${album.assetCount} items") },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = newName.isNotBlank(), onClick = { onNewAlbum(newName) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
