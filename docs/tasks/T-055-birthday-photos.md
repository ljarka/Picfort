# T-055 — Events: photos from past birthdays

**Status:** done (c7681d7; mockup `docs/wireframes/events-birthday-photos-v1.png` approved 2026-11-13)  
**Component:** Android app (`:feature:events`, `:feature:timeline`, `:core:model`, `:core:ui`, `:core:database`)  
**Depends on:** T-037 (birthdays), T-016 (justified timeline rows), ADR 0011 (amendment 2026-11-13)

## Summary

A birthday's detail shows, under "Edit in Family settings", the photos taken on that day in every
year since the birth: one section per year, newest first, laid out like the timeline. The whole
screen scrolls.

## Design

- Only for birthdays with a known year (`turns`). Days: birth date + N years up to today (today
  included on the day); 29 February falls on 28 February in other years, as on the server.
- Sections: "Turned 8 · 16 Nov 2025" … "Born · 16 Nov 2017"; years without photos are left out;
  none at all: "No photos from these days in Immich yet."
- Photos come from the local copy of the Immich timeline (mine and partners' in my timeline), so it
  works offline; photos only on the phone are not included. Day = Immich `localDateTime`.
- Tap opens the full-screen viewer, which pages through all of them (`source = "event:<id>"`).
- Cross-feature: `DayPhotosProvider` (`:core:model`) implemented by `:feature:timeline`
  (`DayPhotosRepository`); `justify` moved to `:core:ui`.

## Acceptance criteria

- [x] A birthday with photos on past birthdays shows them per year; tapping one opens the viewer.
- [x] No birth year or not a birthday: no section. No photos: the empty text.
- [x] Unit tests: days (incl. 29 Feb, today), grouping, titles; Paparazzi: photos, long, dark, none.
- [x] README, ADR 0011 amendment, module AGENTS.md.

## Device check (2026-11-13, test server v0.8.0, test account)

- Ola's birth date set to 22 Dec 2022 for the check (the test library has photos on 22 Dec 2022 and
  2023): "Turned 1 · 22 Dec 2023" and "Born · 22 Dec 2022", each with its photo; the whole screen
  scrolls. Tapping the first opens the viewer; swiping shows the second. Birth date restored after.
- 12 Nov 2017 (no photos on those days): "No photos from these days in Immich yet." No crashes.
- Repeatable: `maestro/events-birthday-photos.yaml` (Maestro; sets up its own test data, 2026-11-13).
