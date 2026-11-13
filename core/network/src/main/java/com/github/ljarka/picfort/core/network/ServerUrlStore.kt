package com.github.ljarka.picfort.core.network

import javax.inject.Inject
import javax.inject.Singleton

private const val SERVER_URL = "SERVER_URL"

@Singleton
class ServerUrlStore @Inject constructor(private val securePreferences: SecurePreferencesStore) {
    var serverUrl: String
        get() = securePreferences.getString(SERVER_URL)
        set(value) = securePreferences.putString(SERVER_URL, value)

    fun hasData() = serverUrl.isNotEmpty()
}
