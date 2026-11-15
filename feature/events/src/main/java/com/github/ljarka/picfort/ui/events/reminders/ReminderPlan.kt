package com.github.ljarka.picfort.ui.events.reminders

import com.github.ljarka.picfort.core.model.FamilyEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.serialization.Serializable

/** What kind of event a default reminder applies to (T-039). */
enum class ReminderKind(val label: String) {
    ALL_DAY("Events without a time"),
    TIMED("Events with a time"),
    BIRTHDAY("Birthdays"),
}

/**
 * When to remind, relative to an occurrence's start. Day presets ([daysBefore] at [at]) fit every event;
 * minute presets ([minutesBefore]) only events with a start time.
 */
enum class ReminderPreset(
    val label: String,
    val daysBefore: Int = 0,
    val at: LocalTime? = null,
    val minutesBefore: Long? = null,
) {
    AT_START("At the start time", minutesBefore = 0),
    MINUTES_15("15 minutes before", minutesBefore = 15),
    HOUR_1("1 hour before", minutesBefore = 60),
    ON_THE_DAY("On the day at 9:00", daysBefore = 0, at = LocalTime.of(9, 0)),
    DAY_BEFORE("The day before at 19:00", daysBefore = 1, at = LocalTime.of(19, 0)),
    DAYS_3("3 days before at 18:00", daysBefore = 3, at = LocalTime.of(18, 0)),
    WEEK("A week before at 18:00", daysBefore = 7, at = LocalTime.of(18, 0)),
    ;

    fun fits(event: FamilyEvent): Boolean = minutesBefore == null || event.startTime != null

    fun fits(kind: ReminderKind): Boolean = minutesBefore == null || kind == ReminderKind.TIMED

    /** When this reminder fires for [event]'s current occurrence (`startDate`), or null if it doesn't fit. */
    fun triggerFor(event: FamilyEvent): LocalDateTime? {
        if (!fits(event)) return null
        return if (minutesBefore != null) {
            LocalDateTime.of(event.startDate, event.startTime).minusMinutes(minutesBefore)
        } else {
            LocalDateTime.of(event.startDate.minusDays(daysBefore.toLong()), at)
        }
    }
}

/** The phone's defaults; null = no reminder for that kind. */
data class ReminderDefaults(
    val allDay: ReminderPreset? = ReminderPreset.DAY_BEFORE,
    val timed: ReminderPreset? = ReminderPreset.HOUR_1,
    val birthday: ReminderPreset? = ReminderPreset.ON_THE_DAY,
) {
    fun of(kind: ReminderKind): ReminderPreset? = when (kind) {
        ReminderKind.ALL_DAY -> allDay
        ReminderKind.TIMED -> timed
        ReminderKind.BIRTHDAY -> birthday
    }

    fun with(kind: ReminderKind, preset: ReminderPreset?): ReminderDefaults = when (kind) {
        ReminderKind.ALL_DAY -> copy(allDay = preset)
        ReminderKind.TIMED -> copy(timed = preset)
        ReminderKind.BIRTHDAY -> copy(birthday = preset)
    }
}

/** One event's choice on this phone: the default, none, or a preset. */
sealed interface ReminderChoice {
    data object Default : ReminderChoice
    data object Off : ReminderChoice
    data class Preset(val preset: ReminderPreset) : ReminderChoice

    companion object {
        /** Stored as "off" or a preset name; anything else is the default. */
        fun fromStored(value: String?): ReminderChoice = when (value) {
            null -> Default
            OFF -> Off
            else -> ReminderPreset.entries.firstOrNull { it.name == value }?.let(::Preset) ?: Default
        }

        const val OFF = "off"
    }
}

fun ReminderChoice.stored(): String? = when (this) {
    ReminderChoice.Default -> null
    ReminderChoice.Off -> ReminderChoice.OFF
    is ReminderChoice.Preset -> preset.name
}

fun FamilyEvent.reminderKind(): ReminderKind = when {
    isBirthday -> ReminderKind.BIRTHDAY
    startTime != null -> ReminderKind.TIMED
    else -> ReminderKind.ALL_DAY
}

/** The preset that applies to [event]: its own choice, else the phone's default (if it fits the event). */
fun effectivePreset(event: FamilyEvent, choice: ReminderChoice, defaults: ReminderDefaults): ReminderPreset? =
    when (choice) {
        ReminderChoice.Off -> null
        is ReminderChoice.Preset -> choice.preset
        ReminderChoice.Default -> defaults.of(event.reminderKind())
    }?.takeIf { it.fits(event) }

/** A reminder armed on this phone; kept so it can be re-armed after a reboot and shown without network. */
@Serializable
data class PlannedReminder(
    val eventId: Long,
    /** Occurrence start, ISO date. */
    val occurrence: String,
    /** Local date-time, ISO: re-converted to an instant on every arm, so a time zone change follows. */
    val trigger: String,
    val title: String,
    val text: String,
) {
    /** Shown once: a new preset (another [trigger]) or the next occurrence is a new reminder. */
    val key: String get() = "$eventId@$occurrence@$trigger"
}

/**
 * An armed reminder whose time passed (missed while the phone was off) still comes, up to this long late
 * and before the event starts. A time already past when first planned (a new event, a new preset) doesn't.
 */
private const val LATE_HOURS = 12L

/**
 * Pure plan (T-039): one reminder per upcoming event, for its current occurrence. Recurring events need no
 * extra logic: once an occurrence is over, the next refresh brings the next one, and its reminder.
 */
fun planReminders(
    events: List<FamilyEvent>,
    defaults: ReminderDefaults,
    choices: Map<Long, ReminderChoice>,
    delivered: Set<String>,
    /** Keys of the reminders armed so far (the previous plan). */
    armed: Set<String>,
    now: LocalDateTime,
    memberName: (String) -> String?,
    locale: Locale,
): List<PlannedReminder> = events.mapNotNull { event ->
    val preset = effectivePreset(event, choices[event.id] ?: ReminderChoice.Default, defaults) ?: return@mapNotNull null
    val trigger = preset.triggerFor(event) ?: return@mapNotNull null
    val starts =
        event.startTime?.let { LocalDateTime.of(event.startDate, it) } ?: event.startDate.plusDays(1).atStartOfDay()
    val planned = PlannedReminder(
        eventId = event.id,
        occurrence = event.startDate.toString(),
        trigger = trigger.toString(),
        title = event.displayTitle(memberName),
        text = reminderText(event, trigger.toLocalDate(), memberName, locale),
    )
    val late = !trigger.isAfter(now)
    when {
        !starts.isAfter(now) || planned.key in delivered -> null
        late && (planned.key !in armed || trigger.isBefore(now.minusHours(LATE_HOURS))) -> null
        else -> planned
    }
}

/** "Tomorrow at 17:30 · Clinic", "Ola turns 9 in 3 days", "Today". Relative to the day the reminder fires. */
fun reminderText(event: FamilyEvent, on: LocalDate, memberName: (String) -> String?, locale: Locale): String {
    val days = ChronoUnit.DAYS.between(on, event.startDate)
    val whenText = when (days) {
        0L -> "today"
        1L -> "tomorrow"
        else -> "in $days days, ${event.startDate.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))}"
    }
    val name = event.birthdayOf?.let(memberName)
    if (name != null) {
        return if (event.turns != null) "$name turns ${event.turns} $whenText" else "$name's birthday is $whenText"
    }
    val time = event.startTime?.let { " at ${it.format(DateTimeFormatter.ofPattern("HH:mm"))}" }.orEmpty()
    val place = event.location?.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    return whenText.replaceFirstChar { it.uppercase() } + time + place
}
