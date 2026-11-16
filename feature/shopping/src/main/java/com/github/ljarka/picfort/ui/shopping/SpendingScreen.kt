package com.github.ljarka.picfort.ui.shopping

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.ljarka.picfort.core.model.Receipt
import com.github.ljarka.picfort.core.ui.avatar.MemberAvatar
import java.io.File
import java.time.YearMonth
import java.util.UUID

@Composable
fun SpendingScreen(onBack: () -> Unit) {
    val viewModel = hiltViewModel<SpendingViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingCamera by rememberSaveable { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addReceipt(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) pendingCamera?.let(viewModel::addReceipt)
    }
    SpendingContent(
        state = state,
        onBack = onBack,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSelectMonth = viewModel::selectMonth,
        onRefresh = viewModel::refresh,
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
        onMessageShown = viewModel::messageShown,
        onOpenReceipt = viewModel::openReceipt,
        onCloseReceipt = viewModel::closeReceipt,
        onSaveReceipt = viewModel::saveReceipt,
        onDeleteReceipt = viewModel::deleteReceipt,
        onReadReceiptAgain = viewModel::readReceiptAgain,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingContent(
    state: SpendingUi,
    onBack: () -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onSelectMonth: (YearMonth) -> Unit = {},
    onRefresh: () -> Unit = {},
    onTakeReceiptPhoto: () -> Unit = {},
    onChooseReceiptPhoto: () -> Unit = {},
    onRetryReceiptUpload: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    onOpenReceipt: (Receipt) -> Unit = {},
    onCloseReceipt: () -> Unit = {},
    onSaveReceipt: (Receipt, String?, String, Long, String, Long?) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteReceipt: (Receipt) -> Unit = {},
    onReadReceiptAgain: (Receipt) -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message, state.canRetryReceiptUpload) {
        state.message?.let {
            val action = if (state.canRetryReceiptUpload) "Retry" else null
            val result = snackbar.showSnackbar(it, actionLabel = action)
            if (result == SnackbarResult.ActionPerformed && state.canRetryReceiptUpload) {
                onRetryReceiptUpload()
            }
            onMessageShown()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spending") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            var fabMenu by remember { mutableStateOf(false) }
            Box {
                ExtendedFloatingActionButton(
                    onClick = { fabMenu = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Receipt") },
                )
                DropdownMenu(expanded = fabMenu, onDismissRequest = { fabMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Take photo") },
                        onClick = {
                            fabMenu = false
                            onTakeReceiptPhoto()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Choose photo") },
                        onClick = {
                            fabMenu = false
                            onChooseReceiptPhoto()
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.summary != null,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading && state.summary == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                state.loadError != null && state.summary == null -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.loadError, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRefresh) { Text("Retry") }
                    }
                }

                else -> {
                    SpendingBody(
                        state = state,
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth,
                        onSelectMonth = onSelectMonth,
                        onOpenReceipt = onOpenReceipt,
                    )
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

@Composable
private fun SpendingBody(
    state: SpendingUi,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onOpenReceipt: (Receipt) -> Unit,
) {
    val selected = state.selectedSummary
    val previous = state.previousSummary
    val months = state.summary?.months.orEmpty()
    val shops = topShopsWithOther(selected?.byShop.orEmpty())
    val payers = selected?.byPayer.orEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        if (state.uploadingReceipt) {
            item(key = "uploading") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Uploading receipt…", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }

        item(key = "month-header") {
            MonthSwitcher(
                month = state.selectedMonth,
                canGoNext = state.canGoNext,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatTotalMinor(selected?.totalMinor ?: 0L, state.currency),
                    style = MaterialTheme.typography.headlineLarge,
                )
                val pct = if (selected != null && previous != null) {
                    monthComparisonPercent(selected.totalMinor, previous.totalMinor)
                } else {
                    null
                }
                if (pct != null) {
                    val sign = if (pct > 0) "+" else ""
                    val prevName = formatMonthYear(state.selectedMonth.minusMonths(1)).substringBefore(' ')
                    Text(
                        text = "$sign$pct% vs $prevName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected != null && (selected.unconfirmedCount > 0 || selected.missingTotalCount > 0)) {
                    Spacer(Modifier.height(4.dp))
                    if (selected.unconfirmedCount > 0) {
                        val n = selected.unconfirmedCount
                        Text(
                            text = if (n == 1) "1 receipt not confirmed" else "$n receipts not confirmed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (selected.missingTotalCount > 0) {
                        val n = selected.missingTotalCount
                        Text(
                            text = if (n == 1) "1 receipt without a total" else "$n receipts without a total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        if (months.isNotEmpty()) {
            item(key = "bars") {
                Spacer(Modifier.height(16.dp))
                SixMonthBarChart(
                    months = months,
                    selectedMonth = state.selectedMonth.toString(),
                    currency = state.currency,
                    onSelect = { iso -> runCatching { YearMonth.parse(iso) }.getOrNull()?.let(onSelectMonth) },
                )
            }
        }

        if (selected == null || (selected.receiptCount == 0 && state.receipts.isEmpty())) {
            item(key = "empty") {
                Text(
                    text = "No receipts in ${formatMonthYear(state.selectedMonth)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 40.dp),
                )
            }
        } else {
            if (shops.isNotEmpty()) {
                item(key = "shops-header") {
                    SectionHeader("By shop")
                }
                items(shops, key = { "shop-${it.shop}" }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = entry.shop ?: "Unknown",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = formatTotalMinor(entry.totalMinor, state.currency),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            if (payers.isNotEmpty()) {
                item(key = "payers-header") {
                    SectionHeader("By person")
                }
                items(payers, key = { "payer-${it.payerId}" }) { entry ->
                    val member = state.memberOf(entry.payerId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (member != null) {
                            MemberAvatar(member, size = 28.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            text = member?.name ?: "Former member",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = formatTotalMinor(entry.totalMinor, state.currency),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            if (state.receipts.isNotEmpty()) {
                item(key = "receipts-header") {
                    SectionHeader("Receipts")
                }
                items(state.receipts, key = { "r-${it.id}" }) { receipt ->
                    SpendingReceiptRow(receipt = receipt, onClick = { onOpenReceipt(receipt) })
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, canGoNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            text = formatMonthYear(month),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(180.dp),
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun SixMonthBarChart(
    months: List<SpendingMonth>,
    selectedMonth: String,
    currency: String,
    onSelect: (String) -> Unit,
) {
    val maxMinor = (months.maxOfOrNull { it.totalMinor } ?: 0L).coerceAtLeast(1L)
    val selectedColor = MaterialTheme.colorScheme.primary
    val otherColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { m ->
            val isSelected = m.month == selectedMonth
            val fraction = (m.totalMinor.toFloat() / maxMinor.toFloat()).coerceIn(0f, 1f)
            val description = "${formatFullMonthYear(m.month)}, ${formatTotalMinor(m.totalMinor, currency)}"
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(m.month) }
                    .semantics { contentDescription = description }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                ) {
                    val barWidth = size.width * 0.65f
                    val left = (size.width - barWidth) / 2f
                    val minHeight = if (m.totalMinor > 0L) 6.dp.toPx() else 2.dp.toPx()
                    val barHeight = (size.height * fraction).coerceAtLeast(minHeight)
                    val top = size.height - barHeight
                    val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    drawRoundRect(
                        color = if (isSelected) selectedColor else otherColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                        cornerRadius = radius,
                    )
                    if (isSelected) {
                        drawRoundRect(
                            color = outlineColor,
                            topLeft = Offset(left, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = radius,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatShortMonth(m.month),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 8.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun SpendingReceiptRow(receipt: Receipt, onClick: () -> Unit) {
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
