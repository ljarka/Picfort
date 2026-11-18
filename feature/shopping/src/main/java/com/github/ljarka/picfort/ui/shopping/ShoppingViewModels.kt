package com.github.ljarka.picfort.ui.shopping

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.core.model.FamilyEventsProvider
import com.github.ljarka.picfort.core.model.FamilyMemberProvider
import com.github.ljarka.picfort.core.model.Receipt
import com.github.ljarka.picfort.core.model.ShoppingItem
import com.github.ljarka.picfort.core.model.ShoppingList
import com.github.ljarka.picfort.core.model.WishlistItem
import com.github.ljarka.picfort.core.model.pollEvery
import com.github.ljarka.picfort.core.network.PicfortServerClient
import com.github.ljarka.picfort.core.network.PicfortServerException
import com.github.ljarka.picfort.core.network.compressImageUri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val LISTS_POLL_MILLIS = 60_000L
private const val LIST_POLL_MILLIS = 10_000L

/** Longest edge of a wishlist photo from the phone (T-083): sharp on a card, small to upload. */
private const val WISHLIST_PHOTO_EDGE = 1600

/** "14:32" in the device's locale, for the offline banner. */
internal fun Long.asTime(zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(this).atZone(zone))

@HiltViewModel
class ShoppingListsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ShoppingRepository,
    private val wishlistRepository: WishlistRepository,
    private val events: FamilyEventsProvider,
    private val family: FamilyMemberProvider,
    private val server: PicfortServerClient,
    savedState: SavedStateHandle,
) : ViewModel() {
    val archived: Boolean = savedState.get<Boolean>("archived") ?: false
    private val extra = MutableStateFlow(ShoppingListsUi(isLoading = true, myId = family.myId))
    private val _state = MutableStateFlow(ShoppingListsUi(isLoading = true, myId = family.myId))
    val state: StateFlow<ShoppingListsUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.lists,
                repository.offlineSince,
                wishlistRepository.items,
                events.upcoming,
                family.allMembers,
                extra,
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val since = values[1] as Long?

                @Suppress("UNCHECKED_CAST")
                val wishlist = values[2] as List<WishlistItem>

                @Suppress("UNCHECKED_CAST")
                val upcomingEvents = values[3] as List<com.github.ljarka.picfort.core.model.FamilyEvent>

                @Suppress("UNCHECKED_CAST")
                val members = values[4] as List<com.github.ljarka.picfort.core.model.FamilyMember>
                val ui = values[5] as ShoppingListsUi
                ui.copy(
                    lists = if (archived) repository.archived() else repository.active(),
                    wishlist = wishlist,
                    upcomingEvents = upcomingEvents,
                    members = members,
                    myId = family.myId,
                    offlineSince = since?.asTime()?.takeIf { !archived },
                )
            }.collect { _state.value = it }
        }
        viewModelScope.launch { repository.messages.collect { msg -> extra.update { it.copy(message = msg) } } }
    }

    suspend fun keepFresh() {
        pollEvery(intervalMillis = LISTS_POLL_MILLIS) {
            refresh()
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        if (!server.isConfigured) {
            extra.update { it.copy(configured = false, isLoading = false) }
            return
        }
        extra.update { it.copy(configured = true) }
        viewModelScope.launch { family.refresh() }
        if (!archived) {
            viewModelScope.launch { events.refreshUpcoming() }
            viewModelScope.launch { wishlistRepository.refresh() }
        }
        val error = repository.refresh(archived)
        val nothing = (if (archived) repository.archived() else repository.active()).isEmpty()
        extra.update {
            it.copy(
                isLoading = false,
                loadError = error?.message?.takeIf { nothing && repository.offlineSince.value == null },
            )
        }
    }

    suspend fun create(title: String): ShoppingList? = try {
        repository.create(title)
    } catch (e: PicfortServerException) {
        extra.update { it.copy(message = e.message) }
        null
    }

    fun messageShown() = extra.update { it.copy(message = null) }

    fun openNewWishlistItem() = extra.update {
        it.copy(
            wishlistSheetOpen = true,
            editingWishlistItem = null,
            wishlistPreview = null,
            wishlistPreviewLoading = false,
        )
    }

    fun editWishlistItem(item: WishlistItem) = extra.update {
        it.copy(
            wishlistSheetOpen = true,
            editingWishlistItem = item,
            wishlistPreview = null,
            wishlistPreviewLoading = false,
        )
    }

    fun closeWishlistSheet() = extra.update {
        it.copy(
            wishlistSheetOpen = false,
            editingWishlistItem = null,
            wishlistPreview = null,
            wishlistPreviewLoading = false,
        )
    }

    fun previewWishlistUrl(url: String) {
        viewModelScope.launch {
            extra.update { it.copy(wishlistPreviewLoading = true) }
            val preview = runCatching { wishlistRepository.preview(url) }.getOrNull()
            extra.update { it.copy(wishlistPreviewLoading = false, wishlistPreview = preview) }
        }
    }

    fun saveWishlistItem(
        url: String,
        title: String,
        imageUrl: String?,
        priceMinor: Long?,
        eventId: Long?,
        targetMemberId: String?,
        /** A photo taken or chosen on the phone (T-083); uploaded after the item is saved. */
        photo: Uri? = null,
    ) {
        viewModelScope.launch {
            extra.update { it.copy(savingWishlist = true) }
            try {
                val editing = extra.value.editingWishlistItem
                val normalizedImageUrl = imageUrl?.trim()?.takeIf { it.isNotEmpty() }
                val saved = if (editing == null) {
                    wishlistRepository.create(
                        url = url,
                        title = title.ifBlank { null },
                        imageUrl = normalizedImageUrl,
                        priceMinor = priceMinor,
                        eventId = eventId,
                        targetMemberId = targetMemberId,
                    )
                } else {
                    wishlistRepository.update(
                        id = editing.id,
                        title = title,
                        imageUrl = normalizedImageUrl,
                        priceMinor = priceMinor,
                        eventId = eventId,
                        targetMemberId = targetMemberId,
                        keepImage = photo != null || normalizedImageUrl == editing.imageUrl,
                    )
                }
                if (photo != null) {
                    val jpeg = compressImageUri(context, photo, maxEdge = WISHLIST_PHOTO_EDGE, jpegQuality = 85)
                    wishlistRepository.uploadPhoto(saved.id, jpeg)
                }
                extra.update {
                    it.copy(
                        savingWishlist = false,
                        wishlistSheetOpen = false,
                        editingWishlistItem = null,
                        wishlistPreview = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // server errors and unreadable photos
                extra.update { it.copy(savingWishlist = false, message = e.message ?: "Couldn't save") }
            }
        }
    }

    fun toggleWishlistBought(item: WishlistItem) {
        viewModelScope.launch {
            try {
                wishlistRepository.setBought(item.id, !item.bought)
            } catch (e: PicfortServerException) {
                extra.update { it.copy(message = e.message) }
            }
        }
    }

    fun toggleWishlistReserve(item: WishlistItem) {
        viewModelScope.launch {
            try {
                val next = if (item.reservedBy == null) family.myId else null
                wishlistRepository.setReservedBy(item.id, next)
            } catch (e: PicfortServerException) {
                extra.update { it.copy(message = e.message) }
            }
        }
    }

    fun deleteWishlistItem(item: WishlistItem) {
        viewModelScope.launch {
            try {
                wishlistRepository.delete(item.id)
            } catch (e: PicfortServerException) {
                extra.update { it.copy(message = e.message) }
            }
        }
    }
}

@HiltViewModel
class ShoppingListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ShoppingRepository,
    private val family: FamilyMemberProvider,
    private val receiptReader: ReceiptReader,
    savedState: SavedStateHandle,
) : ViewModel() {
    val listId: Long = savedState.get<Long>("id") ?: 0L
    private val extra = MutableStateFlow(ShoppingListUi(myId = family.myId))
    private val _state = MutableStateFlow(ShoppingListUi(myId = family.myId))
    val state: StateFlow<ShoppingListUi> = _state.asStateFlow()

    /** Set after a delete, for the snackbar's Undo. */
    var undoable: ShoppingItem? = null
        private set

    private var pendingReceiptBytes: ByteArray? = null
    private val receiptBytesCache = mutableMapOf<Long, ByteArray>()

    init {
        viewModelScope.launch {
            val supported = receiptReader.isSupported()
            extra.update { it.copy(aiSupported = supported) }
        }
        viewModelScope.launch {
            combine(
                repository.lists,
                repository.receipts,
                repository.offlineSince,
                family.members,
                extra,
            ) { lists, receiptsMap, since, members, ui ->
                val listReceipts = receiptsMap.values
                    .filter { it.listId == listId }
                    .sortedWith(compareByDescending<Receipt> { it.date }.thenByDescending { it.id })
                val activeLists = lists.values
                    .filter { !it.archived }
                    .sortedWith(compareByDescending<ShoppingList> { it.created }.thenByDescending { it.id })
                ui.copy(
                    list = lists[listId],
                    receipts = listReceipts,
                    activeLists = activeLists,
                    members = members,
                    offlineSince = since?.asTime(),
                )
            }.collect { _state.value = it }
        }
        viewModelScope.launch { repository.messages.collect { msg -> extra.update { it.copy(message = msg) } } }
        viewModelScope.launch { repository.refreshReceipts(listId) }
    }

    suspend fun keepFresh() {
        // Polls start every LIST_POLL_MILLIS, request time included, so a change made on
        // another phone shows within that time (T-019), not that time plus the request.
        pollEvery(
            intervalMillis = LIST_POLL_MILLIS,
            minDelayMillis = 1_000L,
            includeRunTimeInInterval = true,
        ) {
            val error = repository.refreshList(listId)
            if (error is PicfortServerException.Gone) {
                extra.update { it.copy(loadError = "This list no longer exists") }
            } else if (error != null &&
                repository.lists.value[listId] == null
            ) {
                extra.update { it.copy(loadError = error.message) }
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            repository.refreshList(listId)
            repository.refreshReceipts(listId)
        }
    }

    fun toggle(item: ShoppingItem) = repository.toggle(listId, item, family.myId)
    fun edit(item: ShoppingItem, text: String) = repository.edit(listId, item, text)
    fun add(text: String) = repository.addItem(listId, text)
    fun rename(title: String) = repository.rename(listId, title)
    fun archive(archived: Boolean) = repository.archive(listId, archived)
    fun uncheckAll() = repository.uncheckAll(listId)
    fun deleteChecked() = repository.deleteChecked(listId)
    fun deleteList() = repository.deleteList(listId)

    fun delete(item: ShoppingItem) {
        repository.delete(listId, item)
        undoable = item
        extra.update { it.copy(message = "Deleted \"${item.text}\"") }
    }

    fun undo() {
        undoable?.let { repository.restore(listId, it) }
        undoable = null
    }

    fun messageShown() {
        undoable = null
        pendingReceiptBytes = null
        extra.update { it.copy(message = null, canRetryReceiptUpload = false) }
    }

    fun addReceipt(uri: Uri) {
        viewModelScope.launch {
            extra.update { it.copy(uploadingReceipt = true, canRetryReceiptUpload = false) }
            try {
                val bytes = compressImageUri(context, uri, maxEdge = 2400, jpegQuality = 90)
                pendingReceiptBytes = bytes
                uploadBytes(bytes)
            } catch (e: PicfortServerException) {
                extra.update {
                    it.copy(
                        uploadingReceipt = false,
                        canRetryReceiptUpload = pendingReceiptBytes != null,
                        message = "Couldn't upload receipt: ${e.message}",
                    )
                }
            }
        }
    }

    fun retryReceiptUpload() {
        val bytes = pendingReceiptBytes ?: return
        viewModelScope.launch {
            extra.update { it.copy(uploadingReceipt = true, canRetryReceiptUpload = false) }
            try {
                uploadBytes(bytes)
            } catch (e: PicfortServerException) {
                extra.update {
                    it.copy(
                        uploadingReceipt = false,
                        canRetryReceiptUpload = true,
                        message = "Couldn't upload receipt: ${e.message}",
                    )
                }
            }
        }
    }

    private suspend fun uploadBytes(bytes: ByteArray) {
        val today = LocalDate.now().toString()
        val created = repository.uploadReceipt(bytes, date = today, listId = listId)
        pendingReceiptBytes = null
        receiptBytesCache[created.id] = bytes
        extra.update {
            it.copy(
                uploadingReceipt = false,
                canRetryReceiptUpload = false,
                openReceipt = created,
                receiptReadHint = null,
            )
        }
        if (extra.value.aiSupported) {
            readReceiptOnDevice(created, bytes)
        }
    }

    fun openReceipt(receipt: Receipt) = extra.update {
        it.copy(openReceipt = receipt, readingReceipt = false, receiptReadHint = null)
    }

    fun closeReceipt() = extra.update {
        it.copy(openReceipt = null, readingReceipt = false, receiptReadHint = null)
    }

    fun readReceiptAgain(receipt: Receipt) {
        if (!extra.value.aiSupported || extra.value.readingReceipt) return
        viewModelScope.launch {
            extra.update { it.copy(readingReceipt = true, receiptReadHint = null) }
            val bytes = receiptBytesCache[receipt.id] ?: runCatching {
                receipt.photoUrl?.let { repository.downloadReceiptPhoto(it) }
            }.getOrNull()?.also { receiptBytesCache[receipt.id] = it }
            if (bytes == null) {
                extra.update {
                    it.copy(readingReceipt = false, receiptReadHint = "Couldn't read the receipt. Type the total.")
                }
                return@launch
            }
            readReceiptOnDevice(receipt, bytes)
        }
    }

    private suspend fun readReceiptOnDevice(receipt: Receipt, bytes: ByteArray) {
        extra.update { it.copy(readingReceipt = true, receiptReadHint = null) }
        val reading = receiptReader.read(bytes)
        extra.update { ui ->
            val current = ui.openReceipt
            if (current?.id != receipt.id) return@update ui.copy(readingReceipt = false)
            val updated = if (reading != null) {
                current.copy(
                    shop = reading.shop ?: current.shop,
                    date = reading.date ?: current.date,
                    totalMinor = reading.totalMinor ?: current.totalMinor,
                )
            } else {
                current
            }
            val hint = if (reading?.totalMinor != null) {
                "Read on device. Check the total and save."
            } else {
                "Couldn't find the total. Type it."
            }
            ui.copy(openReceipt = updated, readingReceipt = false, receiptReadHint = hint)
        }
    }

    fun saveReceipt(
        receipt: Receipt,
        shop: String?,
        date: String,
        totalMinor: Long,
        payerId: String,
        targetListId: Long?,
    ) {
        viewModelScope.launch {
            extra.update { it.copy(savingReceipt = true) }
            try {
                repository.updateReceipt(receipt.id, shop, date, totalMinor, payerId, targetListId)
                repository.refreshReceipts(listId)
                extra.update { it.copy(savingReceipt = false, openReceipt = null) }
            } catch (e: PicfortServerException) {
                extra.update { it.copy(savingReceipt = false, message = "Couldn't save receipt: ${e.message}") }
            }
        }
    }

    fun deleteReceipt(receipt: Receipt) {
        viewModelScope.launch {
            extra.update { it.copy(savingReceipt = true) }
            try {
                repository.deleteReceipt(receipt.id)
                extra.update { it.copy(savingReceipt = false, openReceipt = null) }
            } catch (e: PicfortServerException) {
                extra.update { it.copy(savingReceipt = false, message = "Couldn't delete receipt: ${e.message}") }
            }
        }
    }
}

// Screens with state

@Composable
fun ShoppingListsScreen(
    onOpen: (ShoppingList, Boolean) -> Unit,
    onArchivedClick: () -> Unit = {},
    onSpendingClick: () -> Unit = {},
    onOpenEvent: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSetUpServer: () -> Unit = onSettingsClick,
) {
    val viewModel = hiltViewModel<ShoppingListsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() } }
    var creating by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(creating) {
        creating?.let { title ->
            viewModel.create(title)?.let { onOpen(it, true) }
            creating = null
        }
    }
    ShoppingListsContent(
        state = state,
        archived = viewModel.archived,
        onOpen = { onOpen(it, false) },
        onCreate = { creating = it },
        onArchivedClick = onArchivedClick,
        onSpendingClick = onSpendingClick,
        onOpenEvent = onOpenEvent,
        onBack = onBack,
        onSettingsClick = onSettingsClick,
        onRefresh = viewModel::refreshNow,
        onSetUpServer = onSetUpServer,
        onMessageShown = viewModel::messageShown,
        onAddWishlistClick = viewModel::openNewWishlistItem,
        onEditWishlistItem = viewModel::editWishlistItem,
        onCloseWishlistSheet = viewModel::closeWishlistSheet,
        onPreviewWishlistUrl = viewModel::previewWishlistUrl,
        onSaveWishlistItem = viewModel::saveWishlistItem,
        onToggleWishlistBought = viewModel::toggleWishlistBought,
        onToggleWishlistReserve = viewModel::toggleWishlistReserve,
        onDeleteWishlistItem = viewModel::deleteWishlistItem,
    )
}

