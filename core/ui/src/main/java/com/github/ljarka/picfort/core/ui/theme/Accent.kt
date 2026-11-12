package com.github.ljarka.picfort.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The aperture blades of the app icon. Each tab wears one ([AccentTheme]), so the app carries
 * the icon's colors while the base palette stays navy and cream. Strong colors keep >= 4.5:1
 * against their "on" color and the background.
 */
enum class BrandAccent(internal val light: AccentColors, internal val dark: AccentColors) {
    Coral(
        light = AccentColors(Color(0xFFAE3F33), Color(0xFFFFFFFF), Color(0xFFFFDAD4), Color(0xFF3E0500)),
        dark = AccentColors(Color(0xFFFFB4A8), Color(0xFF5F1610), Color(0xFF8A2E24), Color(0xFFFFDAD4)),
    ),
    Amber(
        light = AccentColors(Color(0xFF805200), Color(0xFFFFFFFF), Color(0xFFFFE0A8), Color(0xFF2A1800)),
        dark = AccentColors(Color(0xFFFCC054), Color(0xFF452B00), Color(0xFF654000), Color(0xFFFFE0A8)),
    ),
    Mint(
        light = AccentColors(Color(0xFF1E6B47), Color(0xFFFFFFFF), Color(0xFFB4F1CD), Color(0xFF002113)),
        dark = AccentColors(Color(0xFF90E4B4), Color(0xFF00391F), Color(0xFF005232), Color(0xFFB4F1CD)),
    ),
    Sky(
        light = AccentColors(Color(0xFF1D6580), Color(0xFFFFFFFF), Color(0xFFC8EAF7), Color(0xFF00202C)),
        dark = AccentColors(Color(0xFF8ED2EE), Color(0xFF003546), Color(0xFF0B4D64), Color(0xFFC8EAF7)),
    ),
    Periwinkle(
        light = AccentColors(Color(0xFF4659AE), Color(0xFFFFFFFF), Color(0xFFDDE1FF), Color(0xFF00105C)),
        dark = AccentColors(Color(0xFFB9C3FF), Color(0xFF15277D), Color(0xFF2E3F94), Color(0xFFDDE1FF)),
    ),
    ;

    companion object {
        /** A stable blade for an item (list, card), so a collection shows the whole aperture. */
        fun forKey(key: Long): BrandAccent = entries[Math.floorMod(key, entries.size.toLong()).toInt()]
    }
}

internal data class AccentColors(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)

/** Set by [PicfortTheme]: whether the brand palette is in dark mode, or null with wallpaper colors. */
internal val LocalBrandDark = staticCompositionLocalOf<Boolean?> { null }

/** The scheme with [accent] as its primary and selection colors. */
fun ColorScheme.withAccent(accent: BrandAccent, dark: Boolean): ColorScheme {
    val c = if (dark) accent.dark else accent.light
    return copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.container,
        onPrimaryContainer = c.onContainer,
        // Segmented buttons, filter chips and navigation indicators select with secondaryContainer.
        secondaryContainer = c.container,
        onSecondaryContainer = c.onContainer,
        surfaceTint = c.accent,
    )
}

/**
 * Recolors [content] with [accent]. No-op for a null accent or with wallpaper colors
 * (Material You keeps the phone's colors).
 */
@Composable
fun AccentTheme(accent: BrandAccent?, content: @Composable () -> Unit) {
    val dark = LocalBrandDark.current
    if (accent == null || dark == null) {
        content()
        return
    }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.withAccent(accent, dark),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
