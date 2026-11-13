package com.github.ljarka.picfort.ui.events

import android.text.format.DateFormat
import com.github.ljarka.picfort.core.model.EventsSegment
import com.github.ljarka.picfort.core.model.DayPhoto
import com.github.ljarka.picfort.core.model.EventRepeat
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.model.FamilyMember
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.github.ljarka.picfort.core.ui.avatar.MemberCarousel
import com.github.ljarka.picfort.core.ui.R
import com.github.ljarka.picfort.core.ui.component.ServerRequiredContent
import com.github.ljarka.picfort.core.ui.component.SettingsAction
import com.github.ljarka.picfort.core.ui.component.aboveFloatingTabBar
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

data class EventsListUi(
    val configured: Boolean = true,
    val segment: EventsSegment = EventsSegment.UPCOMING,
    val today: LocalDate = LocalDate.of(2026, 11, 11),
    val upcomingEvents: List<FamilyEvent> = emptyList(),
    val pastEvents: List<FamilyEvent> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMorePast: Boolean = false,
    val hasMorePast: Boolean = false,
    val loadError: String? = null,
    val message: String? = null,
    /** Member id → current name, for "Ola's birthday". */
    val memberNames: Map<String, String> = emptyMap(),
    /** All family members, for the avatars of who an event is for (T-053). */
    val members: List<FamilyMember> = emptyList(),
    /** The member the list is filtered to, or null for everyone. */
    val selectedMemberId: String? = null,
)

data class EventDetailUi(
    val event: FamilyEvent? = null,
    val today: LocalDate = LocalDate.of(2026, 11, 11),
    val authorName: String = "Former member",
    val canDelete: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val message: String? = null,
    val memberNames: Map<String, String> = emptyMap(),
    val members: List<FamilyMember> = emptyList(),
    /** Photos from past birthdays (T-055); null for other events and birthdays without a year. */
    val birthdayPhotos: BirthdayPhotosUi? = null,
)

