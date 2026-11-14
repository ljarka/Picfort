package com.github.ljarka.picfort.ui.settings

import android.Manifest
import android.os.Build
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.ljarka.picfort.backup.BackupQueueState
import com.github.ljarka.picfort.backup.BackupStatus
import com.github.ljarka.picfort.local.RequestMediaPermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onFrameClick: () -> Unit, onFamilyClick: () -> Unit = {}) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val backup by viewModel.backup.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val showFamily by viewModel.showFamily.collectAsStateWithLifecycle()
    val wallpaperColors by viewModel.wallpaperColors.collectAsStateWithLifecycle()

    LaunchedEffect(status) { viewModel.reloadCounts() }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* backup works either way; this only shows its progress */ }
    val onEnable: (Boolean) -> Unit = { enabled ->
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.setEnabled(enabled)
    }
    // Asks only for what's missing: photos, or (T-067) their locations.
    RequestMediaPermission(onGranted = viewModel::reloadCounts)
    SettingsContent(
        backup = backup,
        status = status,
        queue = queue,
        serverUrl = viewModel.serverUrl,
        picfortServer = viewModel.picfortServer,
        showFamily = showFamily,
        wallpaperColors = wallpaperColors,
        onWallpaperColors = viewModel::setWallpaperColors,
        onBack = onBack,
        onFrameClick = onFrameClick,
        onFamilyClick = onFamilyClick,
        onEnable = onEnable,
        onWifiOnly = viewModel::setWifiOnly,
        onChargingOnly = viewModel::setChargingOnly,
        onBackUpNow = viewModel::backUpNow,
        onStopBackup = viewModel::stopBackup,
        onHomeUrl = viewModel::setHomeUrl,
        onLogout = viewModel::logout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    backup: BackupSettingsUi,
    status: BackupStatus,
    serverUrl: String,
    /** What the running backup uploads now and next; empty when idle. */
    queue: BackupQueueState = BackupQueueState(),
    /** Picfort server address, "" when not set. */
    picfortServer: String = "",
    showFamily: Boolean = false,
    /** "Colors from wallpaper" switch state; null hides it (Android < 12). */
    wallpaperColors: Boolean? = null,
    onWallpaperColors: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
    onFrameClick: () -> Unit = {},
    onFamilyClick: () -> Unit = {},
    onEnable: (Boolean) -> Unit = {},
    onWifiOnly: (Boolean) -> Unit = {},
    onChargingOnly: (Boolean) -> Unit = {},
    onBackUpNow: () -> Unit = {},
    onStopBackup: () -> Unit = {},
    onHomeUrl: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    /** Fixed "now" for screenshot tests. */
    now: Long = System.currentTimeMillis(),
    relativeTimeSpan: (lastRunAt: Long, now: Long) -> CharSequence =
        { lastRunAt, currentNow ->
            DateUtils.getRelativeTimeSpanString(lastRunAt, currentNow, DateUtils.MINUTE_IN_MILLIS)
        },
) {
    var confirmLogout by remember { mutableStateOf(false) }
    var editHome by remember { mutableStateOf(false) }
    if (editHome) {
        HomeAddressDialog(
            current = backup.homeUrl,
            onSave = {
                onHomeUrl(it)
                editHome = false
            },
            onDismiss = { editHome = false },
        )
    }
    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Log out?") },
            text = { Text("Backup stops until you log in again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("Log out") }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Backup")
            SwitchRow("Back up this phone", "Photos and videos are uploaded to Immich", backup.enabled, onEnable)
            SwitchRow("Only on Wi-Fi", null, backup.wifiOnly, onWifiOnly)
            SwitchRow("Only while charging", null, backup.chargingOnly, onChargingOnly)
            ListItem(
                modifier = Modifier.clickable { editHome = true },
                headlineContent = { Text("Home network address") },
                supportingContent = {
                    Text(
                        backup.homeUrl.ifBlank { "Not set: large videos may not get through the public address" },
                    )
                },
            )

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                when (val s = status) {
                    is BackupStatus.Running -> {
                        val done = backupDone(s, queue)
                        Text(
                            if (s.total > 0) "Backing up… $done of ${s.total}" else "Preparing backup…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (s.total > 0) {
                            LinearProgressIndicator(
                                progress = { done.toFloat() / s.total },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        }
                    }

                    is BackupStatus.Waiting -> Text(s.reason, style = MaterialTheme.typography.bodyMedium)

                    BackupStatus.Idle -> Text(
                        "${backup.backedUp} of ${backup.onPhone} phone items are in Immich",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (backup.waitingForHome > 0) {
                    Text(
                        "${backup.waitingForHome} over 100 MB wait for the home network",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                backup.lastResult?.let {
                    val ago = relativeTimeSpan(backup.lastRunAt, now)
                    Text("Last backup $ago: $it", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = onBackUpNow,
                        enabled = status !is BackupStatus.Running && backup.hasMediaAccess,
                    ) { Text("Back up now") }
                    if (status !is BackupStatus.Idle) OutlinedButton(onClick = onStopBackup) { Text("Stop") }
                }
            }
            BackupQueueRow(queue, Modifier.padding(bottom = 8.dp))

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Picfort server")
            ListItem(
                modifier = Modifier.clickable(onClick = onFrameClick),
                headlineContent = { Text(picfortServer.ifEmpty { "Not set" }) },
                supportingContent = { Text("Picture frame, slideshow and the Family Wall") },
            )
            if (showFamily) {
                ListItem(
                    modifier = Modifier.clickable(onClick = onFamilyClick),
                    headlineContent = { Text("Family") },
                    supportingContent = { Text("Names, photos and hidden accounts") },
                )
            }

            if (wallpaperColors != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle("Appearance")
                SwitchRow(
                    "Colors from wallpaper",
                    "Use your phone's wallpaper colors instead of Picfort's",
                    wallpaperColors,
                    onWallpaperColors,
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Account")
            ListItem(headlineContent = { Text("Server") }, supportingContent = { Text(serverUrl) })
            ListItem(
                modifier = Modifier.clickable { confirmLogout = true },
                headlineContent = { Text("Log out", color = MaterialTheme.colorScheme.error) },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

/**
 * Files done: the worker reports once per batch of 50, the queue after every file (T-063), so
 * whichever is further on.
 */
fun backupDone(status: BackupStatus.Running, queue: BackupQueueState): Int =
    if (queue.pending.isEmpty()) status.done else maxOf(status.done, status.total - queue.pending.size)
