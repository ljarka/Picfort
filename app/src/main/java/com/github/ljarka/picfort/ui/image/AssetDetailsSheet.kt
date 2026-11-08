package com.github.ljarka.picfort.ui.image

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.assets.AssetDetails
import java.util.Locale

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Loaded(val details: AssetDetails) : DetailsState
    data class Error(val message: String) : DetailsState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailsSheet(state: DetailsState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        AssetDetailsList(state)
    }
}

@Composable
fun AssetDetailsList(state: DetailsState) {
    val context = LocalContext.current
    // Let the sheet's own colour show through, instead of banded list items.
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    run {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Details",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            when (state) {
                DetailsState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
                is DetailsState.Error -> Text(
                    state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )
                is DetailsState.Loaded -> {
                    state.details.rows.forEach { row ->
                        ListItem(
                            colors = transparent,
                            overlineContent = { Text(row.label) },
                            headlineContent = { Text(row.value) },
                        )
                    }
                    state.details.location?.let { (lat, lon) ->
                        ListItem(
                            colors = transparent,
                            modifier = Modifier.clickable {
                                val geo = String.format(Locale.US, "geo:%f,%f?q=%f,%f", lat, lon, lat, lon)
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(geo)))
                                } catch (e: ActivityNotFoundException) {
                                    // No maps app installed; nothing to open.
                                }
                            },
                            leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            headlineContent = { Text("Open in maps") },
                            supportingContent = { Text(String.format(Locale.US, "%.5f, %.5f", lat, lon)) },
                        )
                    }
                }
            }
        }
    }
}
