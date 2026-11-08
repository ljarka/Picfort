package com.github.ljarka.immich.android.ui.frame

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.ljarka.immich.android.ui.preferences.EncryptedPreferences
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val FRAME_URL = "FRAME_URL"

@Singleton
class FrameSettingsStore @Inject constructor(
    @EncryptedPreferences private val preferences: SharedPreferences,
) {
    var frameUrl: String
        get() = preferences.getString(FRAME_URL, "") ?: ""
        set(value) = preferences.edit { putString(FRAME_URL, value) }

    fun hasData() = frameUrl.isNotEmpty()
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
