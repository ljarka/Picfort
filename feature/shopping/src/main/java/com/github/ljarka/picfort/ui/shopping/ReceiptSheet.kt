package com.github.ljarka.picfort.ui.shopping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.Receipt
import com.github.ljarka.picfort.core.model.ShoppingList
import com.github.ljarka.picfort.core.ui.avatar.MemberAvatar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptSheet(
    receipt: Receipt,
    members: List<FamilyMember>,
    activeLists: List<ShoppingList>,
    myId: String,
    saving: Boolean,
    aiSupported: Boolean = false,
    reading: Boolean = false,
    readHint: String? = null,
    onDismiss: () -> Unit,
    onSave: (shop: String?, date: String, totalMinor: Long, payerId: String, listId: Long?) -> Unit,
    onDelete: () -> Unit,
    onReadAgain: () -> Unit = {},
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ReceiptSheetContent(
            receipt = receipt,
            members = members,
            activeLists = activeLists,
            myId = myId,
            saving = saving,
            aiSupported = aiSupported,
            reading = reading,
            readHint = readHint,
            onSave = onSave,
            onDelete = onDelete,
            onReadAgain = onReadAgain,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptSheetContent(
    receipt: Receipt,
    members: List<FamilyMember>,
    activeLists: List<ShoppingList>,
    myId: String,
    saving: Boolean = false,
    aiSupported: Boolean = false,
    reading: Boolean = false,
    readHint: String? = null,
    onSave: (
        shop: String?,
        date: String,
        totalMinor: Long,
        payerId: String,
        listId: Long?,
    ) -> Unit = { _, _, _, _, _ -> },
    onDelete: () -> Unit = {},
    onReadAgain: () -> Unit = {},
) {
    var shop by rememberSaveable(receipt.id) { mutableStateOf(receipt.shop.orEmpty()) }
    var date by rememberSaveable(receipt.id) { mutableStateOf(receipt.date) }
    var totalText by rememberSaveable(receipt.id) {
        mutableStateOf(receipt.totalMinor?.let { formatMinorForInput(it) }.orEmpty())
    }
    var touchedShop by rememberSaveable(receipt.id) { mutableStateOf(false) }
    var touchedDate by rememberSaveable(receipt.id) { mutableStateOf(false) }
    var touchedTotal by rememberSaveable(receipt.id) { mutableStateOf(false) }

    LaunchedEffect(receipt.shop) {
        if (!touchedShop && !receipt.shop.isNullOrBlank()) shop = receipt.shop.orEmpty()
    }
    LaunchedEffect(receipt.date) {
        if (!touchedDate) date = receipt.date
    }
    LaunchedEffect(receipt.totalMinor) {
        if (!touchedTotal && receipt.totalMinor != null) totalText = formatMinorForInput(receipt.totalMinor!!)
    }
    var payerId by rememberSaveable(receipt.id) {
        mutableStateOf(receipt.payerId.ifEmpty { myId })
    }
    var listId by rememberSaveable(receipt.id) { mutableStateOf(receipt.listId) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showFullPhoto by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val parsedTotal = parseTotalMinor(totalText)
    val totalError = totalText.isNotBlank() && parsedTotal == null
    val currencySymbol = remember(receipt.currency) {
        runCatching { Currency.getInstance(receipt.currency).getSymbol(Locale.getDefault()) }
            .getOrElse { receipt.currency }
    }

    if (showDatePicker) {
        val initialMillis = runCatching {
            LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        touchedDate = true
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showFullPhoto && receipt.photoUrl != null) {
        ReceiptPhotoViewer(url = receipt.photoUrl!!, onDismiss = { showFullPhoto = false })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete receipt?") },
            text = { Text("The receipt and its photo will be deleted for the whole family.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = receipt.photoUrl != null) { showFullPhoto = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (receipt.photoUrl != null) {
                        AsyncImage(
                            model = receipt.photoUrl,
                            contentDescription = "Receipt photo, tap to zoom",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("No photo", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Receipt", style = MaterialTheme.typography.titleLarge)
                    when {
                        reading -> {
                            Text(
                                "Reading receipt on device…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }

                        readHint != null -> Text(
                            readHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        !receipt.isConfirmed -> Text(
                            "Type the total and save to confirm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = shop,
                onValueChange = {
                    touchedShop = true
                    shop = it.take(100)
                },
                label = { Text("Shop") },
                placeholder = { Text("Biedronka") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = formatReceiptDate(date),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker = true }
                        .semantics { contentDescription = "Pick receipt date" },
                )
            }

            OutlinedTextField(
                value = totalText,
                onValueChange = {
                    touchedTotal = true
                    totalText = it
                },
                label = { Text("Total") },
                placeholder = { Text("0.00") },
                suffix = { Text(currencySymbol) },
                singleLine = true,
                isError = totalError,
                supportingText = if (totalError) {
                    { Text("Enter a positive amount with up to two decimals") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            PayerDropdown(
                members = members,
                selectedId = payerId,
                onSelect = { payerId = it },
            )

            ListDropdown(
                lists = activeLists,
                selectedListId = listId,
                onSelect = { listId = it },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (aiSupported && receipt.photoUrl != null) {
                    TextButton(
                        enabled = !reading && !saving,
                        onClick = onReadAgain,
                    ) {
                        Text("Read again")
                    }
                }
                if (receipt.createdBy == myId) {
                    TextButton(
                        enabled = !saving,
                        onClick = { confirmDelete = true },
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = parsedTotal != null && !saving,
                    onClick = {
                        parsedTotal?.let { total ->
                            onSave(shop.trim().ifEmpty { null }, date, total, payerId, listId)
                        }
                    },
                ) {
                    Text(if (saving) "Saving…" else "Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayerDropdown(members: List<FamilyMember>, selectedId: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = members.firstOrNull { it.id == selectedId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.name ?: selectedId,
            onValueChange = {},
            readOnly = true,
            label = { Text("Paid by") },
            leadingIcon = selected?.let { { MemberAvatar(it, size = 24.dp) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            members.forEach { member ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(member, size = 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(member.name)
                        }
                    },
                    onClick = {
                        onSelect(member.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListDropdown(lists: List<ShoppingList>, selectedListId: Long?, onSelect: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTitle = lists.firstOrNull { it.id == selectedListId }?.title ?: "No list"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedTitle,
            onValueChange = {},
            readOnly = true,
            label = { Text("List") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("No list") },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.title) },
                    onClick = {
                        onSelect(list.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ReceiptPhotoViewer(url: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (nextScale <= 1f) Offset.Zero else offset + pan
                            scale = nextScale
                        }
                    },
            ) {
                AsyncImage(
                    model = url,
                    contentDescription = "Receipt photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close photo", tint = Color.White)
                }
            }
        }
    }
}

fun formatReceiptDate(isoDate: String, locale: Locale = Locale.getDefault()): String = runCatching {
    LocalDate.parse(isoDate).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
}.getOrElse { isoDate }
