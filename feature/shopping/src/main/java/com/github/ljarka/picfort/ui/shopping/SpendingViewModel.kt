package com.github.ljarka.picfort.ui.shopping

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.core.model.FamilyMemberProvider
import com.github.ljarka.picfort.core.model.Receipt
import com.github.ljarka.picfort.core.model.ShoppingList
import com.github.ljarka.picfort.core.network.PicfortServerException
import com.github.ljarka.picfort.core.network.compressImageUri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SpendingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ShoppingRepository,
    private val family: FamilyMemberProvider,
    private val receiptReader: ReceiptReader,
) : ViewModel() {
    private val extra = MutableStateFlow(SpendingUi(myId = family.myId))
    private val _state = MutableStateFlow(SpendingUi(myId = family.myId))
    val state: StateFlow<SpendingUi> = _state.asStateFlow()

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
                family.members,
                extra,
            ) { lists, members, ui ->
                val activeLists = lists.values
                    .filter { !it.archived }
                    .sortedWith(compareByDescending<ShoppingList> { it.created }.thenByDescending { it.id })
                ui.copy(activeLists = activeLists, members = members)
            }.collect { _state.value = it }
        }
        load(extra.value.selectedMonth)
    }

    fun refresh() = load(extra.value.selectedMonth)

    fun previousMonth() = selectMonth(extra.value.selectedMonth.minusMonths(1))

    fun nextMonth() {
        val next = extra.value.selectedMonth.plusMonths(1)
        if (!next.isAfter(YearMonth.now())) selectMonth(next)
    }

    fun selectMonth(month: YearMonth) {
        val now = YearMonth.now()
        val clamped = if (month.isAfter(now)) now else month
        if (clamped == extra.value.selectedMonth && extra.value.summary != null) return
        load(clamped)
    }

    private fun load(month: YearMonth) {
        val now = YearMonth.now()
        extra.update {
            it.copy(
                selectedMonth = month,
                canGoNext = month.isBefore(now),
                isLoading = true,
                loadError = null,
            )
        }
        viewModelScope.launch {
            try {
                val (summary, receipts) = coroutineScope {
                    val s = async { repository.spending(month.minusMonths(5).toString(), month.toString()) }
                    val r = async { repository.receiptsForMonth(month.toString()) }
                    s.await() to r.await()
                }
                if (extra.value.selectedMonth == month) {
                    extra.update {
                        it.copy(
                            summary = summary,
                            receipts = receiptsSortedForSpending(receipts),
                            isLoading = false,
                            loadError = null,
                        )
                    }
                }
            } catch (e: PicfortServerException) {
                if (extra.value.selectedMonth == month) {
                    extra.update { it.copy(isLoading = false, loadError = e.message ?: "Couldn't load spending") }
                }
            }
        }
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
        val created = repository.uploadReceipt(bytes, date = today, listId = null)
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
        load(extra.value.selectedMonth)
        if (extra.value.aiSupported) {
            readReceiptOnDevice(created, bytes)
        }
    }

    fun openReceipt(receipt: Receipt) = extra.update {
        it.copy(openReceipt = receipt, readingReceipt = false, receiptReadHint = null)
    }

    fun closeReceipt() {
        extra.update { it.copy(openReceipt = null, readingReceipt = false, receiptReadHint = null) }
        load(extra.value.selectedMonth)
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
                extra.update { it.copy(savingReceipt = false, openReceipt = null) }
                load(extra.value.selectedMonth)
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
                load(extra.value.selectedMonth)
            } catch (e: PicfortServerException) {
                extra.update { it.copy(savingReceipt = false, message = "Couldn't delete receipt: ${e.message}") }
            }
        }
    }

    fun messageShown() {
        pendingReceiptBytes = null
        extra.update { it.copy(message = null, canRetryReceiptUpload = false) }
    }
}
