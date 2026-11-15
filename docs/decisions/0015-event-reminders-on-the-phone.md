# ADR 0015 — Event reminders are planned on each phone

**Status:** accepted  
**Date:** 2026-11-15  
**Task:** [T-039](../tasks/T-039-event-reminders-android.md)

## Context

The family wants notifications about events and birthdays. Each parent wants them at a different
time (or not at all), and the Picfort server has no push channel to phones (no Firebase, by design:
"without any cloud service", ADR 0001). Owner choices (2026-11-15): default reminders set once in
Settings plus an override on any event; every family event notifies, whoever it is for.

## Decision

- **Planned on the phone, stored on the phone.** Defaults per kind (events without a time, with a
  time, birthdays) and per-event choices live in the app's preferences; nothing goes to the server.
- **One reminder per event**, for its current occurrence (the server's `occurrence_start`). Recurring
  events need no repeat logic: the next refresh after an occurrence brings the next one.
- **Re-planned** whenever the upcoming list, a default, a choice or a member's name changes, and by a
  WorkManager job every 6 hours (network required), so events added by others are reminded without
  opening the app. An empty list before the first successful load never cancels anything.
- **AlarmManager**: `setExactAndAllowWhileIdle` when exact alarms are allowed (`SCHEDULE_EXACT_ALARM`,
  which Settings offers to turn on), `setAndAllowWhileIdle` otherwise (a few minutes late in Doze).
  `USE_EXACT_ALARM` is not used: it's meant for alarm-clock and calendar apps.
- The plan (title and text included) is stored, so the alarm receiver shows the notification without
  network and a boot / time zone / app update receiver re-arms it. Shown reminders are remembered
  (event, occurrence, time), so a re-plan never repeats them, while a new preset is a new reminder.
  An armed one missed while the phone was off still comes up to 12 hours late, if the event hasn't
  started; a time already past when first planned (a new event, a new preset) gives nothing.

## Consequences

- A reminder for an event added less than ~6 hours before its time may not fire if the app isn't
  opened; there is no server push.
- Choices are lost on reinstall or a new phone (defaults reset), like other per-phone settings.
- Android 13+ needs the notification permission: the Events tab asks once, Settings shows when it's off.
