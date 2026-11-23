package com.github.ljarka.picfort.ui.shopping

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.Receipt
import com.github.ljarka.picfort.core.model.ShoppingItem
import com.github.ljarka.picfort.core.model.ShoppingList
import com.github.ljarka.picfort.core.model.WishlistItem
import com.github.ljarka.picfort.core.ui.R as CoreUiR
import com.github.ljarka.picfort.core.ui.avatar.MemberAvatar
import com.github.ljarka.picfort.core.ui.component.ServerRequiredContent
import com.github.ljarka.picfort.core.ui.component.SettingsAction
import com.github.ljarka.picfort.core.ui.component.aboveFloatingTabBar
import com.github.ljarka.picfort.core.ui.theme.AccentTheme
import com.github.ljarka.picfort.core.ui.theme.BrandAccent
import com.github.ljarka.picfort.ui.shopping.reminders.ReminderBell
import com.github.ljarka.picfort.ui.shopping.reminders.ReminderSheet
import com.github.ljarka.picfort.ui.shopping.reminders.ReminderStatus
import java.time.LocalDateTime

private const val PREVIEW_ITEMS = 6

// Lists (tab root and archived)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListsContent(
    state: ShoppingListsUi,
    archived: Boolean = false,
    onOpen: (ShoppingList) -> Unit = {},
    onCreate: (String) -> Unit = {},
    onArchivedClick: () -> Unit = {},
    onSpendingClick: () -> Unit = {},
    onOpenEvent: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onSetUpServer: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    onAddWishlistClick: () -> Unit = {},
    onEditWishlistItem: (WishlistItem) -> Unit = {},
    onCloseWishlistSheet: () -> Unit = {},
    onPreviewWishlistUrl: (String) -> Unit = {},
    onSaveWishlistItem: (String, String, String?, Long?, Long?, String?, Uri?) -> Unit = { _, _, _, _, _, _, _ -> },
    onToggleWishlistBought: (WishlistItem) -> Unit = {},
    onToggleWishlistReserve: (WishlistItem) -> Unit = {},
    onDeleteWishlistItem: (WishlistItem) -> Unit = {},
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    var creating by rememberSaveable { mutableStateOf(false) }
    if (creating) {
        NewListDialog(onDismiss = { creating = false }, onCreate = {
            creating = false
            onCreate(it)
        })
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (archived) "Archived lists" else "Shopping") },
                navigationIcon = {
                    if (archived) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                actions = {
                    if (!archived && state.configured) {
                        IconButton(onClick = onSpendingClick) {
                            Icon(
                                ImageVector.vectorResource(CoreUiR.drawable.ic_bar_chart),
                                contentDescription = "Spending",
                            )
                        }
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Archived lists") }, onClick = {
                                menu = false
                                onArchivedClick()
                            })
                        }
                    }
                    if (!archived) SettingsAction(onSettingsClick)
                },
            )
        },
        floatingActionButton = {
            if (!archived && state.configured && state.offlineSince == null && state.loadError == null) {
                ExtendedFloatingActionButton(
                    onClick = { creating = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("List") },
                    modifier = Modifier.aboveFloatingTabBar(),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.configured -> ServerRequiredContent(
                    "Connect your Picfort server to use shopping lists.",
                    onSetUpServer,
                )

                state.isLoading && state.lists.isEmpty() -> Centered { CircularProgressIndicator() }

                state.loadError != null && state.lists.isEmpty() -> Centered {
                    Text(state.loadError, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRefresh) { Text("Retry") }
                }

                else -> {
                    state.offlineSince?.let { OfflineBanner(it) }
                    PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
                        val showWishlist = !archived && (state.wishlist.isNotEmpty() || state.offlineSince == null)
                        if (state.lists.isEmpty() && !showWishlist) {
                            Centered {
                                Text(
                                    if (archived) "No archived lists" else "No shopping lists yet",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            val eventsById = remember(state.upcomingEvents) {
                                state.upcomingEvents.associateBy { it.id }
                            }
                            val membersById = remember(state.members) {
                                state.members.associateBy { it.id }
                            }
                            LazyVerticalStaggeredGrid(
                                columns = StaggeredGridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 88.dp),
                                verticalItemSpacing = 8.dp,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (showWishlist) {
                                    item(key = "wishlist", span = StaggeredGridItemSpan.FullLine) {
                                        WishlistCarouselSection(
                                            items = state.wishlist,
                                            eventsById = eventsById,
                                            membersById = membersById,
                                            myId = state.myId,
                                            editable = state.offlineSince == null,
                                            onAddClick = onAddWishlistClick,
                                            onOpenUrl = { url -> openExternalUrl(context, url) },
                                            onEventClick = onOpenEvent,
                                            onToggleBought = onToggleWishlistBought,
                                            onToggleReserve = onToggleWishlistReserve,
                                            onEdit = onEditWishlistItem,
                                            onDelete = onDeleteWishlistItem,
                                            modifier = Modifier.padding(bottom = 8.dp),
                                        )
                                    }
                                    if (state.lists.isNotEmpty()) {
                                        item(key = "lists-header", span = StaggeredGridItemSpan.FullLine) {
                                            Text(
                                                text = "Shopping lists",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                                if (state.lists.isEmpty()) {
                                    item(key = "empty-lists", span = StaggeredGridItemSpan.FullLine) {
                                        Text(
                                            if (archived) "No archived lists" else "No shopping lists yet",
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp),
                                        )
                                    }
                                } else {
                                    items(state.lists, key = { it.id }) { list -> ListCard(list) { onOpen(list) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (state.wishlistSheetOpen) {
        WishlistItemSheet(
            editing = state.editingWishlistItem,
            upcomingEvents = state.upcomingEvents,
            members = state.members,
            isPreviewLoading = state.wishlistPreviewLoading,
            preview = state.wishlistPreview,
            saving = state.savingWishlist,
            onPreviewUrl = onPreviewWishlistUrl,
            onSave = onSaveWishlistItem,
            onDismiss = onCloseWishlistSheet,
        )
    }
}

@Composable
private fun ListCard(list: ShoppingList, onClick: () -> Unit) = AccentTheme(BrandAccent.forKey(list.id)) {
    // Each list wears one aperture blade, like notes in Keep. With wallpaper colors the
    // accent is skipped and primaryContainer is the phone's.
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                list.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            val open = list.unchecked
            if (list.items.isNotEmpty() && open.isEmpty()) {
                Text("All done", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            open.take(PREVIEW_ITEMS).forEach { item ->
                Text(
                    "☐  ${item.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (open.size > PREVIEW_ITEMS) {
                Text("+${open.size - PREVIEW_ITEMS} more", style = MaterialTheme.typography.bodySmall)
            }
            if (list.items.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("${list.checkedCount}/${list.items.size} done", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun NewListDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New list") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(100) },
                singleLine = true,
                label = { Text("Title") },
                placeholder = { Text("Biedronka") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (title.isNotBlank()) onCreate(title.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onCreate(title.trim()) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// One list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListContent(
    state: ShoppingListUi,
    onBack: () -> Unit = {},
    onToggle: (ShoppingItem) -> Unit = {},
    onEdit: (ShoppingItem, String) -> Unit = { _, _ -> },
    onAdd: (String) -> Unit = {},
    onDelete: (ShoppingItem) -> Unit = {},
    onUndo: () -> Unit = {},
    /** Whether the current message can be undone (a deleted item). */
    canUndo: () -> Boolean = { false },
    onRename: (String) -> Unit = {},
    onUncheckAll: () -> Unit = {},
    onDeleteChecked: () -> Unit = {},
    onArchive: (Boolean) -> Unit = {},
    onDeleteList: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    onTakeReceiptPhoto: () -> Unit = {},
    onChooseReceiptPhoto: () -> Unit = {},
    onRetryReceiptUpload: () -> Unit = {},
    onOpenReceipt: (Receipt) -> Unit = {},
    onCloseReceipt: () -> Unit = {},
    onSaveReceipt: (Receipt, String?, String, Long, String, Long?) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteReceipt: (Receipt) -> Unit = {},
    onReadReceiptAgain: (Receipt) -> Unit = {},
    /** Focus "Add item" when the screen opens (a list that was just created). */
    focusAdd: Boolean = false,
    /** Start with the checked section open (screenshots). */
    checkedOpen: Boolean = false,
    onSetReminder: (LocalDateTime) -> Unit = {},
    onRemoveReminder: () -> Unit = {},
    /** The clock the reminder presets count from; fixed in screenshots. */
    now: () -> LocalDateTime = LocalDateTime::now,
) {
    ResizeForKeyboard()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message, state.canRetryReceiptUpload) {
        state.message?.let {
            val action = when {
                state.canRetryReceiptUpload -> "Retry"
                canUndo() -> "Undo"
                else -> null
            }
            val result = snackbar.showSnackbar(it, actionLabel = action)
            if (result == SnackbarResult.ActionPerformed) {
                if (state.canRetryReceiptUpload) onRetryReceiptUpload() else onUndo()
            }
            onMessageShown()
        }
    }
    val list = state.list
    var dialog by remember { mutableStateOf<ListDialog?>(null) }
    var remindering by remember { mutableStateOf(false) }
    if (remindering && list != null) {
        ReminderSheet(
            current = state.reminderAt,
            now = remember { now() },
            onPick = {
                remindering = false
                onSetReminder(it)
            },
            onRemove = {
                remindering = false
                onRemoveReminder()
            },
            onDismiss = { remindering = false },
        )
    }
    if (list != null) {
        when (dialog) {
            ListDialog.Rename -> RenameDialog(list.title, onDismiss = { dialog = null }) {
                dialog = null
                onRename(it)
            }

            ListDialog.DeleteChecked -> Confirm(
                "Delete checked items?",
                "${list.checkedCount} checked items are removed from the list.",
                "Delete",
                onDismiss = { dialog = null },
            ) {
                dialog = null
                onDeleteChecked()
            }

            ListDialog.DeleteList -> Confirm(
                "Delete \"${list.title}\"?",
                "The list is deleted for the whole family.",
                "Delete",
                onDismiss = { dialog = null },
            ) {
                dialog = null
                onDeleteList()
            }

            null -> Unit
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(list?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    // A reminder is this phone's, so it can be set from the offline copy too.
                    if (list != null && !list.archived) {
                        ReminderBell(active = state.reminderAt != null) { remindering = true }
                    }
                    if (list != null && state.editable) {
                        var receiptMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { receiptMenu = true }) {
                                Icon(
                                    ImageVector.vectorResource(CoreUiR.drawable.ic_photo_camera),
                                    contentDescription = "Add receipt",
                                )
                            }
                            DropdownMenu(expanded = receiptMenu, onDismissRequest = { receiptMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Take photo") },
                                    onClick = {
                                        receiptMenu = false
                                        onTakeReceiptPhoto()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Choose photo") },
                                    onClick = {
                                        receiptMenu = false
                                        onChooseReceiptPhoto()
                                    },
                                )
                            }
                        }
                        var menu by remember { mutableStateOf(false) }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = {
                                menu = false
                                dialog =
                                    ListDialog.Rename
                            })
                            if (list.checkedCount > 0) {
                                DropdownMenuItem(text = { Text("Uncheck all") }, onClick = {
                                    menu = false
                                    onUncheckAll()
                                })
                                DropdownMenuItem(text = { Text("Delete checked items") }, onClick = {
                                    menu = false
                                    dialog =
                                        ListDialog.DeleteChecked
                                })
                            }
                            DropdownMenuItem(
                                text = { Text(if (list.archived) "Unarchive" else "Archive") },
                                onClick = {
                                    menu = false
                                    onArchive(!list.archived)
                                },
                            )
                            if (state.isMine) {
                                DropdownMenuItem(
                                    text = { Text("Delete list", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        menu = false
                                        dialog = ListDialog.DeleteList
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            when {
                list == null && state.loadError != null -> Centered {
                    Text(state.loadError, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                }

                list == null -> Centered { CircularProgressIndicator() }

                else -> {
                    state.offlineSince?.let { OfflineBanner(it) }
                    state.reminderAt?.let { at ->
                        ReminderStatus(at, today = remember { now() }.toLocalDate()) { remindering = true }
                    }
                    PullToRefreshBox(isRefreshing = false, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
                        ItemList(
                            state = state,
                            list = list,
                            onToggle = onToggle,
                            onEdit = onEdit,
                            onAdd = onAdd,
                            onDelete = onDelete,
                            onOpenReceipt = onOpenReceipt,
                            focusAdd = focusAdd,
                            checkedOpen = checkedOpen,
                        )
                    }
                }
            }
        }
    }
    state.openReceipt?.let { receipt ->
        ReceiptSheet(
            receipt = receipt,
            members = state.members,
            activeLists = state.activeLists,
            myId = state.myId,
            saving = state.savingReceipt,
            aiSupported = state.aiSupported,
            reading = state.readingReceipt,
            readHint = state.receiptReadHint,
            onDismiss = onCloseReceipt,
            onSave = { shop, date, totalMinor, payerId, targetListId ->
                onSaveReceipt(receipt, shop, date, totalMinor, payerId, targetListId)
            },
            onDelete = { onDeleteReceipt(receipt) },
            onReadAgain = { onReadReceiptAgain(receipt) },
        )
    }
}

private enum class ListDialog { Rename, DeleteChecked, DeleteList }

/**
 * The window pans by default, which pushed the top bar off screen while typing items. While
 * this screen is shown the window resizes instead, and the list makes room with imePadding.
 */
@Composable
private fun ResizeForKeyboard() {
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        val before = window.attributes.softInputMode
        @Suppress("DEPRECATION")
        window.setSoftInputMode(
            (before and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE) or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
        )
        onDispose { window.setSoftInputMode(before) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun ItemList(
    state: ShoppingListUi,
    list: ShoppingList,
    onToggle: (ShoppingItem) -> Unit,
    onEdit: (ShoppingItem, String) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    onOpenReceipt: (Receipt) -> Unit,
    focusAdd: Boolean,
    checkedOpen: Boolean,
) {
    var showChecked by rememberSaveable { mutableStateOf(checkedOpen) }
    val checked = list.checked
    // One LazyColumn: unchecked rows, "Add item", the checked header and checked rows.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(list.unchecked, key = { it.id }) { item ->
            ItemRow(
                item,
                state.editable,
                checker = null,
                onToggle = { onToggle(item) },
                onEdit = { onEdit(item, it) },
                onDelete = { onDelete(item) },
            )
        }
        if (state.editable) {
            item(key = "add") { AddItemRow(onAdd, focusAdd) }
        }
        if (checked.isNotEmpty()) {
            item(key = "checked-header") {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { showChecked = !showChecked }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (showChecked) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (checked.size == 1) "1 checked item" else "${checked.size} checked items",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showChecked) {
                items(checked, key = { it.id }) { item ->
                    ItemRow(
                        item,
                        state.editable,
                        checker = state.memberOf(item.checkedBy),
                        onToggle = {
                            onToggle(item)
                        },
                        onEdit = { onEdit(item, it) },
                        onDelete = { onDelete(item) },
                    )
                }
            }
        }
        if (state.uploadingReceipt) {
            item(key = "uploading-receipt") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Uploading receipt…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        if (state.receipts.isNotEmpty()) {
            item(key = "receipts-header") {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(
                    text = "Receipts · ${formatTotalMinor(state.receiptsTotalMinor, state.receiptsCurrency)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(state.receipts, key = { "receipt-${it.id}" }) { receipt ->
                ReceiptRow(receipt = receipt, onClick = { onOpenReceipt(receipt) })
            }
        }
    }
}

@Composable
private fun ReceiptRow(receipt: Receipt, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = receipt.shop ?: "Receipt",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatReceiptDate(receipt.date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!receipt.isConfirmed) {
            Text(
                text = "⚠",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { contentDescription = "not confirmed" },
            )
        }
        Text(
            text = receipt.totalMinor?.let { formatTotalMinor(it, receipt.currency) } ?: "No total",
            style = MaterialTheme.typography.bodyMedium,
            color = if (receipt.totalMinor != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun ItemRow(
    item: ShoppingItem,
    editable: Boolean,
    checker: FamilyMember?,
    onToggle: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var text by remember(item.id) { mutableStateOf(item.text) }
    var focused by remember { mutableStateOf(false) }
    // A poll never replaces what is being typed; otherwise the row follows the server.
    LaunchedEffect(item.text) { if (!focused) text = item.text }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The checkbox carries the item's name, so TalkBack reads "<text>, checked"; the text
        // field next to it is only for editing.
        Checkbox(
            checked = item.checked,
            onCheckedChange = { onToggle() },
            enabled = editable,
            modifier = Modifier.semantics {
                contentDescription = item.text
                stateDescription = if (item.checked) "checked" else "not checked"
            },
        )
        val colors = MaterialTheme.colorScheme
        val color = if (item.checked) colors.onSurfaceVariant else colors.onSurface
        TextField(
            value = text,
            onValueChange = { text = it.take(200) },
            enabled = editable && !item.checked,
            singleLine = false,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = color,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                disabledTextColor = color,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (text.trim() != item.text) onEdit(text.trim()) }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged {
                    // Saved when the field loses focus; an emptied text deletes the item.
                    if (focused && !it.isFocused && text.trim() != item.text) onEdit(text.trim())
                    focused = it.isFocused
                },
        )
        if (checker != null) MemberAvatar(checker, size = 24.dp, ring = 1.dp)
        if (editable) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.semantics {
                    contentDescription = "Delete ${item.text}"
                },
            ) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddItemRow(onAdd: (String) -> Unit, focusOnStart: Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focusOnStart) { if (focusOnStart) focus.requestFocus() }
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // Same width as a row's checkbox, so the text lines up with the items.
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextField(
            value = text,
            onValueChange = { text = it.take(200) },
            singleLine = true,
            placeholder = { Text("Add item") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            // Adds and keeps the keyboard for the next item; on an empty field it closes it.
            keyboardActions = KeyboardActions(onNext = {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                } else {
                    defaultKeyboardAction(ImeAction.Done)
                }
            }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
    }
}

// Shared pieces

@Composable
private fun OfflineBanner(since: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Offline. List from $since, read only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var title by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename list") },
        text = {
            OutlinedTextField(
                title,
                { title = it.take(100) },
                singleLine = true,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { onRename(title.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Confirm(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
