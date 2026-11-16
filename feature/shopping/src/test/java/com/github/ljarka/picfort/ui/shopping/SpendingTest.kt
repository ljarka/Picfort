package com.github.ljarka.picfort.ui.shopping

import com.github.ljarka.picfort.core.model.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpendingTest {
    @Test
    fun monthComparisonPercentHandlesZeroAndChanges() {
        assertNull(monthComparisonPercent(1000L, 0L))
        assertEquals(12, monthComparisonPercent(1120L, 1000L))
        assertEquals(-25, monthComparisonPercent(750L, 1000L))
        assertEquals(0, monthComparisonPercent(1000L, 1000L))
    }

    @Test
    fun topShopsWithOtherCapsAtFiveAndMapsNullToUnknown() {
        val input = listOf(
            ShopSpending("Biedronka", 5000),
            ShopSpending("Lidl", 4000),
            ShopSpending(null, 3000),
            ShopSpending("Orlen", 2000),
            ShopSpending("Rossmann", 1000),
            ShopSpending("Zabka", 500),
            ShopSpending("Empik", 300),
        )
        val result = topShopsWithOther(input)
        assertEquals(6, result.size)
        assertEquals("Unknown", result[2].shop)
        assertEquals(ShopSpending("Other", 800), result[5])
    }

    @Test
    fun receiptsSortedForSpendingPutsMissingTotalFirst() {
        val r1 = Receipt(id = 1, date = "2026-11-12", totalMinor = 5000)
        val r2 = Receipt(id = 2, date = "2026-11-10", totalMinor = null)
        val r3 = Receipt(id = 3, date = "2026-11-11", totalMinor = 2000)
        val sorted = receiptsSortedForSpending(listOf(r1, r2, r3))
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }
}
