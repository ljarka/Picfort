package com.github.ljarka.picfort.ui.login

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.ljarka.picfort.ui.preferences.EncryptedPreferences
import com.github.ljarka.picfort.ui.preferences.PreferencesStore
import javax.inject.Inject
import javax.inject.Singleton

private const val ACCESS_TOKEN = "ACCESS_TOKEN"

@Singleton
class AccessTokenStore @Inject constructor(
    @EncryptedPreferences private val preferences: SharedPreferences,
) : PreferencesStore {

    var accessToken: String = ""
        set(value) {
            preferences.edit { putString(ACCESS_TOKEN, value) }
            field = value
        }
        get() {
            return preferences.getString(ACCESS_TOKEN, "") ?: ""
        }

    override fun hasData() = accessToken.isNotEmpty()
}