# T-039 — Event & birthday reminders (Android notifications)

**Status:** in-progress (code done in 707a7e6; emulator checked, a real phone in Doze left)  
**Component:** Android app (`:feature:events`, `:app`)  
**Decision:** [ADR 0015](../decisions/0015-event-reminders-on-the-phone.md)  
**Depends on:** T-023 (Events UI), T-037 (Birthdays & repeating events)

## Summary

Set local reminders for upcoming family events and birthdays. Reminders fire local Android
notifications with sound and vibration, opening the event detail on tap.

## Why

Trips, doctor visits, school events and birthdays require timely alerts so family members don't
forget them or have time to prepare (e.g. buying a gift days before a birthday).

## Design

### Setting a reminder
In the event detail screen (and event form), a **Reminder** row with a bell icon [🔔]:
- **Non-timed event:**
  - "None" (default)
  - "On the day at 09:00"
  - "1 day before at 19:00"
  - "Custom date & time..."
- **Timed event (e.g. 17:30):**
  - "At start time (17:30)"
  - "15 minutes before (17:15)"
  - "1 hour before (16:30)"
  - "On the day at 09:00"
  - "Custom..."
- **Birthday event (`kind == birthday`):**
  - "On birthday at 09:00" ("Today is Ola's birthday! Turns 9 🎉")
  - "3 days before at 18:00 (Time to buy a gift!)"
  - "1 week before"

Reminders are **per-device / per-user** (stored locally on the phone): each parent decides when
they want to be notified without spamming others.

### Notification
- **Channel:** `event_reminders` ("Event reminders", `IMPORTANCE_HIGH` with sound/vibration).
- **Title:** Event title (e.g. "Ola's birthday 🎉" or "Dentist appointment").
- **Body:** Relative time & detail (e.g. "Starts at 17:30 · Przychodnia Zdrowie" or "Ola turns 9 today!").
- **Tap action:** Opens `Screen.EventDetail(eventId)`.
- **Dismiss / Action:** Quick "Open" action button.

## Technical implementation

1. **Scheduling:**
   - Android `AlarmManager` with `setExactAndAllowWhileIdle()` for punctual alerts.
   - If exact alarm permission is missing (`Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()`),
     request `SCHEDULE_EXACT_ALARM` or fallback to `setAndAllowWhileIdle()`.
2. **Persistence:**
   - Store active reminder records in Room database or persistent store:
     `EventReminder(eventId, triggerTimestampEpochMs, reminderType)`.
3. **Device Reboot:**
   - `BootReceiver` listening for `ACTION_BOOT_COMPLETED` and `ACTION_TIME_CHANGED` / `TIMEZONE_CHANGED`:
     reschedules all upcoming active alarms.
4. **Series / Recurring events:**
   - When a reminder for occurrence N fires or is dismissed, automatically reschedule the alarm for
     occurrence N+1 (e.g. next year for a birthday, next week for lessons).

## As built (2026-11-15)

Owner choices: **defaults + per-event override** (no need to set anything), **every family event**
notifies, whoever it's for. Changes from the design above:

- Defaults in Settings → "Event reminders": events without a time (the day before at 19:00), with a
  time (1 hour before), birthdays (on the day at 9:00); each can be "No reminder".
- Presets: at the start time, 15 min / 1 hour before (timed only), on the day at 9:00, the day before
  at 19:00, 3 days / a week before at 18:00. **No custom date & time** and **no row in the event form**
  (the detail's "Reminder" row covers a new event right after saving). One reminder per event.
- Stored in preferences, not Room (no migration; ADR 0015). No "Open" action button: tapping opens it.
- Next occurrence of a series: brought by the refresh (in-app or the 6-hourly worker), not by the
  fired alarm.

## Acceptance criteria

- [x] Reminder presets available in Event detail (not in the form, see above).
- [x] Birthday events offer gift-buying advance reminder (3 days before).
- [ ] Notification fires reliably at the chosen time while phone is in Doze mode — exact when allowed;
  on the emulator (API 37, exact alarms off) it came on time; Doze on a real phone left for the owner.
- [x] Tapping the notification navigates directly to the event details screen
  (`maestro/events-reminders.yaml`, from the home screen).
- [x] Rebooting the device preserves and reschedules future reminders (`ReminderRearmReceiver`).
- [x] Deleting an event cancels its scheduled alarm and dismisses any active notification.
- [x] Recurring events automatically schedule the alarm for the next occurrence after firing.
