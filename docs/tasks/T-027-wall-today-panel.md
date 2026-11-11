# T-027 — Family Wall: today panel (date and the day's event)

**Status:** done (merged in PR #44, commit `155d10e`)  
**Component:** Android app  
**Depends on:** T-017 (wall as a tab), T-022 (`GET /events`), T-023 (`EventsRepository`, event
detail)

## Summary

At the top of the Family Wall, above the message carousel, a panel shows today's date and
the most important event of the day. No server change.

## Mockup first

`docs/wireframes/wall-today-panel-v1.png`: the wall with the panel in each state below,
approved by the owner 2026-11-11 before coding. It extends the approved wall mockup
(`family-wall-approved-v4.png`), which has no panel.

## Design

```
┌─────────────────────────────┐
│ Family Wall           [⚙]   │
│ (◯ Mama)(◯ Tata)(◯ Ola) …   │  AvatarRow: fixed (T-002)
├─────────────────────────────┤
│ Wednesday, 11 November      │  TodayPanel: first FullLine item of the grid
│ Mazury trip · Day 2 of 5    │
│ Giżycko            +1 more  │
│ [💬 Mama: "Had fun!"][💬 …→ │  MessageCarousel
│ …photos…                    │
└─────────────────────────────┘
```

- **Placement:** the first `FullLine` item of the wall's `LazyVerticalStaggeredGrid`, before
  the carousel, so it scrolls away with the content (the fixed avatar row already takes
  height; photos need the rest). Also shown above the "No posts yet" state and when the
  carousel is hidden.
- The member filter does not affect the panel.
- **Date:** today in full for the device locale, without the year ("Wednesday, 11 November";
  pattern from `DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")`).
- **Event line(s):** title; then, joined with " · ": the time if set and the event starts
  today ("18:00"), "Day 2 of 5" for a multi-day event, the location. One line each, ellipsis.
- **Tap** on the panel with an event → that event's detail (T-023; back returns to the wall).
  **"+N more"** (other events today) → the Events tab. A panel with only the date is not
  clickable.

### Which event

Among the loaded upcoming events with `start_date ≤ today ≤ coalesce(end_date, start_date)`:

1. Events starting today with a time: the earliest one that hasn't started yet; if all have
   started, the latest one.
2. Ongoing multi-day events (a `start_time` counts only on the start day).
3. One-day events without a time.

Ties: earliest `created`, then lowest `id`. The others today are the "+N more".

### No event today

The next upcoming event, if it starts within 30 days: "Next: Christmas Eve · in 12 days"
("tomorrow" for 1), tap → its detail. Otherwise only the date. (Owner decision 2026-11-11: the
30-day window.)

### States without events data

Only the date, no error and no snackbar, when: the server has no `/events` (`404`), the
events request fails (offline, `401`, `502`), or events haven't loaded yet. The wall's own
errors are unchanged. Without a server address the wall shows the T-017 empty state and no
panel.

## Data

- `TodayPanelState(date: LocalDate, event: Event?, dayOfTrip: Pair<Int, Int>?, moreToday:
  Int, next: Event?, nextInDays: Int?)`, built by a pure function `todayPanel(events, today,
  now)` (unit-tested).
- Events come from `EventsRepository`'s upcoming `StateFlow` (T-023), the same data the
  Events tab shows; `FamilyWallViewModel` asks the repository to refresh on the wall's
  existing 60 s tick and on pull-to-refresh. An events failure never sets the wall's error.
- `today` and `now` are read on every tick, so the panel changes at midnight and when a timed
  event starts, within a minute. Also recompute on resume.

## Acceptance criteria

- [x] The panel is above the carousel, scrolls with the grid, and stays when a member is
      selected, when there are no text posts and when the wall is empty.
- [x] Event today (timed, one-day, multi-day with "Day N of M") shows as specified; the
      choice among several follows the order above and shows "+N more".
- [x] No event today: "Next: …" within 30 days, otherwise only the date.
- [ ] Tap opens the event detail; "+N more" opens the Events tab; back returns to the wall.
- [ ] Old server (`404` on `/events`) or offline: only the date, no error message.
- [ ] An event added on another phone for today appears within 60 s; after midnight the
      date and event change without reopening the app.
- [ ] TalkBack reads the panel as one element: "Today, <date>. <event>, <details>".
- [x] Unit tests for `todayPanel`: each rule and tie, day counting across months, next-event
      window, empty input.
- [x] Paparazzi: event today, multi-day ongoing, several events, next-upcoming fallback, date
      only, dark.
- [ ] README screenshots; CI passes.

## Out of scope

- The panel on the frame / tablet frame mode (T-003).
- Weather, name days, birthdays from Immich people.
- Shopping or spending information in the panel.
- School finish times per child ("Ola 13:40"): a follow-up after T-029, which provides the
  `schoolDay` function (owner decision 2026-11-11).

## Implementation notes

- `ui/wall/TodayPanel.kt`: `TodayPanelState`, pure `todayPanel(events, today, now)`, `TodayPanel` composable.
- `FamilyWallViewModel` injects `EventsRepository`, keeps `FamilyWallUiState.panel` (null without a server),
  refreshes events independently of the posts on the 60 s tick and pull-to-refresh, and swallows events
  errors (the panel keeps what it has, no wall error).
- Layout follows the approved mockup: date, title, then `time · Day N of M · location`; "+N more" on the right.
- Not yet checked on a device: old server (`404`), tap targets, midnight rollover, TalkBack.
