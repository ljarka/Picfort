package com.github.ljarka.picfort.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.core.model.ReceiptAiStatus
import com.github.ljarka.picfort.core.model.ReceiptAiStatusProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PhotoAccessLevel {
    Full,
    SelectedOnly,
    Denied,
}

data class ExperimentalFeaturesUi(
    val deviceLabel: String = "",
    val sdkInt: Int = 0,
    val receiptAiStatus: ReceiptAiStatus? = null,
    val aiCoreInstalled: Boolean = false,
    val appFunctionsServiceRegistered: Boolean = false,
    val appFunctionNames: List<String> = APP_FUNCTION_NAMES,
    val mediaLocationGranted: Boolean = false,
    val photoAccess: PhotoAccessLevel = PhotoAccessLevel.Denied,
    val exactAlarmsAllowed: Boolean = false,
) {
    val appFunctionsOsSupported: Boolean
        get() = sdkInt >= 36

    val wallpaperColorsSupported: Boolean
        get() = sdkInt >= Build.VERSION_CODES.S

    companion object {
        val APP_FUNCTION_NAMES = listOf(
            "getChildSchoolFinishTime",
            "getChildTimetable",
            "getUpcomingEvents",
            "getNextBirthday",
            "getShoppingLists",
            "getShoppingListItems",
            "addShoppingItem",
            "toggleShoppingItem",
            "getRecentWallNotes",
            "postWallNote",
            "getFamilyMembers",
        )
    }
}

@HiltViewModel
class ExperimentalFeaturesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val receiptAiStatusProvider: ReceiptAiStatusProvider,
) : ViewModel() {
    private val _ui = MutableStateFlow(readDeviceSnapshot())
    val ui: StateFlow<ExperimentalFeaturesUi> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.value = readDeviceSnapshot().copy(receiptAiStatus = null)
        viewModelScope.launch {
            val status = receiptAiStatusProvider.status()
            _ui.value = _ui.value.copy(receiptAiStatus = status)
        }
    }

    private fun readDeviceSnapshot(): ExperimentalFeaturesUi {
        val pm = context.packageManager
        val aiCoreInstalled = runCatching {
            pm.getPackageInfo("com.google.android.aicore", 0)
            true
        }.getOrDefault(false)
        val appFunctionsRegistered = runCatching {
            val intent = Intent("android.app.appfunctions.AppFunctionService").setPackage(context.packageName)
            pm.queryIntentServices(intent, 0).isNotEmpty()
        }.getOrDefault(false)
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val label = listOf(manufacturer, model).filter { it.isNotEmpty() }.joinToString(" ")
        return ExperimentalFeaturesUi(
            deviceLabel = label.ifEmpty { "Unknown device" },
            sdkInt = Build.VERSION.SDK_INT,
            aiCoreInstalled = aiCoreInstalled,
            appFunctionsServiceRegistered = appFunctionsRegistered,
            mediaLocationGranted = isMediaLocationGranted(),
            photoAccess = readPhotoAccessLevel(),
            exactAlarmsAllowed = isExactAlarmAllowed(),
        )
    }

    private fun isMediaLocationGranted(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun readPhotoAccessLevel(): PhotoAccessLevel = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> when {
            hasPermission(Manifest.permission.READ_MEDIA_IMAGES) -> PhotoAccessLevel.Full
            hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> PhotoAccessLevel.SelectedOnly
            else -> PhotoAccessLevel.Denied
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
            if (hasPermission(Manifest.permission.READ_MEDIA_IMAGES)) PhotoAccessLevel.Full else PhotoAccessLevel.Denied
        }

        else -> {
            if (hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                PhotoAccessLevel.Full
            } else {
                PhotoAccessLevel.Denied
            }
        }
    }

    private fun isExactAlarmAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

@Composable
fun ExperimentalFeaturesScreen(onBack: () -> Unit) {
    val viewModel: ExperimentalFeaturesViewModel = hiltViewModel()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    ExperimentalFeaturesContent(ui = ui, onBack = onBack, onRefresh = viewModel::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalFeaturesContent(ui: ExperimentalFeaturesUi, onBack: () -> Unit = {}, onRefresh: () -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Experimental features") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Device: ${ui.deviceLabel} (Android API ${ui.sdkInt})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ReceiptAiCard(ui)
            AppFunctionsCard(ui)
            PhotoBackupPermissionsCard(ui)
            ExactRemindersCard(ui)
            DynamicColorsCard(ui)
        }
    }
}

