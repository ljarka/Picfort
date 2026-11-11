# T-023 — Events: Android screens

**Status:** done (all criteria checked on the emulator 2026-11-11)  
**Component:** Android app  
**Depends on:** T-017 (Events tab), T-022 (API), T-019 (`PicfortServerClient`,
`PicfortAuthInterceptor`; extract them here if T-019 hasn't landed)

## Summary

The Events tab lists family events from the nearest to the furthest, with a switch to past
events, and lets anyone add, edit and (their own) delete events. Linking Immich albums is
T-026.

## Mockup first

`docs/wireframes/events-v1.png` (list, detail, form), approved by the owner before coding.

## Screens

### Events (tab root)

```
┌─────────────────────────────┐
│ Events                [⚙]   │
│ [ Upcoming ] [ Past ]       │  SegmentedButton
│ NOW                         │
│ ┌ 10–14 NOV ┐ Mazury trip   │  ongoing events first
│ └───────────┘ Giżycko       │  location, if any
│ DECEMBER 2026               │  month headers (month of the start date)
│ ┌ 24 DEC ┐ Christmas Eve    │  "in 43 days" · 18:00 if timed
│ JANUARY 2027                │
│ ┌ 3–10 JAN ┐ Skiing         │
│                     (+)     │
└─────────────────────────────┘
```

- **Upcoming** (`GET /events?after=<today>`): ongoing events under "Now" (start ≤ today ≤
  end; a one-day event today is "Today", not "Now"), then by start date. Relative label:
  "today", "tomorrow", "in N days" (up to 60 days; none after that).
- **Past** (`GET /events?before=<today>`): latest first, month headers, 50 per page, more
  loaded on scroll. Label: none.
- "Today" is the phone's local date. The split is recomputed on every load: on open, on
  resume, every 60 s while visible (so it is right after midnight), and on pull-to-refresh.
- The chosen segment survives tab switches; the app starts on Upcoming.
- Empty upcoming: "Nothing planned yet. Add a trip or a birthday." Empty past: "No past
  events."
- Dates are formatted for the device locale; a range within one month is "10–14 Nov", across
  months "28 Dec – 3 Jan". The year is shown when it is not the current one.

### Event detail

Title, date (range), time, location (tap → `geo:0,0?q=<location>` intent; ignore
`ActivityNotFoundException`), note, "Added by <name>" (unknown id → "Former member"). Top
bar: Edit, Delete (creator only, with confirmation). A slot for the album section (T-026).

### Add / edit event (full screen)

Title*, start date*, end date (optional), time (optional), location, note. Save is enabled
with a title and a start date.

- Dates: M3 `DatePickerDialog`; the end date can't be before the start (moving the start
  past the end clears the end). The picker works in UTC millis: convert with `ZoneOffset.UTC`
  to `LocalDate`, or the date shifts by one day in some zones.
- Time: M3 `TimePicker` dialog, 24 h per device setting, with "Clear".
- Leaving with unsaved changes asks "Discard changes?".
- Save: `POST` / `PATCH` (only changed fields; cleared ones as `null`). While saving the
  button shows progress; an error keeps the form open with a snackbar.

## Data

- `EventsApi` on `PicfortServerClient` (as `ShoppingApi`, T-019); `EventsRepository`
  (`@Singleton`) with a `StateFlow` per segment; T-027 reads the upcoming one from the wall.
  Event detail is a destination any tab can navigate to. Not optimistic: events change rarely, so the list reloads after a
  successful save or delete.
- Errors as the other Picfort features (`401`, `502`, wrong key); `404` on `/events` itself →
  "This Picfort server has no events yet, update it". An event deleted by someone else
  (`404` on open or save) → snackbar "This event no longer exists" and back to the list.
- Offline: snackbar, the loaded events stay visible; no copy on disk.

## Acceptance criteria

- [x] Add, edit, delete (own) events; delete is not offered on someone else's event; anyone
      can edit.
- [x] One-day, multi-day and timed events display correctly in the list and the detail.
- [x] Upcoming: ongoing first, then nearest first; an event that ended yesterday is only in
      Past; an event ending today is still in Upcoming.
- [x] Past shows the latest first and loads more on scroll.
- [x] With the screen open over midnight, yesterday's event moves to Past within a minute.
- [x] An event added on another phone appears within 60 s while the tab is open.
- [x] The date picked is the date saved (check with the phone set to a zone west of UTC).
- [x] No server address → the shared empty state from T-017.
- [x] TalkBack: a row is read as "<title>, <dates>, <relative label>"; the date badge is not
      read separately.
      _Fixed 2026-11-11: the row uses `clearAndSetSemantics`, so it is one button with "<title>, <dates>, <relative label>". Checked in the accessibility tree, not by ear._
- [x] Unit tests: upcoming/now/past grouping, relative labels, date range formatting, picker
      millis → `LocalDate`.
- [x] Paparazzi: upcoming (with Now), past, both empty states, detail, form, one dark.
- [x] README Features and screenshots; CI passes.

## Out of scope

- Albums on events (T-026).
- Reminders/notifications and calendar export (owner decision 2026-11-11: not in v1).
- Recurring events and birthdays: T-035 to T-037. Events on the frame (v2 candidate).
