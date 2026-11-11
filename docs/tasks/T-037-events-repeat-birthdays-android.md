# T-037 — Events: repeat and birthdays (Android)

**Status:** ready (first step: mockup for the owner to approve)  
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

- [ ] A weekly event shows its next occurrence; after that day passes it moves to the next one (also at midnight).
- [ ] A yearly birthday of a member shows "turns N" and the right date; renaming the member changes the title.
- [ ] A series with an end date moves to Past after the last occurrence.
- [ ] Birthday events can't be edited or deleted in Events (the app and the server both refuse).
- [ ] Today panel shows a birthday on the day, with the age.
- [ ] Unit tests: occurrence-based grouping and labels, birthday text; Paparazzi: form with repeat, list, birthday detail, dark.
- [ ] README Features; CI passes.
