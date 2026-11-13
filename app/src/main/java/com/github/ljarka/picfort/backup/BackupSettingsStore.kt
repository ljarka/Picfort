package com.github.ljarka.picfort.backup

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupSettingsStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("backup", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifiOnly", true)
        set(value) = prefs.edit { putBoolean("wifiOnly", value) }

    var chargingOnly: Boolean
        get() = prefs.getBoolean("chargingOnly", false)
        set(value) = prefs.edit { putBoolean("chargingOnly", value) }

    /** Sent as deviceId to Immich < v3. */
    val deviceId: String
        get() = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString("deviceId", it) }
        }

    /** Human-readable result of the last backup run, e.g. "Uploaded 12". */
    var lastResult: String?
        get() = prefs.getString("lastResult", null)
        set(value) = prefs.edit { putString("lastResult", value) }

    var lastRunAt: Long
        get() = prefs.getLong("lastRunAt", 0)
        set(value) = prefs.edit { putLong("lastRunAt", value) }

    /** "server|userId" the local→remote mappings belong to; they're reset when it changes. */
    var mappingsOwner: String?
        get() = prefs.getString("mappingsOwner", null)
        set(value) = prefs.edit { putString("mappingsOwner", value) }
}
