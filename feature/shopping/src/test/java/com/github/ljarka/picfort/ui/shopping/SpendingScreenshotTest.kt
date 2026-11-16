package com.github.ljarka.picfort.ui.shopping

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.Receipt
import com.github.ljarka.picfort.core.ui.avatar.avatarColor
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test

class SpendingScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        renderingMode = SessionParams.RenderingMode.NORMAL,
    )

    private fun snap(dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        paparazzi.snapshot { PicfortTheme(darkTheme = dark, content = content) }
    }

    private val members = listOf(
        FamilyMember("mama", "Mama", avatarUrl = null, color = avatarColor("pink")),
        FamilyMember("tata", "Tata", avatarUrl = null, color = avatarColor("blue")),
    )

    private val sampleMonths = listOf(
        SpendingMonth("2026-06", 62000, 6, 0, 0, emptyList(), emptyList()),
        SpendingMonth("2026-07", 88000, 8, 0, 0, emptyList(), emptyList()),
        SpendingMonth("2026-08", 95000, 9, 0, 0, emptyList(), emptyList()),
        SpendingMonth("2026-09", 71000, 7, 0, 0, emptyList(), emptyList()),
        SpendingMonth("2026-10", 114600, 11, 0, 0, emptyList(), emptyList()),
        SpendingMonth(
            month = "2026-11",
            totalMinor = 128450,
            receiptCount = 14,
            unconfirmedCount = 2,
            missingTotalCount = 1,
            byShop = listOf(
                ShopSpending("Biedronka", 64000),
                ShopSpending("Lidl", 42000),
                ShopSpending(null, 22450),
            ),
            byPayer = listOf(
                PayerSpending("mama", 90000),
                PayerSpending("tata", 38450),
            ),
        ),
    )

    private val sampleReceipts = listOf(
        Receipt(id = 1, shop = "Orlen", date = "2026-11-10", totalMinor = null, payerId = "tata", status = "manual"),
        Receipt(id = 2, shop = "Lidl", date = "2026-11-12", totalMinor = 8410, payerId = "mama", status = "confirmed"),
    )

    @Test
    fun spending_with_data() = snap {
        SpendingContent(
            SpendingUi(
                selectedMonth = YearMonth.of(2026, 11),
                canGoNext = false,
                summary = SpendingSummary("PLN", sampleMonths),
                receipts = sampleReceipts,
                members = members,
                myId = "mama",
            ),
        )
    }

    @Test
    fun spending_empty_month() = snap {
        SpendingContent(
            SpendingUi(
                selectedMonth = YearMonth.of(2026, 11),
                canGoNext = false,
                summary = SpendingSummary(
                    "PLN",
                    listOf(SpendingMonth("2026-11", 0, 0, 0, 0, emptyList(), emptyList())),
                ),
                receipts = emptyList(),
                members = members,
                myId = "mama",
            ),
        )
    }

    @Test
    fun spending_notes_dark() = snap(dark = true) {
        SpendingContent(
            SpendingUi(
                selectedMonth = YearMonth.of(2026, 11),
                canGoNext = false,
                summary = SpendingSummary("PLN", sampleMonths),
                receipts = sampleReceipts,
                members = members,
                myId = "mama",
            ),
        )
    }
}
