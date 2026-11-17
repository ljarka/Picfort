package com.github.ljarka.picfort.ui.schooljournal

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.avatar.MemberCarousel
import com.github.ljarka.picfort.core.ui.avatar.avatarColor
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.delay

/** Everything the screen can ask for. */
data class SchoolJournalActions(
    val onBack: () -> Unit = {},
    val onAddressChange: (String) -> Unit = {},
    val onKeyChange: (String) -> Unit = {},
    val onConnect: () -> Unit = {},
    val onNotificationsChange: (Boolean) -> Unit = {},
    val onCancelSettings: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onShowSettings: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onSelectChild: (String?) -> Unit = {},
    val onSelectTab: (JournalTab) -> Unit = {},
    val onOpenMessage: (String) -> Unit = {},
    val onLoadMoreMessages: () -> Unit = {},
    val onSelectKind: (MessageKind?) -> Unit = {},
    val onToggleShowRemoved: () -> Unit = {},
    val onMarkRead: (MessageSummary) -> Unit = {},
    val onMarkUnread: (MessageSummary) -> Unit = {},
    val onMarkAllRead: () -> Unit = {},
    val onSnackbarShown: () -> Unit = {},
    val onSelectSemester: (Int?) -> Unit = {},
    val onSyncNow: () -> Unit = {},
)

/** How times are shown; fixed in screenshot tests. */
data class JournalClock(
    val now: Instant,
    val zone: ZoneId = ZoneId.systemDefault(),
    val locale: Locale = Locale.ENGLISH,
    val is24Hour: Boolean = true,
)

/** The school journal (spec §3, §4): configuration until connected, then the content. */
@Composable
fun SchoolJournalScreen(onBack: () -> Unit, onOpenMessage: (String) -> Unit, initialTab: JournalTab? = null) {
    val viewModel = hiltViewModel<SchoolJournalViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(initialTab) { if (initialTab != null) viewModel.selectTab(initialTab) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.refresh() }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(context)
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onNotificationsChange(true)
        } else {
            viewModel.onNotificationsChange(
                false,
                deniedHint = "Notifications permission is off. Allow notifications in Android Settings.",
            )
        }
    }
    fun ensureNotificationsPermission(enabled: Boolean) {
        if (
            enabled &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            !canPostJournalNotifications(context)
        ) {
            viewModel.onNotificationsChange(true)
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onNotificationsChange(enabled)
        }
    }
    // "12 min ago" keeps up with the clock.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = Instant.now()
        }
    }
    SchoolJournalContent(
        state = state,
        clock = JournalClock(now = now, locale = locale, is24Hour = is24Hour),
        actions = SchoolJournalActions(
            onBack = onBack,
            onAddressChange = viewModel::onAddressChange,
            onKeyChange = viewModel::onKeyChange,
            onConnect = {
                if (
                    state.form.notifications &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    !canPostJournalNotifications(context)
                ) {
                    askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                viewModel.connect()
            },
            onNotificationsChange = ::ensureNotificationsPermission,
            onCancelSettings = viewModel::cancelSettings,
            onDisconnect = viewModel::disconnect,
            onShowSettings = { viewModel.showSettings() },
            onRefresh = { viewModel.refresh(full = true) },
            onSelectChild = viewModel::selectChild,
            onSelectTab = viewModel::selectTab,
            onOpenMessage = onOpenMessage,
            onLoadMoreMessages = viewModel::loadMoreMessages,
            onSelectKind = viewModel::selectKind,
            onToggleShowRemoved = viewModel::toggleShowRemoved,
            onMarkRead = viewModel::markRead,
            onMarkUnread = viewModel::markUnread,
            onMarkAllRead = viewModel::markAllRead,
            onSnackbarShown = viewModel::snackbarShown,
            onSelectSemester = viewModel::selectSemester,
            onSyncNow = viewModel::syncNow,
        ),
    )
}