@Composable
private fun ReceiptAiCard(ui: ExperimentalFeaturesUi) {
    val (badgeText, badgeAvailable) = when (ui.receiptAiStatus) {
        null -> "Checking…" to false
        ReceiptAiStatus.Available -> "Available" to true
        ReceiptAiStatus.Downloadable -> "Model downloadable" to false
        ReceiptAiStatus.Downloading -> "Model downloading" to false
        ReceiptAiStatus.Unavailable -> "Not available" to false
    }
    FeatureStatusCard(
        title = "On-device receipt reading (Gemini Nano)",
        badgeText = badgeText,
        badgeAvailable = badgeAvailable,
        details = listOf(
            "Android AICore (com.google.android.aicore): " +
                if (ui.aiCoreInstalled) "installed" else "not installed",
            "ML Kit GenAI status: ${ui.receiptAiStatus?.name ?: "Checking"}",
        ),
        description = "Reads shop, date and total from receipt photos locally via Android AICore " +
            "(no cloud AI). When unavailable, receipt fields stay manual and AI UI is hidden.",
    )
}

@Composable
private fun AppFunctionsCard(ui: ExperimentalFeaturesUi) {
    val ready = ui.appFunctionsOsSupported && ui.appFunctionsServiceRegistered
    val badgeText = when {
        ready -> "Ready on OS"
        !ui.appFunctionsOsSupported -> "Requires Android 16+"
        else -> "Service not registered"
    }
    FeatureStatusCard(
        title = "Gemini Assistant (AppFunctions)",
        badgeText = badgeText,
        badgeAvailable = ready,
        details = listOf(
            "OS requirement: Android 16+ (API 36+) — current API ${ui.sdkInt}",
            "PicfortAppFunctionService: " +
                if (ui.appFunctionsServiceRegistered) "registered" else "not registered",
            "Exposed functions (${ui.appFunctionNames.size}): ${ui.appFunctionNames.joinToString(", ")}",
        ),
        description = "Exposes school timetables, events, shopping lists, Family Wall and family " +
            "members to Android AppFunctions. Can be tested via adb; invocation from the Gemini " +
            "app depends on Google's allowlist / Connected Apps support.",
    )
}

@Composable
private fun PhotoBackupPermissionsCard(ui: ExperimentalFeaturesUi) {
    val photoLabel = when (ui.photoAccess) {
        PhotoAccessLevel.Full -> "Full access"
        PhotoAccessLevel.SelectedOnly -> "Selected photos only"
        PhotoAccessLevel.Denied -> "Not granted"
    }
    val allReady = ui.photoAccess == PhotoAccessLevel.Full && ui.mediaLocationGranted
    val badgeText = when {
        allReady -> "Ready"
        ui.photoAccess == PhotoAccessLevel.SelectedOnly -> "Partial access"
        else -> "Missing permissions"
    }
    FeatureStatusCard(
        title = "Photo backup & original GPS metadata",
        badgeText = badgeText,
        badgeAvailable = allReady,
        details = listOf(
            "Gallery access: $photoLabel",
            "ACCESS_MEDIA_LOCATION (original EXIF/GPS): " +
                if (ui.mediaLocationGranted) "granted" else "not granted",
        ),
        description = "Full gallery access lets backup see all photos; ACCESS_MEDIA_LOCATION " +
            "(Android 10+) preserves original GPS data so checksums match Immich.",
    )
}

@Composable
private fun ExactRemindersCard(ui: ExperimentalFeaturesUi) {
    FeatureStatusCard(
        title = "Exact event reminders",
        badgeText = if (ui.exactAlarmsAllowed) "Allowed" else "Inexact only",
        badgeAvailable = ui.exactAlarmsAllowed,
        details = listOf(
            "SCHEDULE_EXACT_ALARM (Android 12+): " +
                if (ui.exactAlarmsAllowed) "granted" else "not granted",
        ),
        description = "Allows family event and birthday reminders to fire at the exact scheduled time.",
    )
}

@Composable
private fun DynamicColorsCard(ui: ExperimentalFeaturesUi) {
    FeatureStatusCard(
        title = "Wallpaper colors (Material You)",
        badgeText = if (ui.wallpaperColorsSupported) "Supported" else "Requires Android 12+",
        badgeAvailable = ui.wallpaperColorsSupported,
        details = listOf(
            "OS requirement: Android 12+ (API 31+) — current API ${ui.sdkInt}",
        ),
        description = "Enables the 'Colors from wallpaper' toggle in Appearance settings.",
    )
}

@Composable
private fun FeatureStatusCard(
    title: String,
    badgeText: String,
    badgeAvailable: Boolean,
    details: List<String>,
    description: String,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                StatusBadge(text = badgeText, available = badgeAvailable)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            details.forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, available: Boolean) {
    val container = if (available) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (available) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = container,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
