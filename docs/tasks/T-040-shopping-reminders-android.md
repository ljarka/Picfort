# T-040 — Shopping list reminders (Android notifications)

**Status:** ready  
**Component:** Android app  
**Depends on:** T-019 (Shopping list screens)

## Summary

Set a reminder for a shopping list so the phone alerts you before or during your planned grocery
trip, showing the unchecked items right in the notification.

## Why

People plan shopping lists in advance (at home on Wednesday) but need a prompt when it's time to
go to the store (e.g. "Friday at 17:00 after work").

## Design

### Setting a reminder
In the shopping list screen (`ShoppingListScreen`), a bell icon [🔔] in the top app bar:
- Quick presets:
  - "Today at 17:00"
  - "Tonight at 19:00"
  - "Tomorrow at 10:00"
  - "Saturday morning at 09:00"
  - "Custom date & time..."
- When a reminder is already set: shows active time ("🔔 Friday, 17:00") with options to change or
  "Remove reminder".

### Notification
- **Channel:** `shopping_reminders` ("Shopping reminders", `IMPORTANCE_DEFAULT`).
- **Title:** "Time for groceries: <List title>" (e.g. "Time for groceries: Biedronka").
- **Body:** Summary of remaining items:
  - E.g. "5 items left to buy: Milk, Butter, Sourdough bread, Eggs, Apples..."
  - If all items are already checked off: notification is automatically suppressed or cancelled.
- **Actions in notification:**
  - **Open list:** navigates directly to `Screen.ShoppingList(listId)`.
  - **Snooze 1h:** delays the reminder by 60 minutes.

## Technical implementation

1. **Storage:**
   - Saved locally per device in Room / DataStore:
     `ShoppingReminder(listId, triggerTimestampEpochMs)`.
2. **Scheduling:**
   - Android `AlarmManager` with `setAndAllowWhileIdle()` or `setExactAndAllowWhileIdle()`.
   - `ShoppingReminderReceiver` loads the current unchecked items from `ShoppingRepository` cache
     to format the notification text.
3. **Reboot handling:**
   - Shared `BootReceiver` (from T-039) reschedules active shopping reminders after restart.
4. **Auto-cleanup:**
   - When a shopping list is archived or deleted, any active reminder is cancelled.

## Acceptance criteria

- [ ] Bell icon in shopping list top bar opens reminder bottom sheet with presets.
- [ ] Active reminder is clearly indicated with a badge or status text in the list.
- [ ] Notification displays the list title and the actual pending items to buy.
- [ ] Tapping the notification opens the specific shopping list.
- [ ] "Snooze 1h" action postpones the alert by one hour.
- [ ] If all items are checked off before the alert time, the reminder automatically cancels.
- [ ] Device reboot preserves scheduled shopping reminders.
