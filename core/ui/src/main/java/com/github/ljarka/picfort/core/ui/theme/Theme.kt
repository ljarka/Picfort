package com.github.ljarka.picfort.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

val DarkColorScheme = darkColorScheme(
    primary = NavyDark,
    onPrimary = OnNavyDark,
    primaryContainer = NavyContainerDark,
    onPrimaryContainer = OnNavyContainerDark,
    inversePrimary = NavyLight,
    secondary = SkyDark,
    onSecondary = OnSkyDark,
    secondaryContainer = SkyContainerDark,
    onSecondaryContainer = OnSkyContainerDark,
    tertiary = CoralDark,
    onTertiary = OnCoralDark,
    tertiaryContainer = CoralContainerDark,
    onTertiaryContainer = OnCoralContainerDark,
    background = NightBackground,
    onBackground = OnNight,
    surface = NightBackground,
    onSurface = OnNight,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = OnNightVariant,
    surfaceTint = NavyDark,
    inverseSurface = OnNight,
    inverseOnSurface = NightContainer,
    outline = NightOutline,
    outlineVariant = NightOutlineVariant,
    surfaceBright = NightBright,
    surfaceDim = NightBackground,
    surfaceContainerLowest = NightContainerLowest,
    surfaceContainerLow = NightContainerLow,
    surfaceContainer = NightContainer,
    surfaceContainerHigh = NightContainerHigh,
    surfaceContainerHighest = NightContainerHighest,
)

val LightColorScheme = lightColorScheme(
    primary = NavyLight,
    onPrimary = OnNavyLight,
    primaryContainer = NavyContainerLight,
    onPrimaryContainer = OnNavyContainerLight,
    inversePrimary = NavyDark,
    secondary = SkyLight,
    onSecondary = OnSkyLight,
    secondaryContainer = SkyContainerLight,
    onSecondaryContainer = OnSkyContainerLight,
    tertiary = CoralLight,
    onTertiary = OnCoralLight,
    tertiaryContainer = CoralContainerLight,
    onTertiaryContainer = OnCoralContainerLight,
    background = CreamBackground,
    onBackground = OnCream,
    surface = CreamBackground,
    onSurface = OnCream,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = OnCreamVariant,
    surfaceTint = NavyLight,
    inverseSurface = OnCream,
    inverseOnSurface = CreamContainer,
    outline = CreamOutline,
    outlineVariant = CreamOutlineVariant,
    surfaceBright = CreamBackground,
    surfaceDim = CreamDim,
    surfaceContainerLowest = CreamContainerLowest,
    surfaceContainerLow = CreamContainerLow,
    surfaceContainer = CreamContainer,
    surfaceContainerHigh = CreamContainerHigh,
    surfaceContainerHighest = CreamContainerHighest,
)

/**
 * Picfort's palette (from the app icon) everywhere by default. [dynamicColor] switches to
 * Material You wallpaper colors on Android 12+ (Settings → Appearance).
 */
@Composable
fun PicfortTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