data class EventFormUi(
    val isEditing: Boolean = false,
    val title: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val location: String = "",
    val note: String = "",
    val repeat: EventRepeat? = null,
    /** Last day an occurrence may start; null = never ends. Only used with [repeat]. */
    val repeatUntil: LocalDate? = null,
    /** Editing an event that already repeats: the change applies to the whole series. */
    val editsSeries: Boolean = false,
    /** Who the event is for (T-053), chosen from [members]. */
    val memberIds: List<String> = emptyList(),
    val members: List<FamilyMember> = emptyList(),
    val today: LocalDate = LocalDate.of(2026, 11, 11),
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
) {
    val canSave: Boolean get() = title.trim().isNotEmpty() && startDate != null && !isSaving
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsListContent(
    state: EventsListUi,
    locale: Locale = Locale.ENGLISH,
    onSegmentChange: (EventsSegment) -> Unit = {},
    onOpenEvent: (FamilyEvent) -> Unit = {},
    onAddClick: () -> Unit = {},
    onLoadMorePast: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSetUpServer: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    onSelectMember: (String?) -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }
    val selected = state.members.firstOrNull { it.id == state.selectedMemberId }
    val events = (if (state.segment == EventsSegment.UPCOMING) state.upcomingEvents else state.pastEvents)
        .forMember(selected?.id)
    val sections = remember(state.segment, events, state.today, locale) {
        if (state.segment == EventsSegment.UPCOMING) {
            groupUpcomingEvents(events, state.today, locale)
        } else {
            groupPastEvents(events, state.today, locale)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Events") },
                actions = { SettingsAction(onSettingsClick) },
            )
        },
        floatingActionButton = {
            if (state.configured && state.loadError == null) {
                FloatingActionButton(
                    onClick = onAddClick,
                    modifier = Modifier.aboveFloatingTabBar(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add event")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.configured) {
                ServerRequiredContent("Connect your Picfort server to use events.", onSetUpServer)
                return@Scaffold
            }
            if (state.members.isNotEmpty()) {
                MemberCarousel(
                    members = state.members,
                    selectedId = selected?.id,
                    onSelect = { onSelectMember(it.takeIf { id -> id != selected?.id }) },
                )
            }
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SegmentedButton(
                    selected = state.segment == EventsSegment.UPCOMING,
                    onClick = { onSegmentChange(EventsSegment.UPCOMING) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) {
                    Text("Upcoming")
                }
                SegmentedButton(
                    selected = state.segment == EventsSegment.PAST,
                    onClick = { onSegmentChange(EventsSegment.PAST) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("Past")
                }
            }

            when {
                state.isLoading && events.isEmpty() -> CenteredBox { CircularProgressIndicator() }
                state.loadError != null && events.isEmpty() -> CenteredBox {
                    Text(
                        state.loadError,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRefresh) { Text("Retry") }
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = false,
                        onRefresh = onRefresh,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (sections.isEmpty()) {
                            CenteredBox {
                                Text(
                                    text = when {
                                        selected != null && state.segment == EventsSegment.UPCOMING ->
                                            "Nothing planned for ${selected.name}."
                                        selected != null -> "No past events for ${selected.name}."
                                        state.segment == EventsSegment.UPCOMING ->
                                            "Nothing planned yet. Add a trip or a birthday."
                                        else -> "No past events."
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            val listState = rememberLazyListState()
                            val nearEnd by remember {
                                derivedStateOf {
                                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    val total = listState.layoutInfo.totalItemsCount
                                    total > 0 && lastVisible >= total - 3
                                }
                            }
                            LaunchedEffect(nearEnd, state.segment, state.hasMorePast, state.isLoadingMorePast) {
                                if (nearEnd && state.segment == EventsSegment.PAST && state.hasMorePast && !state.isLoadingMorePast) {
                                    onLoadMorePast()
                                }
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 148.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                sections.forEach { section ->
                                    item(key = "header-${section.title}") {
                                        Text(
                                            text = section.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (section.isNow) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                                        )
                                    }
                                    items(section.events, key = { it.id }) { event ->
                                        EventRow(
                                            event = event,
                                            title = event.displayTitle(state.memberNames::get),
                                            today = state.today,
                                            isUpcoming = state.segment == EventsSegment.UPCOMING,
                                            isNow = section.isNow,
                                            locale = locale,
                                            who = membersOf(event.who, state.members),
                                            onClick = { onOpenEvent(event) },
                                        )
                                    }
                                }
                                if (state.segment == EventsSegment.PAST && state.isLoadingMorePast) {
                                    item(key = "loading-more") {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: FamilyEvent,
    title: String,
    today: LocalDate,
    isUpcoming: Boolean,
    isNow: Boolean,
    locale: Locale,
    onClick: () -> Unit,
    who: List<FamilyMember> = emptyList(),
) {
    val badge = remember(event.startDate, event.endDate, locale) {
        formatEventDateBadge(event.startDate, event.endDate, locale)
    }
    val formattedDates = remember(event.startDate, event.endDate, today, locale) {
        formatEventDateRange(event.startDate, event.endDate, today, locale)
    }
    val relLabel = remember(event, today, isUpcoming) {
        if (isUpcoming) relativeEventLabel(event, today) else null
    }
    val timeStr = remember(event.startTime) { event.startTime?.format(TIME_FMT) }
    val turns = turnsLabel(event)
    val subtitle = remember(relLabel, timeStr, turns) {
        listOfNotNull(relLabel, timeStr, turns).joinToString(" · ").ifEmpty { null }
    }
    // Birthdays repeat yearly by nature: the cake says it, no "Every year" line.
    val repeatLabel = event.repeat?.takeUnless { event.isBirthday }?.label
    val whoNames = who.joinToString(", ") { it.name }.ifEmpty { null }
    val talkBackDesc = remember(title, formattedDates, relLabel, turns, repeatLabel, whoNames) {
        listOfNotNull(title, formattedDates, relLabel, turns, repeatLabel, whoNames).joinToString(", ")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // One stop for TalkBack: "<title>, <dates>, <relative label>", without the
            // title and label Texts inside being read again.
            .clearAndSetSemantics {
                contentDescription = talkBackDesc
                role = Role.Button
                onClick { onClick(); true }
            },
        shape = RoundedCornerShape(16.dp),
        color = if (isNow) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (isNow) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .width(76.dp)
                    .clearAndSetSemantics {},
                shape = RoundedCornerShape(12.dp),
                color = if (isNow) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (event.isBirthday) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_cake),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(bottom = 2.dp),
                            tint = if (isNow) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                        )
                    }
                    Text(
                        text = badge.top,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isNow) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        maxLines = 1,
                    )
                    Text(
                        text = badge.bottom,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isNow) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val location = event.location
                if (!location.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (repeatLabel != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_repeat),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = repeatLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (who.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                MemberStack(who)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailContent(
    state: EventDetailUi,
    locale: Locale = Locale.ENGLISH,
    onBack: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onLocationClick: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    /** Birthdays live in Family settings (T-036): their only action opens it. */
    onEditBirthday: () -> Unit = {},
    albumSlot: @Composable () -> Unit = {},
    /** A birthday photo was tapped: the photo and its position among all of them (T-055). */
    onOpenPhoto: (DayPhoto, Int) -> Unit = { _, _ -> },
    onOpenDay: (LocalDate) -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && state.event != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete event?") },
            text = { Text("\"${state.event.title}\" will be removed for everyone in the family.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.event != null && !state.event.isBirthday) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                        if (state.canDelete) {
                            IconButton(onClick = { confirmDelete = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val event = state.event
        when {
            state.isLoading && event == null -> CenteredBox(Modifier.padding(padding)) {
                CircularProgressIndicator()
            }
            state.loadError != null && event == null -> CenteredBox(Modifier.padding(padding)) {
                Text(state.loadError, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Retry") }
            }
            event != null -> {
                val dateRange = remember(event.startDate, event.endDate, state.today, locale) {
                    formatEventDateRange(event.startDate, event.endDate, state.today, locale)
                }
                val timeStr = remember(event.startTime) { event.startTime?.format(TIME_FMT) }
                val relLabel = remember(event, state.today) {
                    if (event.isOngoingOn(state.today)) "Now" else relativeEventLabel(event, state.today)
                }
                EventDetailList(
                    photos = state.birthdayPhotos,
                    name = event.birthdayOf?.let(state.memberNames::get) ?: event.title,
                    onOpenPhoto = onOpenPhoto,
                    onOpenDay = onOpenDay,
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    if (relLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = relLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        text = event.displayTitle(state.memberNames::get),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(16.dp))

                    val turns = turnsLabel(event)
                    if (event.isBirthday && turns != null) {
                        DetailLine(R.drawable.ic_cake, turns.replaceFirstChar { it.uppercase() })
                        Spacer(Modifier.height(14.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (timeStr != null) "$dateRange · $timeStr" else dateRange,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }

                    val who = membersOf(event.who, state.members)
                    if (who.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        WhoLine(who)
                    }

                    val repeatText = remember(event, state.today, locale) { repeatSummary(event, state.today, locale) }
                    if (repeatText != null && !event.isBirthday) {
                        Spacer(Modifier.height(14.dp))
                        DetailLine(R.drawable.ic_repeat, repeatText)
                    }

                    val location = event.location
                    if (!location.isNullOrBlank()) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLocationClick(location) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = location,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    val note = event.note
                    if (!note.isNullOrBlank()) {
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }

                    albumSlot()

                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    if (event.isBirthday) {
                        Text(
                            text = "Birthdays come from the birth dates in Family settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        FilledTonalButton(onClick = onEditBirthday) {
                            Text("Edit in Family settings")
                        }
                    } else {
                        Text(
                            text = "Added by ${state.authorName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CenteredBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope
