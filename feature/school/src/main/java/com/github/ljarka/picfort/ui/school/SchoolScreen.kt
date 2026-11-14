package com.github.ljarka.picfort.ui.school

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.avatar.MemberCarousel
import com.github.ljarka.picfort.core.ui.avatar.avatarColor
import com.github.ljarka.picfort.core.ui.avatar.initials
import com.github.ljarka.picfort.core.ui.component.ServerRequiredContent
import com.github.ljarka.picfort.core.ui.component.SettingsAction
import com.github.ljarka.picfort.core.ui.component.aboveFloatingTabBar
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/** The School tab (T-029): one child's timetable, today's summary, the week day by day. */
@Composable
fun SchoolScreen(onSettingsClick: () -> Unit, onSetUpServer: () -> Unit = onSettingsClick) {
    val viewModel = hiltViewModel<SchoolViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { viewModel.tick() }
            viewModel.keepFresh()
        }
    }
    val context = LocalContext.current
    SchoolContent(
        state = state,
        locale = LocalConfiguration.current.locales[0],
        is24Hour = DateFormat.is24HourFormat(context),
        actions = SchoolActions(
            onSelectChild = viewModel::selectChild,
            onSelectDay = viewModel::selectDay,
            onToggleEditing = viewModel::toggleEditing,
            onRefresh = viewModel::refreshNow,
            onMessageShown = viewModel::messageShown,
            onSettingsClick = onSettingsClick,
            onSetUpServer = onSetUpServer,
            onAddChild = viewModel::addChild,
            onUpdateChild = viewModel::updateChild,
            onDeleteChild = viewModel::deleteChild,
            onSavePeriods = viewModel::savePeriods,
            onCopyPeriods = viewModel::copyPeriods,
            onSaveEntry = viewModel::saveEntry,
            onDeleteEntry = viewModel::deleteEntry,
            onCopyDay = viewModel::copyDay,
        ),
    )
}

/** Everything the screen can ask for; `done` closes the form once the server has the change. */
data class SchoolActions(
    val onSelectChild: (Long) -> Unit = {},
    val onSelectDay: (DayOfWeek) -> Unit = {},
    val onToggleEditing: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onSettingsClick: () -> Unit = {},
    val onSetUpServer: () -> Unit = {},
    val onAddChild: (member: FamilyMember, done: () -> Unit) -> Unit = { _, _ -> },
    val onUpdateChild: (id: Long, name: String, color: String, done: () -> Unit) -> Unit = { _, _, _, _ -> },
    val onDeleteChild: (id: Long, done: () -> Unit) -> Unit = { _, _ -> },
    val onSavePeriods: (childId: Long, periods: List<Period>, done: () -> Unit) -> Unit = { _, _, _ -> },
    val onCopyPeriods: (childId: Long, fromChild: Long, done: () -> Unit) -> Unit = { _, _, _ -> },
    val onSaveEntry: (childId: Long, entryId: Long?, draft: EntryDraft, done: () -> Unit) -> Unit = { _, _, _, _ -> },
    val onDeleteEntry: (childId: Long, entryId: Long, done: () -> Unit) -> Unit = { _, _, _ -> },
    val onCopyDay: (childId: Long, from: DayOfWeek, to: List<DayOfWeek>, done: () -> Unit) -> Unit = { _, _, _, _ -> },
)

/** Which form is open over the timetable. */
private sealed interface Sheet {
    data class ChildSheet(val child: Child) : Sheet

    /** Pick who goes to school from the family. */
    data object AddChild : Sheet

    data class EntrySheet(val entry: SchoolEntry?, val day: DayOfWeek) : Sheet

    data object Bells : Sheet

    data class CopyDay(val from: DayOfWeek) : Sheet
}

