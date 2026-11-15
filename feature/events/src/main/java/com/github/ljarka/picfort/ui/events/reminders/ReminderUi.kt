package com.github.ljarka.picfort.ui.events.reminders

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.feature.events.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** The detail's reminder row (T-039): this phone's choice for the event and the default it falls back to. */
data class EventReminderUi(val choice: ReminderChoice, val defaults: ReminderDefaults)

fun reminderSummary(event: FamilyEvent, ui: EventReminderUi): String = when (val c = ui.choice) {
    ReminderChoice.Off -> "None"

    is ReminderChoice.Preset -> c.preset.label

    ReminderChoice.Default ->
        "Default · " +
            (ui.defaults.of(event.reminderKind())?.takeIf { it.fits(event) }?.label ?: "none")
}

@Composable
fun ReminderLine(event: FamilyEvent, ui: EventReminderUi, onChange: (ReminderChoice) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { picking = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_notifications),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Reminder", style = MaterialTheme.typography.bodyLarge)
            Text(
                reminderSummary(event, ui),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (picking) {
        val defaultLabel = ui.defaults.of(event.reminderKind())?.takeIf { it.fits(event) }?.label ?: "none"
        val options = listOf<Pair<String, ReminderChoice>>("Default ($defaultLabel)" to ReminderChoice.Default) +
            ReminderPreset.entries.filter { it.fits(event) }.map { it.label to ReminderChoice.Preset(it) } +
            ("None" to ReminderChoice.Off)
        ChoiceDialog(
            title = "Remind me",
            options = options,
            selected = ui.choice,
            onPick = {
                picking = false
                onChange(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<String, T>>,
    selected: T,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = value == selected, onClick = { onPick(value) })
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Asks for the notification permission (Android 13+) when it's missing; [onResult] gets the answer. */
@Composable
fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return remember(launcher) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !canPostNotifications(context)) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/** The Events tab asks once, so the default reminders can show without visiting Settings. */
@Composable
fun AskForNotificationsOnce() {
    val viewModel: ReminderSettingsViewModel = hiltViewModel()
    val ask = rememberNotificationPermissionRequest()
    LaunchedEffect(Unit) {
        if (!viewModel.store.askedPermission) {
            viewModel.store.askedPermission = true
            ask()
        }
    }
}

@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    val store: ReminderStore,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val defaults = store.defaults

    fun setDefault(kind: ReminderKind, preset: ReminderPreset?) = store.setDefault(kind, preset)

    fun exactAllowed() = scheduler.exactAllowed()
}

/** Settings section (T-039): the phone's default reminder per kind, and what keeps them from showing. */
@Composable
fun EventReminderSettings() {
    val viewModel: ReminderSettingsViewModel = hiltViewModel()
    val defaults by viewModel.defaults.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Re-read the permissions when coming back from the system settings.
    var resumed by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            resumed++
        }
    }
    val notificationsOn = remember(resumed) { canPostNotifications(context) }
    val exactOn = remember(resumed) { viewModel.exactAllowed() }
    val askNotifications = rememberNotificationPermissionRequest { granted ->
        if (!granted) {
            // Denied for good: only the system settings can turn it on.
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
        resumed++
    }
    EventReminderSettingsContent(
        defaults = defaults,
        notificationsOn = notificationsOn,
        exactOn = exactOn,
        onDefault = viewModel::setDefault,
        onAllowNotifications = askNotifications,
        onAllowExact = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                )
            }
        },
    )
}

@Composable
fun EventReminderSettingsContent(
    defaults: ReminderDefaults,
    notificationsOn: Boolean = true,
    exactOn: Boolean = true,
    onDefault: (ReminderKind, ReminderPreset?) -> Unit = { _, _ -> },
    onAllowNotifications: () -> Unit = {},
    onAllowExact: () -> Unit = {},
) {
    var picking by remember { mutableStateOf<ReminderKind?>(null) }
    Column {
        ReminderKind.entries.forEach { kind ->
            ListItem(
                modifier = Modifier.clickable { picking = kind },
                headlineContent = { Text(kind.label) },
                supportingContent = { Text(defaults.of(kind)?.label ?: "No reminder") },
            )
        }
        if (!notificationsOn) {
            ListItem(
                modifier = Modifier.clickable(onClick = onAllowNotifications),
                headlineContent = { Text("Notifications are off", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Tap to allow them, or reminders won't show") },
            )
        } else if (!exactOn) {
            ListItem(
                modifier = Modifier.clickable(onClick = onAllowExact),
                headlineContent = { Text("Exact times are off") },
                supportingContent = { Text("Reminders may come a few minutes late. Tap to allow alarms") },
            )
        }
    }
    picking?.let { kind ->
        ChoiceDialog(
            title = kind.label,
            options = ReminderPreset.entries.filter { it.fits(kind) }.map { it.label to it } + ("No reminder" to null),
            selected = defaults.of(kind),
            onPick = {
                onDefault(kind, it)
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}
