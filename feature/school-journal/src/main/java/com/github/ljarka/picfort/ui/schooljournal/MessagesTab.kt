package com.github.ljarka.picfort.ui.schooljournal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Start loading the next page this many rows before the end. */
private const val LOAD_MORE_AHEAD = 5

/** The Messages tab (spec §4.3): "Unread only" chip, rows newest first, infinite scroll. */
@Composable
fun MessagesTab(state: SchoolJournalUi, clock: JournalClock, actions: SchoolJournalActions) {
    val messages = state.messages
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - LOAD_MORE_AHEAD
        }
    }
    LaunchedEffect(nearEnd, messages.nextCursor) {
        if (nearEnd && messages.nextCursor != null) actions.onLoadMoreMessages()
    }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
        item(key = "filters") {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FilterChip(
                    selected = messages.unreadOnly,
                    onClick = actions.onToggleUnreadOnly,
                    label = { Text("Unread only") },
                )
            }
        }
        when {
            messages.loading && messages.items.isEmpty() -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            messages.loaded && messages.items.isEmpty() -> item(key = "empty") {
                Text(
                    if (messages.unreadOnly) "No unread messages" else "No messages",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }

            else -> items(messages.items, key = { it.id }) { message ->
                MessageRow(
                    message = message,
                    read = state.isRead(message),
                    childName = state.childName(message.childId),
                    clock = clock,
                    onOpen = { actions.onOpenMessage(message.id) },
                    onMarkRead = { actions.onMarkRead(message) },
                    onMarkUnread = { actions.onMarkUnread(message) },
                )
                HorizontalDivider(Modifier.padding(start = 40.dp))
            }
        }
        if (messages.loadingMore) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
        // Room above the floating tab bar's gesture area.
        item(key = "bottom") { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

/** Unread: bold with a dot. Removed from the e-journal: struck through. Long-press: read / unread. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: MessageSummary,
    read: Boolean,
    childName: String?,
    clock: JournalClock,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val removed = message.deletedAt != null
    val weight = if (read) FontWeight.Normal else FontWeight.Bold
    val decoration = if (removed) TextDecoration.LineThrough else null
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true }, onLongClickLabel = "Read or unread")
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.padding(top = 6.dp).size(10.dp)) {
                if (!read) Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        message.sender,
                        fontWeight = weight,
                        textDecoration = decoration,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    instantOrNull(message.sentAt)?.let {
                        Text(
                            shortWhen(it, clock.now, clock.zone, clock.locale, clock.is24Hour),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    message.subject,
                    fontWeight = weight,
                    textDecoration = decoration,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    childName,
                    "Attachment".takeIf { message.hasAttachments },
                    "Removed from the e-journal".takeIf { removed },
                )
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (read) "Mark as unread" else "Mark as read") },
                onClick = {
                    menu = false
                    if (read) onMarkUnread() else onMarkRead()
                },
            )
        }
    }
}

/** The Grades tab until T-079. */
@Composable
fun GradesPlaceholder() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Grades will show here.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
