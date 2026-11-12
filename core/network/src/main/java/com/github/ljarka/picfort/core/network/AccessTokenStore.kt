package com.github.ljarka.picfort.core.network

import javax.inject.Inject
import javax.inject.Singleton

private const val ACCESS_TOKEN = "ACCESS_TOKEN"
private const val USER_ID = "USER_ID"
private const val USER_EMAIL = "USER_EMAIL"

@Singleton
class AccessTokenStore @Inject constructor(
    private val securePreferences: SecurePreferencesStore,
) {
    var accessToken: String
        get() = securePreferences.getString(ACCESS_TOKEN)
        set(value) = securePreferences.putString(ACCESS_TOKEN, value)

    /** Immich user id of the logged-in account. */
    var userId: String
        get() = securePreferences.getString(USER_ID)
        set(value) = securePreferences.putString(USER_ID, value)

    /** Email of the logged-in account, shown so people can tell which account they're on. */
    var userEmail: String
        get() = securePreferences.getString(USER_EMAIL)
        set(value) = securePreferences.putString(USER_EMAIL, value)

    fun hasData(): Boolean = accessToken.isNotEmpty()
}
