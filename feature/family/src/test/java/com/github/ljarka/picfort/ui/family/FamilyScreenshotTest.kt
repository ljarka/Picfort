package com.github.ljarka.picfort.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.github.ljarka.picfort.core.model.BirthDate
import com.github.ljarka.picfort.core.ui.avatar.avatarColor
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import org.junit.Rule
import org.junit.Test

class FamilyScreenshotTest {

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

    private val familyUsers = listOf(
        FamilyUserUi(
            "mama",
            name = "Mama",
            immichName = "Anna Kowalska",
            avatarUrl = "avatar-mama",
            hasCustomPhoto = true,
            color = avatarColor("pink"),
            isMe = true,
            birthDate = BirthDate(month = 5, day = 14, year = 1988),
        ),
        FamilyUserUi(
            "tata",
            name = "Tata",
            immichName = "Tata",
            avatarUrl = null,
            color = avatarColor("blue"),
            birthDate = BirthDate(month = 11, day = 28),
        ),
        FamilyUserUi(
            "ola",
            name = "Ola",
            immichName = "Aleksandra K.",
            avatarUrl = "avatar-ola",
            hasCustomPhoto = true,
            color = avatarColor("green"),
            birthDate = BirthDate(month = 9, day = 3, year = 2018),
        ),
        FamilyUserUi(
            "babcia",
            name = "Babcia Zosia",
            immichName = "Zofia Nowak",
            avatarUrl = null,
            color = avatarColor("amber"),
        ),
        FamilyUserUi(
            "stasiu",
            name = "Staś",
            immichName = "",
            avatarUrl = null,
            color = avatarColor("teal"),
            isImmichUser = false,
            birthDate = BirthDate(month = 3, day = 12, year = 2021),
        ),
        FamilyUserUi(
            "test",
            name = "LukaszTest",
            immichName = "LukaszTest",
            avatarUrl = null,
            color = avatarColor("gray"),
            hidden = true,
        ),
    )

    @Test
    fun family_settings() = snap {
        FamilySettingsContent(FamilySettingsUi(users = familyUsers))
    }

    @Test
    fun family_settings_dark() = snap(dark = true) {
        FamilySettingsContent(FamilySettingsUi(users = familyUsers))
    }

    @Composable
    private fun WithSheet(background: @Composable () -> Unit, sheet: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize()) {
            background()
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)))
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .padding(vertical = 12.dp)
                            .size(width = 32.dp, height = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(2.dp),
                            ),
                    )
                    sheet()
                }
            }
        }
    }

    @Test
    fun family_member_sheet() = snap {
        WithSheet(
            background = { FamilySettingsContent(FamilySettingsUi(users = familyUsers)) },
            sheet = {
                FamilyMemberSheetContent(
                    FamilyMemberEditUi(
                        user = familyUsers[2], // Ola (Aleksandra K.), so the switch is enabled
                        displayName = "Ola",
                        previewAvatarUrl = "avatar-ola",
                        hasCustomPhoto = true,
                        showInFamily = true,
                        birthDate = BirthDate(month = 9, day = 3, year = 2018),
                    )
                )
            },
        )
    }

    @Test
    fun family_manual_member_sheet() = snap {
        WithSheet(
            background = { FamilySettingsContent(FamilySettingsUi(users = familyUsers)) },
            sheet = {
                FamilyMemberSheetContent(
                    FamilyMemberEditUi(
                        user = familyUsers.first { !it.isImmichUser }, // Staś: no Immich account
                        previewAvatarUrl = "avatar-stas",
                        hasCustomPhoto = true,
                    )
                )
            },
        )
    }

    @Test
    fun family_add_member_sheet() = snap {
        WithSheet(
            background = { FamilySettingsContent(FamilySettingsUi(users = familyUsers)) },
            sheet = {
                AddPersonSheetContent(
                    AddPersonUi(
                        name = "Dziadek Jan",
                        birthDate = BirthDate(month = 6, day = 20, year = 1955),
                    )
                )
            },
        )
    }

    @Test
    fun people_picker() = snap {
        val people = listOf("Ola", "Staś", "Mama", "Tata", "Babcia Zosia", "Dziadek Jan", "Ciocia Kasia", "Wujek Marek")
            .mapIndexed { i, name -> ImmichPersonItem(id = "p-$i", name = name, thumbnailUrl = "person-$i") }
        WithSheet(
            background = { FamilySettingsContent(FamilySettingsUi(users = familyUsers)) },
            sheet = { PeoplePickerSheetContent(PeoplePickerUi(people = people, profilePictureUrl = "avatar-mama")) },
        )
    }
}
