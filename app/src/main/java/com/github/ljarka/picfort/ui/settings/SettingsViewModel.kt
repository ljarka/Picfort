package com.github.ljarka.picfort.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.backup.BackupQueue
import com.github.ljarka.picfort.backup.BackupQueueState
import com.github.ljarka.picfort.backup.BackupScheduler
import com.github.ljarka.picfort.backup.BackupSettingsStore
import com.github.ljarka.picfort.backup.BackupStatus
import com.github.ljarka.picfort.backup.LocalMediaScanner
import com.github.ljarka.picfort.backup.NEEDS_HOME_NETWORK
import com.github.ljarka.picfort.core.database.BackupDao
import com.github.ljarka.picfort.core.network.FrameSettingsStore
import com.github.ljarka.picfort.core.network.ServerUrlStore
import com.github.ljarka.picfort.local.hasMediaAccess
import com.github.ljarka.picfort.network.SessionManager
import com.github.ljarka.picfort.ui.family.FamilyRepository
import com.github.ljarka.picfort.ui.timeline.TimelineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupSettingsUi(
    val enabled: Boolean,
    val wifiOnly: Boolean,
    val chargingOnly: Boolean,
    /** Immich's home-network address for backup, "" when not set (T-064). */
    val homeUrl: String = "",
    /** Files too large for the public address, waiting for the home one. */
    val waitingForHome: Int = 0,
    val backedUp: Int = 0,
    val onPhone: Int = 0,
    val lastResult: String? = null,
    val lastRunAt: Long = 0,
    val hasMediaAccess: Boolean = true,
    /** Number of selected folders; 0 means "all folders". */
    val selectedBucketCount: Int = 0,
)

data class MediaBucketUi(val id: String, val name: String, val mediaCount: Int, val selected: Boolean)

sealed interface FoldersState {
    data object Loading : FoldersState
    data class Loaded(val buckets: List<MediaBucketUi>) : FoldersState
}

/**
 * Phone items in Immich: those Picfort's backup linked, plus those the timeline matched to an
 * Immich asset (uploaded by another app, or before this backup ran; T-063).
 */
fun countInImmich(onPhone: Int, linked: Collection<Long>, onServer: Set<Long>?): Int =
    (linked.toSet() + onServer.orEmpty()).size.coerceAtMost(onPhone)

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
    private val timelineRepository: TimelineRepository,
    backupQueue: BackupQueue,
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

    /** What the running backup is uploading now and what's next (T-063). */
    val queue: StateFlow<BackupQueueState> = backupQueue.state

    init {
        reloadCounts()
        viewModelScope.launch { familyRepository.refresh() }
        viewModelScope.launch {
            if (hasMediaAccess(context)) timelineRepository.loadPhoneMediaIfNeeded()
            // The timeline's match changes with each sync and backup: recount.
            timelineRepository.phoneOnServer.distinctUntilChanged().collect { reloadCounts() }
        }
    }

    private val _folders = MutableStateFlow<FoldersState?>(null)
    val folders: StateFlow<FoldersState?> = _folders.asStateFlow()

    private fun read() = BackupSettingsUi(
        enabled = settings.enabled,
        wifiOnly = settings.wifiOnly,
        chargingOnly = settings.chargingOnly,
        homeUrl = settings.homeUrl,
        lastResult = settings.lastResult,
        lastRunAt = settings.lastRunAt,
        hasMediaAccess = hasMediaAccess(context),
        selectedBucketCount = settings.selectedBucketIds.size,
    )

    /** Counts and the last result change while a backup runs; called when status changes. */
    fun reloadCounts() {
        viewModelScope.launch {
            val bucketFilter = settings.selectedBucketIds.takeIf { it.isNotEmpty() }
            val onPhone = if (hasMediaAccess(context)) scanner.count(bucketFilter) else 0
            val onServer = timelineRepository.phoneOnServer.first()
            val backedUp = countInImmich(onPhone, backupDao.linkedLocalIds(), onServer)
            val waiting = backupDao.countRejected(NEEDS_HOME_NETWORK)
            _backup.value = read().copy(onPhone = onPhone, backedUp = backedUp, waitingForHome = waiting)
        }
    }

    /** Loads available MediaStore buckets; call when the folder-selection dialog opens. */
    fun loadFolders() {
        if (_folders.value is FoldersState.Loaded) return
        _folders.value = FoldersState.Loading
        viewModelScope.launch {
            val selected = settings.selectedBucketIds
            val buckets = scanner.listBuckets().map { b ->
                MediaBucketUi(b.id, b.name, b.mediaCount, selected.isEmpty() || b.id in selected)
            }
            _folders.value = FoldersState.Loaded(buckets)
        }
    }

    fun toggleFolder(bucketId: String) {
        val current = (_folders.value as? FoldersState.Loaded) ?: return
        val updated = current.buckets.map { if (it.id == bucketId) it.copy(selected = !it.selected) else it }
        _folders.value = FoldersState.Loaded(updated)
        val allSelected = updated.all { it.selected }
        settings.selectedBucketIds =
            if (allSelected) emptySet() else updated.filter { it.selected }.map { it.id }.toSet()
        _backup.update { it.copy(selectedBucketCount = settings.selectedBucketIds.size) }
        // force reload on next open so counts refresh
        if (!allSelected) _folders.value = FoldersState.Loaded(updated)
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

    fun setHomeUrl(value: String) {
        settings.homeUrl = value
        _backup.update { it.copy(homeUrl = settings.homeUrl) }
    }

    fun stopBackup() {
        scheduler.stop()
        reloadCounts()
    }

    fun logout() {
        scheduler.cancelAll()
        sessionManager.logout()
    }
}
