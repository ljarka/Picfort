package com.github.ljarka.picfort.core.network

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Singleton

private const val SERVER_URL = "SERVER_URL"

@Singleton
class ServerUrlStore @Inject constructor(
    @EncryptedPreferences private val preferences: SharedPreferences,
) {
    var serverUrl: String = ""
        set(value) {
            preferences.edit { putString(SERVER_URL, value) }
            field = value
        }
        get() {
            return preferences.getString(SERVER_URL, "") ?: ""
        }

    fun hasData() = serverUrl.isNotEmpty()
}