@Composable
fun ShoppingListScreen(onBack: () -> Unit, focusAdd: Boolean) {
    val viewModel = hiltViewModel<ShoppingListViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.keepFresh() } }
    val context = LocalContext.current
    var pendingCamera by rememberSaveable { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addReceipt(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) pendingCamera?.let(viewModel::addReceipt)
    }
    ShoppingListContent(
        state = state,
        onBack = onBack,
        onToggle = { viewModel.toggle(it) },
        onEdit = { item, text -> viewModel.edit(item, text) },
        onAdd = { viewModel.add(it) },
        onDelete = viewModel::delete,
        onUndo = viewModel::undo,
        canUndo = { viewModel.undoable != null },
        onRename = { viewModel.rename(it) },
        onUncheckAll = { viewModel.uncheckAll() },
        onDeleteChecked = { viewModel.deleteChecked() },
        onArchive = { viewModel.archive(it) },
        onDeleteList = {
            viewModel.deleteList()
            onBack()
        },
        onRefresh = viewModel::refreshNow,
        onMessageShown = viewModel::messageShown,
        onTakeReceiptPhoto = {
            val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            pendingCamera = uri
            camera.launch(uri)
        },
        onChooseReceiptPhoto = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRetryReceiptUpload = viewModel::retryReceiptUpload,
        onOpenReceipt = viewModel::openReceipt,
        onCloseReceipt = viewModel::closeReceipt,
        onSaveReceipt = viewModel::saveReceipt,
        onDeleteReceipt = viewModel::deleteReceipt,
        onReadReceiptAgain = viewModel::readReceiptAgain,
        focusAdd = focusAdd,
    )
}
