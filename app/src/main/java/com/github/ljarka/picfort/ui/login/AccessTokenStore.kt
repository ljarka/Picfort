package com.github.ljarka.picfort.ui.login

import android.content.SharedPreferences
import androidx.core.content.edit
import com.github.ljarka.picfort.ui.preferences.EncryptedPreferences
import com.github.ljarka.picfort.ui.preferences.PreferencesStore
import javax.inject.Inject
import javax.inject.Singleton

private const val ACCESS_TOKEN = "ACCESS_TOKEN"
private const val USER_ID = "USER_ID"
private const val USER_EMAIL = "USER_EMAIL"

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

    /** Immich user id of the logged-in account. */
    var userId: String
        get() = preferences.getString(USER_ID, "") ?: ""
        set(value) = preferences.edit { putString(USER_ID, value) }

    /** Email of the logged-in account, shown so people can tell which account they're on. */
    var userEmail: String
        get() = preferences.getString(USER_EMAIL, "") ?: ""
        set(value) = preferences.edit { putString(USER_EMAIL, value) }

    override fun hasData() = accessToken.isNotEmpty()
}