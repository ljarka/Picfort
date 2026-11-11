package com.github.ljarka.picfort.ui.frame

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.ljarka.picfort.ui.preferences.EncryptedPreferences
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val FRAME_URL = "FRAME_URL"
private const val PICFORT_API_KEY = "PICFORT_API_KEY"
private const val PICFORT_SERVER_PROMPTED = "PICFORT_SERVER_PROMPTED"

@Singleton
class FrameSettingsStore @Inject constructor(
    @EncryptedPreferences private val preferences: SharedPreferences,
) {
    var frameUrl: String
        get() = preferences.getString(FRAME_URL, "") ?: ""
        set(value) = preferences.edit { putString(FRAME_URL, value) }

    /** The server's PICFORT_API_KEY, or empty when it has none (home network only). */
    var apiKey: String
        get() = preferences.getString(PICFORT_API_KEY, "") ?: ""
        set(value) = preferences.edit { putString(PICFORT_API_KEY, value.trim()) }

    /** Whether the one-time "Picfort server" step was shown (skipped or saved). */
    var serverPrompted: Boolean
        get() = preferences.getBoolean(PICFORT_SERVER_PROMPTED, false)
        set(value) = preferences.edit { putBoolean(PICFORT_SERVER_PROMPTED, value) }

    fun hasData() = frameUrl.isNotEmpty()

    /** Show the first-run step: no address yet and never asked on this install. */
    val needsSetupStep: Boolean get() = !hasData() && !serverPrompted
}

/**
 * Accepts what people actually type ("192.168.0.105:5000", "frame.local:5000/")
 * and returns a normalized base URL, or null if it isn't a valid address.
 */
fun normalizeFrameUrl(input: String): HttpUrl? {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    return withScheme.toHttpUrlOrNull()?.takeIf { it.scheme == "http" || it.scheme == "https" }
}
