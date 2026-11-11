package com.github.ljarka.picfort.ui.shopping

import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.ShoppingItem
import com.github.ljarka.picfort.core.model.ShoppingList
import com.github.ljarka.picfort.core.ui.avatar.avatarColor
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import org.junit.Rule
import org.junit.Test

class ShoppingScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private fun snap(dark: Boolean = false, content: @Composable () -> Unit) {
        if (dark) paparazzi.unsafeUpdateConfig(DeviceConfig.PIXEL_6.copy(nightMode = NightMode.NIGHT))
        paparazzi.snapshot {
            PicfortTheme(darkTheme = dark, dynamicColor = false) {
                content()
            }
        }
    }

    private val members = listOf(
        FamilyMember("mama", "Mama", avatarUrl = "avatar-mama", color = avatarColor("pink")),
        FamilyMember("tata", "Tata", avatarUrl = null, color = avatarColor("blue")),
        FamilyMember("ola", "Ola", avatarUrl = "avatar-ola", color = avatarColor("green")),
        FamilyMember("babcia", "Babcia Zosia", avatarUrl = null, color = avatarColor("amber")),
    )

    private val groceries = ShoppingList(
        id = 1,
        title = "Biedronka",
        createdBy = "mama",
        items = listOf(
            ShoppingItem(1, "Mleko 2x", position = 1.0),
            ShoppingItem(2, "Chleb", position = 2.0),
            ShoppingItem(3, "Jajka", position = 3.0),
            ShoppingItem(4, "Pomidory", position = 4.0),
            ShoppingItem(5, "Masło", checked = true, checkedBy = "tata", checkedAt = "2026-11-10T12:05:00Z"),
            ShoppingItem(6, "Ser żółty", checked = true, checkedBy = "ola", checkedAt = "2026-11-10T12:04:00Z"),
            ShoppingItem(7, "Kawa", checked = true, checkedBy = "mama", checkedAt = "2026-11-10T12:01:00Z"),
        ),
    )

    private val shoppingLists = listOf(
        groceries,
        ShoppingList(
            2,
            "Weekend: grill",
            items = listOf(
                ShoppingItem(10, "Węgiel", position = 1.0),
                ShoppingItem(11, "Kiełbasa", position = 2.0),
                ShoppingItem(12, "Musztarda", position = 3.0),
            ),
        ),
        ShoppingList(
            3,
            "Apteka",
            items = listOf(
                ShoppingItem(20, "Witamina D", checked = true, checkedAt = "x"),
            ),
        ),
        ShoppingList(
            4,
            "Rossmann",
            items = (1..9).map {
                ShoppingItem(
                    30L + it,
                    listOf(
                        "Pasta do zębów",
                        "Szampon",
                        "Płyn do naczyń",
                        "Ręczniki papierowe",
                        "Mydło",
                        "Chusteczki",
                        "Krem",
                        "Gąbki",
                        "Worki",
                    )[it - 1],
                    position = it.toDouble(),
                )
            },
        ),
    )

    @Test
    fun shopping_lists() = snap {
        ShoppingListsContent(ShoppingListsUi(lists = shoppingLists))
    }

    @Test
    fun shopping_lists_empty() = snap {
        ShoppingListsContent(ShoppingListsUi())
    }

    @Test
    fun shopping_lists_archived() = snap {
        ShoppingListsContent(
            ShoppingListsUi(lists = shoppingLists.take(2).map { it.copy(archived = true) }),
            archived = true,
        )
    }

    @Test
    fun shopping_lists_offline_dark() = snap(dark = true) {
        ShoppingListsContent(
            ShoppingListsUi(lists = shoppingLists, offlineSince = "14:32"),
        )
    }

    @Test
    fun shopping_list() = snap {
        ShoppingListContent(ShoppingListUi(list = groceries, members = members, myId = "mama"))
    }

    @Test
    fun shopping_list_checked_open() = snap {
        ShoppingListContent(
            ShoppingListUi(list = groceries, members = members, myId = "mama"),
            checkedOpen = true,
        )
    }

    @Test
    fun shopping_list_offline() = snap {
        ShoppingListContent(
            ShoppingListUi(list = groceries, members = members, myId = "mama", offlineSince = "14:32"),
            checkedOpen = true,
        )
    }
}
