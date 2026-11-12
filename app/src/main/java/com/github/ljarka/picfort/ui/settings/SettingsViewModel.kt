package com.github.ljarka.picfort.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.backup.BackupScheduler
import com.github.ljarka.picfort.backup.BackupSettingsStore
import com.github.ljarka.picfort.backup.BackupStatus
import com.github.ljarka.picfort.backup.LocalMediaScanner
import com.github.ljarka.picfort.core.database.BackupDao
import com.github.ljarka.picfort.core.network.FrameSettingsStore
import com.github.ljarka.picfort.core.network.ServerUrlStore
import com.github.ljarka.picfort.local.hasMediaAccess
import com.github.ljarka.picfort.network.SessionManager
import com.github.ljarka.picfort.ui.family.FamilyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupSettingsUi(
    val enabled: Boolean,
    val wifiOnly: Boolean,
    val chargingOnly: Boolean,
    val backedUp: Int = 0,
    val onPhone: Int = 0,
    val lastResult: String? = null,
    val lastRunAt: Long = 0,
    val hasMediaAccess: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: BackupSettingsStore,
    private val scheduler: BackupScheduler,
    private val scanner: LocalMediaScanner,
    private val backupDao: BackupDao,
    private val serverUrlStore: ServerUrlStore,
    private val frameSettingsStore: FrameSettingsStore,
    private val familyRepository: FamilyRepository,
    private val sessionManager: SessionManager,
    private val appearance: AppearanceSettingsStore,
) : ViewModel() {

    /** null when the phone has no wallpaper colors (Android < 12): the row is hidden. */
    val wallpaperColors: StateFlow<Boolean?> =
        if (appearance.wallpaperColorsAvailable) appearance.wallpaperColors else MutableStateFlow(null)

    fun setWallpaperColors(value: Boolean) = appearance.setWallpaperColors(value)

    private val _backup = MutableStateFlow(read())
    val backup: StateFlow<BackupSettingsUi> = _backup.asStateFlow()

    val status: StateFlow<BackupStatus> =
        scheduler.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupStatus.Idle)

    val showFamily: StateFlow<Boolean> = familyRepository.supported

    val serverUrl: String get() = serverUrlStore.serverUrl

    /** The Picfort server (frame + Family Wall), or "" when not set. */
    val picfortServer: String get() = frameSettingsStore.frameUrl

    init {
        reloadCounts()
        viewModelScope.launch { familyRepository.refresh() }
    }

    private fun read() = BackupSettingsUi(
        enabled = settings.enabled,
        wifiOnly = settings.wifiOnly,
        chargingOnly = settings.chargingOnly,
        lastResult = settings.lastResult,
        lastRunAt = settings.lastRunAt,
        hasMediaAccess = hasMediaAccess(context),
    )

    /** Counts and the last result change while a backup runs; called when status changes. */
    fun reloadCounts() {
        viewModelScope.launch {
            val onPhone = if (hasMediaAccess(context)) scanner.count() else 0
            val backedUp = backupDao.countBackedUp()
            _backup.value = read().copy(onPhone = onPhone, backedUp = backedUp.coerceAtMost(onPhone))
        }
    }

    fun setEnabled(value: Boolean) {
        settings.enabled = value
        scheduler.apply()
        if (value) scheduler.runNow() // start right away instead of in up to 6 hours
        _backup.update { it.copy(enabled = value) }
    }

    fun setWifiOnly(value: Boolean) {
        settings.wifiOnly = value
        scheduler.apply()
        _backup.update { it.copy(wifiOnly = value) }
    }

    fun setChargingOnly(value: Boolean) {
        settings.chargingOnly = value
        scheduler.apply()
        _backup.update { it.copy(chargingOnly = value) }
    }

    fun backUpNow() = scheduler.runNow()

    fun logout() {
        scheduler.cancelAll()
        sessionManager.logout()
    }
}
