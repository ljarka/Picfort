package com.github.ljarka.picfort.ui.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.defaultPreferences: DataStore<Preferences> by preferencesDataStore(name = "Default")