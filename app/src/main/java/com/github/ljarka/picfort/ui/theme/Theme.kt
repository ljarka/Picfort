package com.github.ljarka.picfort.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TerracottaDark,
    onPrimary = OnTerracottaDark,
    primaryContainer = TerracottaContainerDark,
    onPrimaryContainer = OnTerracottaContainerDark,
    secondary = SlateDark,
    onSecondary = OnSlateDark,
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = OnSlateContainerDark,
    tertiary = AmberDark,
    onTertiary = OnAmberDark,
    tertiaryContainer = AmberContainerDark,
    onTertiaryContainer = OnAmberContainerDark,
    background = WarmDarkBackground,
    surface = WarmDarkSurface,
    surfaceVariant = WarmDarkSurfaceVariant,
    onBackground = OnWarmDarkBackground,
    onSurface = OnWarmDarkSurface,
    onSurfaceVariant = OnWarmDarkSurfaceVariant,
    outline = WarmDarkOutline,
)

private val LightColorScheme = lightColorScheme(
    primary = TerracottaLight,
    onPrimary = OnTerracottaLight,
    primaryContainer = TerracottaContainerLight,
    onPrimaryContainer = OnTerracottaContainerLight,
    secondary = SlateLight,
    onSecondary = OnSlateLight,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = OnSlateContainerLight,
    tertiary = AmberLight,
    onTertiary = OnAmberLight,
    tertiaryContainer = AmberContainerLight,
    onTertiaryContainer = OnAmberContainerLight,
    background = WarmCreamBackground,
    surface = WarmCreamSurface,
    surfaceVariant = WarmSurfaceVariant,
    onBackground = OnWarmBackground,
    onSurface = OnWarmSurface,
    onSurfaceVariant = OnWarmSurfaceVariant,
    outline = WarmOutline,
)

@Composable
fun PicfortTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
