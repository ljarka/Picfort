# T-069 — School: no flicker after editing on Saturday or Sunday

**Status:** done (c5879a0)  
**Component:** Android app (`:feature:school`)  
**Depends on:** T-029

## Summary

Owner report (2026-11-14): editing a timetable, choosing Saturday or Sunday and tapping Done made the
School screen flicker. Reproduced on the emulator: it alternated between Monday and Friday for good.

## Cause

The weekend chip shows only while editing (or with entries). After Done the chosen day (Saturday) had
no chip: the ViewModel showed Monday but kept Saturday as the chosen day, and the pager (7 → 5 pages)
clamped to Friday. Its "swipe picks the day" effect chose Friday while the "day moves the pager"
effect scrolled to Monday, forever.

## Fix

- `shownDay(day, today, days)`: the chosen day if it has a chip, else today or Monday; the ViewModel
  stores that fallback as the chosen day, so there is one day.
- `SchoolScreen`: the pager, its effects and the day chips sit in `key(days)`: a new list of days gets
  a new pager on the chosen day (keeping the old one crashed with an index out of bounds).

## Acceptance criteria

- [x] `maestro/school-family.yaml`: Edit → Sat → Done: the Saturday chip goes, the screen settles,
  a Tue tap sticks (Art shown).
- [x] Unit test `saturdayChosenWhileEditingFallsBackWhenItsChipGoes`.
- [x] `school.yaml`, unit tests, Paparazzi, ktlint, lint, boundaries green.
