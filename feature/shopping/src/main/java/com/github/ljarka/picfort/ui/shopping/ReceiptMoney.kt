package com.github.ljarka.picfort.ui.shopping

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private const val MAX_TOTAL_MINOR = 100_000_000L

/**
 * Parses a user-typed total (accepting `,` or `.` as decimal separator, at most two decimals,
 * strictly greater than zero) into minor units using [BigDecimal], or null if invalid.
 */
fun parseTotalMinor(input: String): Long? {
    val normalized = input.trim().replace(" ", "").replace(',', '.')
    if (normalized.isEmpty()) return null
    val match = Regex("^(\\d+)(?:\\.(\\d{1,2}))?$").matchEntire(normalized) ?: return null
    val bd = runCatching { BigDecimal(match.value) }.getOrNull() ?: return null
    val minor = runCatching { bd.movePointRight(2).longValueExact() }.getOrNull() ?: return null
    return minor.takeIf { it in 1..MAX_TOTAL_MINOR }
}

/** Formats minor units (e.g. `8410`) with [NumberFormat.getCurrencyInstance] for [locale] and [currencyCode]. */
fun formatTotalMinor(totalMinor: Long, currencyCode: String = "PLN", locale: Locale = Locale.getDefault()): String {
    val amount = BigDecimal.valueOf(totalMinor, 2)
    val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
    if (currency == null) {
        return "${amount.toPlainString()} $currencyCode"
    }
    return NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = currency
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)
}

/** Formats minor units for editing in a decimal text field using the locale's decimal separator. */
fun formatMinorForInput(totalMinor: Long, locale: Locale = Locale.getDefault()): String {
    val sep = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    val plain = BigDecimal.valueOf(totalMinor, 2).toPlainString()
    return if (sep == '.') plain else plain.replace('.', sep)
}
