package com.github.ljarka.picfort.ui.settings

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Appearance choices; observed by `MainActivity` so a change recolors the app at once. */
@Singleton
class AppearanceSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _wallpaperColors = MutableStateFlow(prefs.getBoolean(KEY_WALLPAPER_COLORS, false))

    /**
     * Material You colors from the wallpaper instead of Picfort's palette. Off by default:
     * the app should look like its icon on every phone.
     */
    val wallpaperColors: StateFlow<Boolean> = _wallpaperColors.asStateFlow()

    /** Wallpaper colors exist from Android 12. */
    val wallpaperColorsAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun setWallpaperColors(value: Boolean) {
        prefs.edit { putBoolean(KEY_WALLPAPER_COLORS, value) }
        _wallpaperColors.value = value
    }

    private companion object {
        const val KEY_WALLPAPER_COLORS = "wallpaperColors"
    }
}
