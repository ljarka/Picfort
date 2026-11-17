package com.github.ljarka.picfort.ui.schooljournal

import android.text.format.DateFormat
import android.util.Log
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.github.ljarka.picfort.feature.schooljournal.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One message (spec §4.3). */
data class MessageUi(
    val loading: Boolean = true,
    val message: Message? = null,
    val childName: String? = null,
    /** Read on this phone by this Immich user. */
    val read: Boolean = true,
    /** 404: gone from the server; the screen closes. */
    val gone: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SchoolJournalMessageViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val api: SchoolJournalApi,
    private val config: SchoolJournalConfigStore,
    private val readStates: ReadStateStore,
    private val news: SchoolJournalNews,
    private val removed: RemovedMessages,
) : ViewModel() {
    private val _state = MutableStateFlow(MessageUi())
    val state: StateFlow<MessageUi> = _state.asStateFlow()
    private var loadedId: String? = null

    /** Loads the message once and marks it read for this user (opening = reading). */
    fun load(id: String) {
        if (loadedId == id) return
        loadedId = id
        val c = config.connection.value ?: return
        viewModelScope.launch {
            try {
                val message = api.message(c, id)
                readStates.update(c.base.toString()) { it.markRead(id) }
                dismissMessageNotification(context, id)
                _state.update { it.copy(loading = false, message = message, read = true, error = null) }
                news.refresh()
                val name = runCatching { api.children(c) }.getOrNull()?.takeIf { it.size >= 2 }
                    ?.firstOrNull { it.id == message.childId }?.name
                _state.update { it.copy(childName = name) }
            } catch (e: SchoolJournalError.NotFound) {
                removed.add(id)
                readStates.update(c.base.toString()) { it.forget(id) }
                dismissMessageNotification(context, id)
                _state.update { it.copy(loading = false, gone = true) }
            } catch (e: SchoolJournalError) {
                Log.w("SchoolJournal", "Message $id: ${e.message}")
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun toggleRead() {
        val c = config.connection.value ?: return
        val message = _state.value.message ?: return
        val read = !_state.value.read
        readStates.update(c.base.toString()) { if (read) it.markRead(message.id) else it.markUnread(message.summary()) }
        if (read) dismissMessageNotification(context, message.id)
        _state.update { it.copy(read = read) }
        viewModelScope.launch { news.refresh() }
    }
}

@Composable
fun SchoolJournalMessageScreen(messageId: String, onBack: () -> Unit) {
    val viewModel = hiltViewModel<SchoolJournalMessageViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(messageId) { viewModel.load(messageId) }
    LaunchedEffect(state.gone) { if (state.gone) onBack() }
    val context = LocalContext.current
    SchoolJournalMessageContent(
        state = state,
        clock = JournalClock(
            now = remember { Instant.now() },
            locale = LocalConfiguration.current.locales[0],
            is24Hour = DateFormat.is24HourFormat(context),
        ),
        onBack = onBack,
        onToggleRead = viewModel::toggleRead,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolJournalMessageContent(
    state: MessageUi,
    clock: JournalClock,
    onBack: () -> Unit = {},
    onToggleRead: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.message?.kind == MessageKind.ANNOUNCEMENT) "Announcement" else "Message")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.message != null) {
                        TextButton(onClick = onToggleRead) {
                            Text(if (state.read) "Mark as unread" else "Mark as read")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val message = state.message
        when {
            message != null -> MessageBody(message, state.childName, clock, Modifier.padding(padding))

            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(state.error ?: "Can't open this message", textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun MessageBody(message: Message, childName: String?, clock: JournalClock, modifier: Modifier) {
    val linkColor = MaterialTheme.colorScheme.primary
    val body = remember(message.bodyHtml, message.bodyText, linkColor) {
        val html = message.bodyHtml.takeIf { it.isNotBlank() }?.let {
            AnnotatedString.fromHtml(
                it,
                linkStyles = TextLinkStyles(
                    style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                ),
            )
        }
        html?.takeIf { it.isNotBlank() } ?: AnnotatedString(message.bodyText)
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(message.subject, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            listOfNotNull(senderLabel(message.kind, message.sender), childName).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        instantOrNull(message.sentAt)?.let {
            Text(
                whenText(it, clock.now, clock.zone, clock.locale, clock.is24Hour, withToday = true),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (message.kind == MessageKind.ANNOUNCEMENT) {
            Spacer(Modifier.height(12.dp))
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_announcement),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        listOfNotNull(
                            "Announcement from the school notice board",
                            boardDates(message.validFrom, message.validTo, clock.locale),
                        ).joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        if (message.deletedAt != null) {
            Spacer(Modifier.height(12.dp))
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(
                    "Removed from the e-journal",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        SelectionContainer { Text(body, style = MaterialTheme.typography.bodyLarge) }
        if (message.attachments.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text("Attachments", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            message.attachments.forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(4.dp))
            Text(
                "Open the e-journal to download attachments.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}
