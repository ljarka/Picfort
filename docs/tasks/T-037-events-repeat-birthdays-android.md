# T-037 — Events: repeat and birthdays (Android)

**Status:** done (f750b10, f2ed6df; checked on the emulator 2026-11-12)  
**Component:** Android app  
**Depends on:** T-023 (event screens), T-035, T-036 (API), T-034 (members with names). Album linking is T-026 and
works for these events too (a series has one album).

## Summary

The event form gets **Repeat**; the list and detail show occurrences; birthdays come from Family settings
and look like events with an age.

## Mockup first

`docs/wireframes/events-repeat-v2.png`: the form with Repeat and its end date, the list with a repeating
event and a birthday ("Ola's birthday · turns 9"), the detail of a birthday. It extends `events-v1.png`.

## Design

- **Form:** a Repeat row: "Doesn't repeat" / "Every week" / "Every month" / "Every year", and, when set, "Ends:
  never / on a date". Saves `repeat` and `repeat_until`.
- **List and detail** read `occurrence_start` / `occurrence_end`, not `start_date`. A repeating event shows a
  small repeat icon and the label ("Every year").
- **Birthdays** (`kind = 'birthday'`): title "Ola's birthday" from the member's current name (fallback: the
  event title); the second line "turns 9" when `turns` is set. The cake icon replaces the date badge's
  dot. Tapping opens the detail with "Edit in Family settings" instead of Edit and no Delete.
- **Today panel** (T-027) uses the occurrence dates and the same birthday text. `todayPanel` takes the
  occurrence, not `start_date` (small change, keeps its tests).
- Editing a series says "Changes all events in the series."
- Old server (no `repeat` fields): the Repeat row is hidden.

## Acceptance criteria

- [x] A weekly event shows its next occurrence; after that day passes it moves to the next one (also at midnight).
- [x] A yearly birthday of a member shows "turns N" and the right date; renaming the member changes the title.
- [x] A series with an end date moves to Past after the last occurrence.
- [x] Birthday events can't be edited or deleted in Events (the app and the server both refuse).
- [x] Today panel shows a birthday on the day, with the age.
- [x] Unit tests: occurrence-based grouping and labels, birthday text; Paparazzi: form with repeat, list, birthday detail, dark.
- [x] README Features; CI passes.

## Implementation notes (2026-11-12)

- Old-server fallback (hiding Repeat) left out: one user, one server, always current.
- Birthday cake sits in the date badge; a repeating event gets a "⟳ Every week" line under its place.
- Detail of a birthday: "Turns 9", the date, and "Edit in Family settings" (no Edit, no Delete).

## Device check (2026-11-12, test server on the current image)

- Weekly series started 4 Nov shows its next occurrence (18 Nov, "in 6 days", "Every week"); edit says
  "Changes all events in the series." A weekly series that ended 5 Nov is in Past.
- Birth date set in Family settings → "Ola's birthday · turns 9" with the cake; renaming the member renames it.
- Birthday detail has no Edit/Delete; the server refuses both (`test_birthday_event_patch_and_delete_rules`).
- Fixed on the way (f2ed6df): "Edit in Family settings" crashed (navigated to the companion object);
  the today panel hid a birthday behind "+1 more" when an older untimed event shared the day.
- Midnight rollover is covered by `EventsRepositoryTest`, not watched on the device.