@Composable
fun SchoolJournalContent(
    state: SchoolJournalUi,
    clock: JournalClock,
    actions: SchoolJournalActions = SchoolJournalActions(),
) {
    when (state.mode) {
        JournalMode.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        JournalMode.SETUP -> SchoolJournalSetup(state.form, state.status, clock, actions)

        JournalMode.CONTENT -> JournalShell(state, clock, actions)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JournalShell(state: SchoolJournalUi, clock: JournalClock, actions: SchoolJournalActions) {
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbar.showSnackbar(it)
            actions.onSnackbarShown()
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("School journal") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.syncing) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = actions.onSyncNow) {
                            Icon(Icons.Default.Refresh, contentDescription = "Check the e-journal now")
                        }
                    }
                    Box {
                        IconButton(onClick = {
                            menu = true
                        }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Mark all as read") },
                                onClick = {
                                    menu = false
                                    actions.onMarkAllRead()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Show removed messages") },
                                trailingIcon = {
                                    if (state.messages.showRemoved) Icon(Icons.Default.Check, contentDescription = "On")
                                },
                                onClick = {
                                    menu = false
                                    actions.onToggleShowRemoved()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Server settings") },
                                onClick = {
                                    menu = false
                                    actions.onShowSettings()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.showChildChips) {
                ChildCarousel(state.children, state.selectedChild, actions.onSelectChild)
            }
            state.banner?.let { JournalBannerCard(it, state.cachedAt, clock) }
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                JournalTab.entries.forEachIndexed { index, tab ->
                    val dot = when (tab) {
                        JournalTab.MESSAGES -> state.news.unreadMessages
                        JournalTab.GRADES -> state.news.unseenGrades
                    }
                    SegmentedButton(
                        selected = state.tab == tab,
                        onClick = { actions.onSelectTab(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = JournalTab.entries.size),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tab.title)
                            if (dot) {
                                Spacer(Modifier.width(6.dp))
                                Badge(containerColor = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (state.tab) {
                    JournalTab.MESSAGES -> MessagesTab(state, clock, actions)
                    JournalTab.GRADES -> GradesTab(state, clock, actions)
                }
            }
        }
    }
}

/**
 * Amber for "data older than an hour", apart from the error red of "last check failed"; the brand
 * palette's tertiary is too close to that red (T-076).
 */
@Composable
fun warningColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFFB951) else Color(0xFF8A5A00)

/** "Last successful sync: …" plus "· last check failed" in the error color (spec §4.1). */
@Composable
fun SyncLineText(
    line: SyncLine,
    modifier: Modifier = Modifier,
    color: Color = if (line.stale) warningColor() else MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val base = color
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = base)) { append(line.text) }
            if (line.failed) {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
                    append(" · last check failed")
                }
            }
        },
        style = style,
        modifier = modifier,
    )
}

@Composable
private fun JournalBannerCard(banner: JournalBanner, cachedAt: Instant?, clock: JournalClock) {
    val text = if (banner == JournalBanner.OFFLINE && cachedAt != null) {
        "Offline. Showing what was loaded ${whenText(
            cachedAt,
            clock.now,
            clock.zone,
            clock.locale,
            clock.is24Hour,
            withToday = true,
        )}."
    } else {
        banner.text
    }
    val error = banner == JournalBanner.AUTH_FAILED || banner == JournalBanner.STORAGE
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (error) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (error) colors.onErrorContainer else colors.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun ChildCarousel(children: List<ChildAccount>, selected: String?, onSelect: (String?) -> Unit) {
    val active = selected?.takeIf { id -> children.any { it.id == id } } ?: children.firstOrNull()?.id
    val members = remember(children) {
        children.mapIndexed { index, child ->
            FamilyMember(
                id = child.id,
                name = child.name.ifEmpty { child.login },
                avatarUrl = null,
                color = avatarColor(null, fallbackIndex = index),
            )
        }
    }
    MemberCarousel(
        members = members,
        selectedId = active,
        onSelect = { id -> if (id != null) onSelect(id) },
        onEditMember = null,
    )
}

/** The status sheet (spec §4.1), also part of Server settings. */
@Composable
fun StatusDetails(status: JournalStatus, clock: JournalClock, address: String?) {
    fun at(text: String?): String = instantOrNull(text)?.let {
        whenText(it, clock.now, clock.zone, clock.locale, clock.is24Hour, withToday = true)
    } ?: "—"
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("School server", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SyncLineText(syncLine(status, clock.now, clock.zone, clock.locale, clock.is24Hour))
        Spacer(Modifier.height(12.dp))
        val login = when (status.auth) {
            "ok" -> "OK"
            "auth_failed" -> "Failed"
            else -> "Not checked yet"
        }
        StatusRow("E-journal login", login)
        status.lastSync?.let { run ->
            StatusRow("Last check started", at(run.startedAt))
            StatusRow("Last check finished", at(run.finishedAt))
            val result = when (run.status) {
                "ok" -> "OK"
                "running" -> "Running"
                "auth_failed" -> "Login failed"
                "error" -> "Failed" + (run.errorCode?.let { " ($it)" } ?: "")
                else -> run.status
            }
            StatusRow("Last check result", result)
            StatusRow("Items read · changes", "${run.itemsFetched} · ${run.changesDetected}")
        }
        StatusRow("Last successful sync", at(status.lastSuccessfulSyncAt))
        StatusRow("Next check", at(status.nextSyncAt))
        StatusRow("Grades · messages", "${status.counts.grades} · ${status.counts.messages}")
        StatusRow("Server version", status.version.ifEmpty { "—" })
        address?.let { StatusRow("Address", it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(horizontal = 8.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}