/** Time as the phone shows it: 13:40 or 1:40 PM. */
fun timeFormatter(is24Hour: Boolean, locale: Locale): (LocalTime) -> String {
    val format = DateTimeFormatter.ofPattern(if (is24Hour) "H:mm" else "h:mm a", locale)
    return { it.format(format) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolContent(
    state: SchoolUi,
    locale: Locale = Locale.ENGLISH,
    is24Hour: Boolean = true,
    actions: SchoolActions = SchoolActions(),
) {
    val time = remember(is24Hour, locale) { timeFormatter(is24Hour, locale) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val child = state.selected
    val editing = state.editing && state.canEdit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("School") },
                actions = {
                    if (state.configured && child != null && state.canEdit) {
                        IconButton(onClick = actions.onToggleEditing) {
                            if (editing) {
                                Icon(Icons.Default.Done, contentDescription = "Done editing")
                            } else {
                                Icon(Icons.Default.Edit, contentDescription = "Edit timetable")
                            }
                        }
                    }
                    SettingsAction(actions.onSettingsClick)
                },
            )
        },
        floatingActionButton = {
            if (editing && child != null) {
                FloatingActionButton(
                    onClick = { sheet = Sheet.EntrySheet(null, state.day) },
                    modifier = Modifier.aboveFloatingTabBar(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add lesson or activity")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.configured -> ServerRequiredContent(
                    "Connect your Picfort server to use school timetables.",
                    actions.onSetUpServer,
                )

                state.isLoading && state.children.isEmpty() -> Centered { CircularProgressIndicator() }

                state.loadError != null -> Centered {
                    Text(state.loadError, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = actions.onRefresh) { Text("Retry") }
                }

                else -> {
                    state.offlineSince?.let { OfflineBanner(it) }
                    if (child == null) {
                        NoChildren(state, onAdd = { sheet = Sheet.AddChild })
                    } else {
                        Timetable(
                            state = state,
                            child = child,
                            editing = editing,
                            locale = locale,
                            time = time,
                            actions = actions,
                            onEditChild = { sheet = Sheet.ChildSheet(it) },
                            onAddChild = { sheet = Sheet.AddChild },
                            onOpenEntry = { sheet = Sheet.EntrySheet(it, it.weekday) },
                            onCopyDay = { sheet = Sheet.CopyDay(it) },
                        )
                    }
                }
            }
        }
    }

    sheet?.let { open ->
        SchoolSheet(open, state, locale, time, actions, onOpen = { sheet = it }, onClose = { sheet = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchoolSheet(
    open: Sheet,
    state: SchoolUi,
    locale: Locale,
    time: (LocalTime) -> String,
    actions: SchoolActions,
    onOpen: (Sheet) -> Unit,
    onClose: () -> Unit,
) {
    val child = state.selected
    if (open is Sheet.CopyDay) {
        if (child != null) {
            CopyDayDialog(
                child = child,
                from = open.from,
                locale = locale,
                onDismiss = onClose,
                onCopy = { days -> actions.onCopyDay(child.id, open.from, days, onClose) },
            )
        }
        return
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onClose() } }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        when (open) {
            is Sheet.ChildSheet -> ChildForm(
                child = state.children.firstOrNull { it.id == open.child.id } ?: open.child,
                avatar = state.avatarOf(open.child),
                linked = state.memberOf(open.child) != null,
                saving = state.saving,
                onSave = { name, color -> actions.onUpdateChild(open.child.id, name, color, close) },
                onBells = { onOpen(Sheet.Bells) },
                onDelete = { id -> actions.onDeleteChild(id, close) },
            )

            Sheet.AddChild -> AddChildForm(
                candidates = state.candidates,
                saving = state.saving,
                onPick = { actions.onAddChild(it, close) },
            )

            is Sheet.EntrySheet -> if (child != null) {
                EntryForm(
                    child = child,
                    entry = open.entry,
                    day = open.day,
                    locale = locale,
                    time = time,
                    saving = state.saving,
                    onSave = { draft, addNext, saved ->
                        actions.onSaveEntry(child.id, open.entry?.id, draft, if (addNext) saved else close)
                    },
                    onDelete = { actions.onDeleteEntry(child.id, it, close) },
                    onSetUpBells = { onOpen(Sheet.Bells) },
                )
            }

            Sheet.Bells -> if (child != null) {
                BellsForm(
                    child = child,
                    others = state.children.filter { it.id != child.id && it.periods.isNotEmpty() },
                    time = time,
                    saving = state.saving,
                    onSave = { actions.onSavePeriods(child.id, it, close) },
                    onCopyFrom = { actions.onCopyPeriods(child.id, it, close) },
                )
            }

            is Sheet.CopyDay -> Unit
        }
    }
}

@Composable
private fun Timetable(
    state: SchoolUi,
    child: Child,
    editing: Boolean,
    locale: Locale,
    time: (LocalTime) -> String,
    actions: SchoolActions,
    onEditChild: (Child) -> Unit,
    onAddChild: () -> Unit,
    onOpenEntry: (SchoolEntry) -> Unit,
    onCopyDay: (DayOfWeek) -> Unit,
) {
    val days = state.days
    ChildrenRow(
        state = state,
        selectedId = child.id,
        editing = editing,
        onSelect = actions.onSelectChild,
        onEdit = onEditChild,
        onAdd = onAddChild,
    )
    SummaryCard(child, schoolDay(child.entries, state.today, state.now), time)
    val pager = rememberPagerState(initialPage = days.indexOf(state.day).coerceAtLeast(0)) { days.size }
    val scope = rememberCoroutineScope()
    // Swiping picks the day; a chip tap (or a new list of days) moves the pager.
    LaunchedEffect(pager.currentPage, days) {
        days.getOrNull(pager.currentPage)?.let { if (it != state.day) actions.onSelectDay(it) }
    }
    LaunchedEffect(state.day, days) {
        val index = days.indexOf(state.day)
        if (index >= 0 && index != pager.currentPage && !pager.isScrollInProgress) {
            pager.scrollToPage(index)
        }
    }
    DayChips(days, state.day, state.today, locale) { day ->
        actions.onSelectDay(day)
        scope.launch { pager.animateScrollToPage(days.indexOf(day)) }
    }
    PullToRefreshBox(isRefreshing = false, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { days[it] }) { page ->
            val day = days[page]
            DayPage(
                child = child,
                day = day,
                isToday = day == state.today,
                now = state.now,
                editing = editing,
                locale = locale,
                time = time,
                onOpenEntry = onOpenEntry,
                onCopyDay = { onCopyDay(day) },
            )
        }
    }
}

// --- Children ---

/** A child as an avatar: the family member's face and color when linked (as on the Wall). */
fun SchoolUi.avatarOf(child: Child): FamilyMember {
    val member = memberOf(child)
    return FamilyMember(
        id = child.id.toString(),
        name = child.name,
        avatarUrl = child.photoUrl,
        color = member?.color ?: avatarColor(child.color),
    )
}

/**
 * The children as the family's avatar row (same as the Wall), with a last "+" that adds someone
 * from the family. In edit mode, tapping the chosen child (or long-press "Edit") opens its sheet.
 */
@Composable
private fun ChildrenRow(
    state: SchoolUi,
    selectedId: Long?,
    editing: Boolean,
    onSelect: (Long) -> Unit,
    onEdit: (Child) -> Unit,
    onAdd: () -> Unit,
) {
    val byId = state.children.associateBy { it.id.toString() }
    MemberCarousel(
        members = state.children.map { state.avatarOf(it) },
        selectedId = selectedId?.toString(),
        onSelect = { id ->
            val child = id?.let(byId::get) ?: return@MemberCarousel
            if (editing && child.id == selectedId) onEdit(child) else onSelect(child.id)
        },
        onEditMember = if (editing) ({ id -> byId[id]?.let(onEdit) }) else null,
        onOpenFamily = if (state.canEdit) onAdd else null,
        addLabel = "Add",
        addClickLabel = "Add child",
    )
}

// --- Today ---

@Composable
private fun SummaryCard(child: Child, day: SchoolDay, time: (LocalTime) -> String) {
    val lines = summaryLines(child.name, day, time)
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(
                lines.first(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            lines.drop(1).forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// --- Week ---

@Composable
private fun DayChips(
    days: List<DayOfWeek>,
    selected: DayOfWeek,
    today: DayOfWeek,
    locale: Locale,
    onSelect: (DayOfWeek) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(days, key = { it.value }) { day ->
            val name = day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            FilterChip(
                selected = day == selected,
                onClick = { onSelect(day) },
                label = {
                    Text(
                        day.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                        fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                leadingIcon = if (day == today) {
                    { Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)) }
                } else {
                    null
                },
                modifier = Modifier.semantics {
                    contentDescription = if (day == today) "$name, today" else name
                },
            )
        }
    }
}

@Composable
private fun DayPage(
    child: Child,
    day: DayOfWeek,
    isToday: Boolean,
    now: LocalTime,
    editing: Boolean,
    locale: Locale,
    time: (LocalTime) -> String,
    onOpenEntry: (SchoolEntry) -> Unit,
    onCopyDay: () -> Unit,
) {
    val entries = child.on(day)
    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (editing && entries.any { it.isLesson }) {
            item {
                TextButton(onClick = onCopyDay, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Copy lessons to…")
                }
            }
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    "No lessons on ${day.getDisplayName(TextStyle.FULL_STANDALONE, locale)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }
        items(entries, key = { it.id }) { entry ->
            val state = when {
                !isToday -> RowTime.OTHER
                now >= entry.end -> RowTime.DONE
                now >= entry.start -> RowTime.NOW
                else -> RowTime.OTHER
            }
            EntryRow(entry, state, time, onClick = if (editing) ({ onOpenEntry(entry) }) else null)
        }
    }
}

private enum class RowTime { DONE, NOW, OTHER }

@Composable
private fun EntryRow(entry: SchoolEntry, rowTime: RowTime, time: (LocalTime) -> String, onClick: (() -> Unit)?) {
    val activity = !entry.isLesson
    val container = when {
        rowTime == RowTime.NOW -> MaterialTheme.colorScheme.primaryContainer
        activity -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val onContainer = when {
        rowTime == RowTime.NOW -> MaterialTheme.colorScheme.onPrimaryContainer
        activity -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val spoken = spokenEntry(entry, time) + if (rowTime == RowTime.NOW) ", now" else ""
    Surface(
        color = container,
        shape = RoundedCornerShape(14.dp),
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .alpha(if (rowTime == RowTime.DONE) 0.5f else 1f)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (activity) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = onContainer,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        entry.period?.toString().orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                timeRange(entry.start, entry.end, time),
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer.copy(alpha = 0.75f),
                maxLines = 1,
                // "11:40 AM–12:25 PM" needs more room than "11:40–12:25".
                modifier = Modifier.width(if (isTwelveHour(time)) 140.dp else 96.dp).padding(start = 4.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val second = if (activity) {
                    listOfNotNull(entry.place, entry.note)
                } else {
                    listOfNotNull(entry.teacher, entry.note)
                }
                if (second.isNotEmpty()) {
                    Text(
                        second.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainer.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!activity && entry.place != null) {
                Text(
                    "r. ${entry.place}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = onContainer.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

// --- Empty states ---

@Composable
private fun NoChildren(state: SchoolUi, onAdd: () -> Unit) {
    ChildrenRow(state, selectedId = null, editing = false, onSelect = {}, onEdit = {}, onAdd = onAdd)
    Centered {
        Text(
            if (state.canEdit) "Tap + to add who goes to school" else "No one in School yet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OfflineBanner(since: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            "Offline. Timetable from $since",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}
