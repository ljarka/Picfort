# T-039 — Event & birthday reminders (Android notifications)

**Status:** ready  
**Component:** Android app  
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

## Acceptance criteria

- [ ] Reminder presets available in Event detail and Event form.
- [ ] Birthday events offer gift-buying advance reminder (3 days before).
- [ ] Notification fires reliably at the chosen time while phone is in Doze mode.
- [ ] Tapping the notification navigates directly to the event details screen.
- [ ] Rebooting the device preserves and reschedules future reminders.
- [ ] Deleting an event cancels its scheduled alarm and dismisses any active notification.
- [ ] Recurring events automatically schedule the alarm for the next occurrence after firing.
