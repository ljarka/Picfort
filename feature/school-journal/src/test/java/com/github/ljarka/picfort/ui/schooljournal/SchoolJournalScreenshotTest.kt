package com.github.ljarka.picfort.ui.schooljournal

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import com.github.ljarka.picfort.core.ui.theme.PicfortTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

class SchoolJournalScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, maxPercentDifference = 0.1)

    private val clock = JournalClock(now = Instant.parse("2026-11-17T13:44:00Z"), zone = ZoneId.of("Europe/Warsaw"))

    private fun snap(dark: Boolean = false, content: @Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(
            DeviceConfig.PIXEL_6.copy(nightMode = if (dark) NightMode.NIGHT else NightMode.NOTNIGHT),
        )
        paparazzi.snapshot { PicfortTheme(darkTheme = dark, dynamicColor = false) { content() } }
    }

    private val fresh = JournalStatus(
        auth = "ok",
        lastSync = SyncRun(
            startedAt = "2026-11-17T13:32:00Z",
            finishedAt = "2026-11-17T13:32:20Z",
            status = "ok",
            itemsFetched = 120,
            changesDetected = 2,
        ),
        lastSuccessfulSyncAt = "2026-11-17T13:32:20Z",
        nextSyncAt = "2026-11-17T13:47:20Z",
        lastEventId = 340,
        counts = StatusCounts(grades = 80, messages = 40),
        version = "1.2.0",
    )
    private val children = listOf(ChildAccount("101", "ola.k", "Ola"), ChildAccount("102", "kuba.k", "Kuba"))

    @Composable
    private fun content(ui: SchoolJournalUi) = SchoolJournalContent(ui, clock)

    @Test
    fun setupEmpty() = snap { content(SchoolJournalUi(mode = JournalMode.SETUP)) }

    @Test
    fun setupError() = snap {
        content(
            SchoolJournalUi(
                mode = JournalMode.SETUP,
                form = SetupForm(address = "192.168.1.10:5100", key = "secret", error = "Wrong API key"),
            ),
        )
    }

    @Test
    fun setupKeyNoLongerValid() = snap(dark = true) {
        content(
            SchoolJournalUi(
                mode = JournalMode.SETUP,
                form = SetupForm(
                    address = "http://192.168.1.10:5100/",
                    key = "old",
                    notice = "The API key is no longer valid",
                    editing = true,
                ),
                status = fresh,
            ),
        )
    }

    @Test
    fun contentTwoChildren() = snap {
        content(SchoolJournalUi(mode = JournalMode.CONTENT, status = fresh, children = children, selectedChild = "101"))
    }

    @Test
    fun contentOneChildDark() = snap(dark = true) {
        content(
            SchoolJournalUi(
                mode = JournalMode.CONTENT,
                status = fresh,
                children = children.take(1),
                tab = JournalTab.GRADES,
            ),
        )
    }

    @Test
    fun contentStaleAndFailed() = snap {
        content(
            SchoolJournalUi(
                mode = JournalMode.CONTENT,
                status = fresh.copy(
                    lastSync = fresh.lastSync!!.copy(status = "error", errorCode = "journal_unavailable"),
                    lastSuccessfulSyncAt = "2026-11-16T09:05:00Z",
                ),
                children = children,
            ),
        )
    }

    @Test
    fun contentAuthFailed() = snap {
        content(
            SchoolJournalUi(
                mode = JournalMode.CONTENT,
                status = fresh.copy(auth = "auth_failed", lastSync = fresh.lastSync!!.copy(status = "auth_failed")),
            ),
        )
    }

    @Test
    fun contentFirstSyncRunning() = snap {
        content(
            SchoolJournalUi(
                mode = JournalMode.CONTENT,
                status = JournalStatus(auth = "unknown", lastSync = SyncRun(status = "running"), nextSyncAt = null),
            ),
        )
    }

    @Test
    fun contentOffline() = snap {
        content(SchoolJournalUi(mode = JournalMode.CONTENT, status = fresh, children = children, offline = true))
    }

    @Test
    fun statusDetails() = snap { StatusDetails(fresh, clock, address = "http://192.168.1.10:5100/") }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun topBarIcon() = snap {
        TopAppBar(title = { Text("Family Wall") }, actions = { SchoolJournalIcon(hasNew = false) {} })
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun topBarIconWithDot() = snap {
        TopAppBar(title = { Text("Family Wall") }, actions = { SchoolJournalIcon(hasNew = true) {} })
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun topBarIconWithDotDark() = snap(dark = true) {
        TopAppBar(title = { Text("School") }, actions = { SchoolJournalIcon(hasNew = true) {} })
    }

    private val messages = listOf(
        MessageSummary("m4", "102", "Coach", "Match on Saturday", "2026-11-17T12:10:00Z"),
        MessageSummary(
            "m3",
            "101",
            "Class teacher",
            "Trip on Friday: consent form and what to pack",
            "2026-11-16T07:30:00Z",
            hasAttachments = true,
        ),
        MessageSummary("m2", "101", "School office", "Photo day", "2026-11-12T08:00:00Z"),
        MessageSummary(
            "m1",
            "101",
            "School office",
            "Old timetable",
            "2026-10-02T08:00:00Z",
            deletedAt = "2026-10-05T00:00:00Z",
        ),
    )

    /** m4 and m3 unread: after the watermark, m3 also marked unread on purpose. */
    private val readState =
        ReadState(watermark = "2026-11-15T00:00:00Z", readIds = setOf("m3"), unreadIds = setOf("m2"))

    private fun withMessages(selected: String? = null, unreadOnly: Boolean = false) = SchoolJournalUi(
        mode = JournalMode.CONTENT,
        status = fresh,
        children = children,
        selectedChild = selected,
        readState = readState,
        messages = MessagesUi(items = messages, loaded = true, unreadOnly = unreadOnly, showRemoved = true),
        news = JournalNews(unreadMessages = true),
    )

    @Test
    fun messagesAllChildren() = snap { content(withMessages()) }

    @Test
    fun messagesOneChildDark() = snap(dark = true) { content(withMessages(selected = "101")) }

    @Test
    fun messagesEmptyUnreadOnly() = snap {
        content(
            withMessages().copy(messages = MessagesUi(loaded = true, unreadOnly = true), news = JournalNews()),
        )
    }

    private val fullMessage = Message(
        id = "m3",
        childId = "101",
        sender = "Class teacher",
        subject = "Trip on Friday",
        sentAt = "2026-11-16T07:30:00Z",
        hasAttachments = true,
        bodyHtml = "<p>Dear parents,</p><p>On <b>Friday</b> we go to the museum. Details: " +
            "<a href=\"https://example.com/trip\">example.com/trip</a></p><ul><li>packed lunch</li><li>rain jacket</li></ul>",
        bodyText = "Dear parents, …",
        attachments = listOf(Attachment("consent-form.pdf"), Attachment("plan.pdf")),
    )

    @Test
    fun messageScreen() = snap {
        SchoolJournalMessageContent(MessageUi(loading = false, message = fullMessage, childName = "Ola"), clock)
    }

    @Test
    fun messageScreenUnreadRemovedDark() = snap(dark = true) {
        SchoolJournalMessageContent(
            MessageUi(
                loading = false,
                message = fullMessage.copy(deletedAt = "2026-11-17T00:00:00Z", attachments = emptyList()),
                read = false,
            ),
            clock,
        )
    }

    @Test
    fun contentTabDots() = snap {
        content(
            SchoolJournalUi(
                mode = JournalMode.CONTENT,
                status = fresh,
                children = children,
                news = JournalNews(unreadMessages = true, unseenGrades = true),
            ),
        )
    }
}
