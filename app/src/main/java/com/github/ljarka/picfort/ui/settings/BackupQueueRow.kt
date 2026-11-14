package com.github.ljarka.picfort.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.backup.BackupQueueState
import com.github.ljarka.picfort.backup.QueuedMedia
import com.github.ljarka.picfort.core.model.localImageUri
import com.github.ljarka.picfort.core.model.localVideoUri
import com.github.ljarka.picfort.local.hasLocalThumbnails
import com.github.ljarka.picfort.local.localThumbnailUrl

private val TILE = 72.dp

/**
 * The running backup's queue under "Back up now" (T-063): the file being uploaded first, with its
 * progress, then the ones waiting. Files leave once uploaded or found in Immich. Nothing when idle.
 */
@Composable
fun BackupQueueRow(queue: BackupQueueState, modifier: Modifier = Modifier) {
    val items = queue.ordered
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Text(
            if (queue.uploadingId != null) "Uploading · ${items.size} in the queue" else "In the queue · ${items.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(items, key = { it.id }) { media ->
                QueueTile(
                    media = media,
                    progress = if (media.id == queue.uploadingId) queue.uploadProgress else null,
                    // Only slide: fading tiles overlap the next ones when small files go fast.
                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                )
            }
        }
    }
}

@Composable
private fun QueueTile(media: QueuedMedia, progress: Float?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val uploading = progress != null
    Box(
        modifier
            .size(TILE)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (uploading) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .semantics {
                contentDescription =
                    (if (media.isVideo) "Video" else "Photo") + if (uploading) ", uploading" else ", waiting"
            },
    ) {
        AsyncImage(
            model = when {
                hasLocalThumbnails -> localThumbnailUrl(media.id, media.isVideo)
                media.isVideo -> localVideoUri(media.id)
                else -> localImageUri(media.id)
            },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (media.isVideo) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).size(18.dp),
            )
        }
        if (progress != null) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            CircularProgressIndicator(
                progress = { progress },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
        }
    }
}
