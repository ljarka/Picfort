package com.github.ljarka.picfort.ui.shopping

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.WishlistItem
import com.github.ljarka.picfort.core.model.WishlistPreview

@Composable
fun WishlistCarouselSection(
    items: List<WishlistItem>,
    eventsById: Map<Long, FamilyEvent>,
    membersById: Map<String, FamilyMember>,
    myId: String,
    editable: Boolean,
    onAddClick: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onEventClick: (Long) -> Unit,
    onToggleBought: (WishlistItem) -> Unit,
    onToggleReserve: (WishlistItem) -> Unit,
    onEdit: (WishlistItem) -> Unit,
    onDelete: (WishlistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Wishlist",
                style = MaterialTheme.typography.titleMedium,
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = "${items.count { !it.bought }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (editable) {
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add wishlist link",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        if (items.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    WishlistProductCard(
                        item = item,
                        event = item.eventId?.let(eventsById::get),
                        reservedByName = item.reservedBy?.let { membersById[it]?.name },
                        hideSecretReservation = item.targetMemberId == myId,
                        editable = editable,
                        canDelete = item.createdBy == myId || item.bought,
                        onOpenUrl = { onOpenUrl(item.url) },
                        onEventClick = { item.eventId?.let(onEventClick) },
                        onToggleBought = { onToggleBought(item) },
                        onToggleReserve = { onToggleReserve(item) },
                        onEdit = { onEdit(item) },
                        onDelete = { onDelete(item) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WishlistProductCard(
    item: WishlistItem,
    event: FamilyEvent?,
    reservedByName: String?,
    hideSecretReservation: Boolean,
    editable: Boolean,
    canDelete: Boolean,
    onOpenUrl: () -> Unit,
    onEventClick: () -> Unit,
    onToggleBought: () -> Unit,
    onToggleReserve: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .width(172.dp)
            .alpha(if (item.bought) 0.6f else 1f)
            .combinedClickable(
                onClick = onOpenUrl,
                onLongClick = { if (editable) menu = true },
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (!item.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = item.title.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (editable) {
                    Box(Modifier.align(Alignment.TopEnd)) {
                        IconButton(
                            onClick = { menu = true },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Wishlist item actions",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (item.bought) "Mark as not bought" else "Mark as bought") },
                                onClick = {
                                    menu = false
                                    onToggleBought()
                                },
                            )
                            if (!hideSecretReservation && !item.bought) {
                                DropdownMenuItem(
                                    text = {
                                        Text(if (item.reservedBy == null) "Reserve gift" else "Release reservation")
                                    },
                                    onClick = {
                                        menu = false
                                        onToggleReserve()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = {
                                    menu = false
                                    onEdit()
                                },
                            )
                            if (canDelete) {
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menu = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.bought) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (item.priceMinor != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatTotalMinor(item.priceMinor!!, item.currency),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (event != null) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.clickable(onClick = onEventClick),
                ) {
                    val icon = if (event.birthdayOf != null) "🎂 " else "🎁 "
                    Text(
                        text = "$icon${event.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (!hideSecretReservation && reservedByName != null && !item.bought) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Reserved by $reservedByName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistItemSheet(
    editing: WishlistItem?,
    upcomingEvents: List<FamilyEvent>,
    members: List<FamilyMember>,
    isPreviewLoading: Boolean,
    preview: WishlistPreview?,
    saving: Boolean,
    onPreviewUrl: (String) -> Unit,
    onSave: (
        url: String,
        title: String,
        imageUrl: String?,
        priceMinor: Long?,
        eventId: Long?,
        targetMemberId: String?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var url by remember(editing?.id) { mutableStateOf(editing?.url.orEmpty()) }
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
    var imageUrl by remember(editing?.id) { mutableStateOf(editing?.imageUrl) }
    var priceInput by remember(editing?.id) {
        mutableStateOf(editing?.priceMinor?.let { formatMinorForInput(it) }.orEmpty())
    }
    var eventId by remember(editing?.id) { mutableStateOf(editing?.eventId) }
    var targetMemberId by remember(editing?.id) { mutableStateOf(editing?.targetMemberId) }
    var lastPreviewedUrl by remember(editing?.id) { mutableStateOf("") }
    var showWebViewSheet by remember(editing?.id) { mutableStateOf(false) }
    var wasPreviewLoading by remember(editing?.id) { mutableStateOf(false) }

    LaunchedEffect(preview) {
        if (editing == null && preview != null) {
            if (title.isBlank() && !preview.title.isNullOrBlank()) {
                title = preview.title.orEmpty()
            }
            if (imageUrl.isNullOrBlank() && !preview.imageUrl.isNullOrBlank()) {
                imageUrl = preview.imageUrl
            }
            if (priceInput.isBlank() && preview.priceMinor != null) {
                priceInput = formatMinorForInput(preview.priceMinor!!)
            }
        }
    }

    LaunchedEffect(isPreviewLoading) {
        if (wasPreviewLoading && !isPreviewLoading && editing == null && imageUrl.isNullOrBlank() &&
            isHttpUrl(url.trim())
        ) {
            showWebViewSheet = true
        }
        wasPreviewLoading = isPreviewLoading
    }

    LaunchedEffect(url) {
        val trimmed = url.trim()
        if (editing == null && isHttpUrl(trimmed) && trimmed != lastPreviewedUrl) {
            lastPreviewedUrl = trimmed
            onPreviewUrl(trimmed)
        }
    }

    val birthdayEvents = remember(upcomingEvents) {
        upcomingEvents
            .filter { it.isBirthday }
            .sortedBy { it.startDate }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (editing == null) "Add to wishlist" else "Edit wishlist item",
                style = MaterialTheme.typography.titleLarge,
            )
            if (editing == null) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Product URL") },
                    singleLine = true,
                    trailingIcon = {
                        TextButton(onClick = {
                            clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { url = it }
                        }) {
                            Text("Paste")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (isPreviewLoading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
            OutlinedTextField(
                value = imageUrl.orEmpty(),
                onValueChange = { imageUrl = it },
                label = { Text("Image URL (optional)") },
                singleLine = true,
                trailingIcon = {
                    TextButton(onClick = {
                        clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let { imageUrl = it }
                    }) {
                        Text("Paste")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (imageUrl.isNullOrBlank() && isHttpUrl(url.trim()) && !isPreviewLoading) {
                TextButton(onClick = { showWebViewSheet = true }) {
                    Text("Load photo in browser")
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = priceInput,
                onValueChange = { priceInput = it },
                label = { Text("Price (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            EventPickerDropdown(
                events = birthdayEvents,
                selectedEventId = eventId,
                onSelect = { eventId = it },
            )
            MemberPickerDropdown(
                members = members,
                selectedMemberId = targetMemberId,
                onSelect = { targetMemberId = it },
            )
            val parsedPrice = if (priceInput.isBlank()) null else parseTotalMinor(priceInput)
            val priceValid = priceInput.isBlank() || parsedPrice != null
            val urlValid = editing != null || isHttpUrl(url.trim())
            val imageUrlValid = imageUrl.isNullOrBlank() || isHttpUrl(imageUrl!!.trim())
            val titleValid = title.trim().isNotEmpty() || editing == null
            Button(
                onClick = {
                    onSave(
                        url.trim(),
                        title.trim(),
                        imageUrl?.trim()?.takeIf { it.isNotEmpty() },
                        parsedPrice,
                        eventId,
                        targetMemberId,
                    )
                },
                enabled = !saving && urlValid && imageUrlValid && priceValid && titleValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showWebViewSheet) {
        WishlistWebViewSheet(
            url = url.trim(),
            onExtracted = { extracted ->
                showWebViewSheet = false
                if (!extracted.imageUrl.isNullOrBlank()) {
                    imageUrl = extracted.imageUrl
                }
                if (title.isBlank() && !extracted.title.isNullOrBlank()) {
                    title = extracted.title.orEmpty()
                }
                if (priceInput.isBlank() && extracted.priceMinor != null) {
                    priceInput = formatMinorForInput(extracted.priceMinor!!)
                }
            },
            onDismiss = { showWebViewSheet = false },
        )
    }
}

@Composable
private fun EventPickerDropdown(events: List<FamilyEvent>, selectedEventId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = events.firstOrNull { it.id == selectedEventId }
    Box {
        OutlinedTextField(
            value = selected?.let { "${it.title} (${it.startDate})" } ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text("Link to event") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("None") },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            events.forEach { ev ->
                val prefix = if (ev.birthdayOf != null) "🎂 " else ""
                DropdownMenuItem(
                    text = { Text("$prefix${ev.title} (${ev.startDate})") },
                    onClick = {
                        expanded = false
                        onSelect(ev.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun MemberPickerDropdown(members: List<FamilyMember>, selectedMemberId: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = members.firstOrNull { it.id == selectedMemberId }
    Box {
        OutlinedTextField(
            value = selected?.name ?: "Anyone",
            onValueChange = {},
            readOnly = true,
            label = { Text("For who") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Anyone") },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            members.forEach { member ->
                DropdownMenuItem(
                    text = { Text(member.name) },
                    onClick = {
                        expanded = false
                        onSelect(member.id)
                    },
                )
            }
        }
    }
}

internal fun openExternalUrl(context: android.content.Context, rawUrl: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl)))
    }
}

internal fun isHttpUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
