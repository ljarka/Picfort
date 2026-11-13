# T-056 — Events: a birthday's year opens Photos on that day

**Status:** done (5df6956; mockup `docs/wireframes/events-birthday-to-timeline-v1.png` approved 2026-11-13)  
**Component:** Android app (`:feature:events`, `:feature:timeline`, `:app`)  
**Depends on:** T-055 (birthday photos), ADR 0011 (amendment 2026-11-13)

## Summary

In a birthday's photos, each year's title ("Turned 36 · 17 Nov 2025 ›") opens the Photos timeline
scrolled to that day, so the photos around the birthday are one swipe away.

## Design

- The whole title row is tappable, with a chevron; TalkBack: "Show 17 Nov 2025 in Photos".
- `:app` pushes `Screen.Timeline(date)` on top of the event: the Photos tab, with its tab bar.
  Back returns to the birthday. Switching tabs from there pops it without saving, so the event's
  tab never restores into Photos.
- The timeline scrolls once (flag in `rememberSaveable`) to `TimelineLayout.rowIndexOf(day)`: the
  row with the day's newest photo, or the month header right above it when that row starts the
  month. A day without photos lands on the next older ones. Near the end of the timeline the list
  can't scroll further, so the day shows lower on the screen.

## Acceptance criteria

- [x] Tapping a year opens Photos at that day; Back returns to the birthday.
- [x] Switching to Events from there shows the Events list, not Photos.
- [x] Unit tests for `rowIndexOf` (day in a row, first row of a month, no photos, past the end, empty).
- [x] Paparazzi re-recorded (chevron); module AGENTS.md, ADR 0011 amendment.

## Device check (2026-11-13, emulator, test server, test account)

- `maestro/events-birthday-photos.yaml` extended: "Born · 22 Dec 2022" → "December, 2022" in
  Photos → Back → the birthday; again → Events tab → "Upcoming". Passed.
