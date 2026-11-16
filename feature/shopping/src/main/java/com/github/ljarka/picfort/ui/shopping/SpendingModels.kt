package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.Receipt
import com.github.ljarka.picfort.core.model.ShoppingList
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

data class ShopSpending(val shop: String?, val totalMinor: Long)

data class PayerSpending(val payerId: String, val totalMinor: Long)

data class SpendingMonth(
    val month: String,
    val totalMinor: Long,
    val receiptCount: Int,
    val unconfirmedCount: Int,
    val missingTotalCount: Int,
    val byShop: List<ShopSpending>,
    val byPayer: List<PayerSpending>,
)

data class SpendingSummary(val currency: String = "PLN", val months: List<SpendingMonth> = emptyList())

data class SpendingUi(
    val selectedMonth: YearMonth = YearMonth.now(),
    val canGoNext: Boolean = false,
    val summary: SpendingSummary? = null,
    val receipts: List<Receipt> = emptyList(),
    val members: List<FamilyMember> = emptyList(),
    val activeLists: List<ShoppingList> = emptyList(),
    val myId: String = "",
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val uploadingReceipt: Boolean = false,
    val canRetryReceiptUpload: Boolean = false,
    val message: String? = null,
    val openReceipt: Receipt? = null,
    val savingReceipt: Boolean = false,
    val aiSupported: Boolean = false,
    val readingReceipt: Boolean = false,
    val receiptReadHint: String? = null,
) {
    val selectedSummary: SpendingMonth?
        get() = summary?.months?.lastOrNull { it.month == selectedMonth.toString() }

    val previousSummary: SpendingMonth?
        get() = summary?.months?.lastOrNull { it.month == selectedMonth.minusMonths(1).toString() }

    val currency: String get() = summary?.currency ?: "PLN"

    fun memberOf(id: String): FamilyMember? = members.firstOrNull { it.id == id }
}

/** Percentage change from [previous] to [current], or null when [previous] is zero. */
fun monthComparisonPercent(current: Long, previous: Long): Int? {
    if (previous <= 0L) return null
    return (((current - previous) * 100.0) / previous).roundToInt()
}

/** Keeps the top 5 shops and groups the rest into "Other". Null shop becomes "Unknown". */
fun topShopsWithOther(byShop: List<ShopSpending>): List<ShopSpending> {
    val named = byShop.map { it.copy(shop = it.shop?.takeIf { s -> s.isNotBlank() } ?: "Unknown") }
    if (named.size <= 5) return named
    val top = named.take(5)
    val otherTotal = named.drop(5).sumOf { it.totalMinor }
    return top + ShopSpending(shop = "Other", totalMinor = otherTotal)
}

/** Receipts without a total first, then newest by date and id. */
fun receiptsSortedForSpending(receipts: List<Receipt>): List<Receipt> = receipts.sortedWith(
    compareBy<Receipt> { it.totalMinor != null }
        .thenByDescending { it.date }
        .thenByDescending { it.id },
)

fun formatMonthYear(month: YearMonth, locale: Locale = Locale.getDefault()): String =
    month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

fun formatShortMonth(isoMonth: String, locale: Locale = Locale.getDefault()): String = runCatching {
    YearMonth.parse(isoMonth).format(DateTimeFormatter.ofPattern("MMM", locale))
}.getOrElse { isoMonth }

fun formatFullMonthYear(isoMonth: String, locale: Locale = Locale.getDefault()): String =
    runCatching { formatMonthYear(YearMonth.parse(isoMonth), locale) }.getOrElse { isoMonth }
