package com.github.ljarka.picfort.ui.schooljournal

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
        MessageSummary(
            "a_7",
            "101",
            "",
            "Open day at school on Saturday",
            "2026-11-16T23:00:00Z",
            type = MESSAGE_TYPE_ANNOUNCEMENT,
            validFrom = "2026-11-17",
            validTo = "2026-11-21",
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
    fun messagesOnlyAnnouncements() = snap {
        val ui = withMessages()
        content(
            ui.copy(
                messages = ui.messages.copy(
                    kind = MessageKind.ANNOUNCEMENT,
                    items = messages.filter { it.kind == MessageKind.ANNOUNCEMENT },
                ),
            ),
        )
    }

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

    private val announcement = Message(
        id = "a_7",
        childId = "101",
        type = MESSAGE_TYPE_ANNOUNCEMENT,
        subject = "Open day at school on Saturday",
        sentAt = "2026-11-16T23:00:00Z",
        validFrom = "2026-11-17",
        validTo = "2026-11-21",
        bodyHtml = "<p>The school is open for families on Saturday from 10:00 to 14:00.</p>",
    )

    @Test
    fun announcementScreen() = snap {
        SchoolJournalMessageContent(
            MessageUi(loading = false, message = announcement, childName = "Ola", read = true),
            clock,
        )
    }

    @Test
    fun announcementScreenDark() = snap(dark = true) {
        SchoolJournalMessageContent(
            MessageUi(
                loading = false,
                message = announcement.copy(sender = "Head teacher", validFrom = null),
                read = true,
            ),
            clock,
        )
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

    private fun grade(
        id: String,
        value: String,
        numeric: Double?,
        issued: String,
        weight: Int? = 2,
        kind: String = "regular",
        category: String = "Test",
    ) = Grade(
        id = id,
        childId = "101",
        value = value,
        numericValue = numeric,
        weight = weight,
        category = category,
        kind = kind,
        countsToAverage = kind == "regular",
        teacher = "A. Teacher",
        issuedOn = issued,
        firstSeenAt = issued + "T12:00:00Z",
        updatedAt = issued + "T12:00:00Z",
    )

    private val olaSubjects = listOf(
        SubjectSummary(
            "Art",
            null,
            listOf(grade("a1", "+", null, "2026-10-01", weight = null, category = "Activity")),
        ),
        SubjectSummary(
            "English",
            5.67,
            listOf(grade("e1", "6", 6.0, "2026-10-12"), grade("e2", "5", 5.0, "2026-11-02", weight = 1)),
        ),
        SubjectSummary(
            "Mathematics",
            4.33,
            listOf(
                grade("m1", "5", 5.0, "2026-09-20", weight = 3),
                grade("m2", "4+", 4.5, "2026-10-08", weight = 1),
                grade("m3", "3-", 2.75, "2026-10-30"),
                grade("m4", "1", 1.0, "2026-11-05"),
                grade("m5", "6", 6.0, "2026-11-17", weight = 3),
                grade("m6", "4", 4.0, "2026-11-10", kind = "proposed_semester", weight = null, category = "Proposed"),
            ),
        ),
    )

    private fun withGrades(sections: List<GradeSection>, newIds: Set<String> = setOf("m5"), selected: String? = "101") =
        SchoolJournalUi(
            mode = JournalMode.CONTENT,
            status = fresh,
            children = children,
            selectedChild = selected,
            tab = JournalTab.GRADES,
            grades = GradesUi(semester = 1, semesterChosen = true, sections = sections, newIds = newIds, loaded = true),
        )

    @Test
    fun gradesOneChildWithNew() = snap { content(withGrades(listOf(GradeSection("101", null, olaSubjects)))) }

    @Test
    fun gradesOneChildDark() = snap(dark = true) {
        content(withGrades(listOf(GradeSection("101", null, olaSubjects)), newIds = emptySet()))
    }

    @Test
    fun gradesAllChildrenInSections() = snap {
        content(
            withGrades(
                listOf(
                    GradeSection("101", "Ola", olaSubjects.take(2)),
                    GradeSection(
                        "102",
                        "Kuba",
                        listOf(SubjectSummary("Physical education", 5.0, listOf(grade("k1", "5", 5.0, "2026-11-01")))),
                    ),
                ),
                newIds = emptySet(),
                selected = null,
            ),
        )
    }

    @Test
    fun gradesEmptySemester() = snap { content(withGrades(listOf(GradeSection("101", null, emptyList())))) }

    @Test
    fun gradeDetails() = snap {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            GradeDetails(
                grade("m5", "6", 6.0, "2026-11-17", weight = 3).copy(comment = "Excellent test, no mistakes"),
                "Mathematics",
                isNew = true,
                clock,
            )
        }
    }

    @Test
    fun syncing() = snap {
        content(withMessages().copy(syncing = true))
    }

    @Test
    fun contentStaleDark() = snap(dark = true) {
        content(
            SchoolJournalUi(
                mode = JournalMode.CONTENT,
                status = fresh.copy(lastSuccessfulSyncAt = "2026-11-17T11:05:00Z"),
                children = children,
            ),
        )
    }

    @Test
    fun contentStorageProblem() = snap {
        content(SchoolJournalUi(mode = JournalMode.CONTENT, status = fresh, storageProblem = true))
    }

    @Test
    fun statusDetailsFailedRun() = snap {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            StatusDetails(
                fresh.copy(
                    lastSync = fresh.lastSync!!.copy(status = "error", errorCode = "journal_unavailable"),
                    lastSuccessfulSyncAt = "2026-11-17T09:05:00Z",
                ),
                clock,
                address = "https://school.example.com/",
            )
        }
    }

    @Test
    fun offlineWithCopy() = snap {
        content(withMessages().copy(offline = true, cachedAt = Instant.parse("2026-11-17T13:20:00Z")))
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
