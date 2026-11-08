package com.github.ljarka.picfort.ui.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.R

@Composable
fun TimelineMenuContent(
    selectedCount: Int,
    isBusy: Boolean,
    canSendToFrame: Boolean,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onSendToFrame: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.height(4.dp).fillMaxWidth()) {
            if (isBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Text(
            text = "$selectedCount selected",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(88.dp)
                .alpha(if (isBusy) 0.5f else 1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val enabled = !isBusy && selectedCount > 0
            IconTextButton(icon = Icons.Default.Share, text = "Share", onClick = { if (enabled) onShare() })
            IconTextButton(
                icon = ImageVector.vectorResource(R.drawable.ic_download),
                text = "Download",
                onClick = { if (enabled) onDownload() },
            )
            IconTextButton(
                icon = Icons.AutoMirrored.Filled.Send,
                text = "Frame",
                onClick = { if (enabled && canSendToFrame) onSendToFrame() },
                modifier = Modifier.alpha(if (canSendToFrame) 1f else 0.38f),
            )
            IconTextButton(icon = Icons.Default.Delete, text = "Delete", onClick = { if (enabled) onDelete() })
        }
    }
}
