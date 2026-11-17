# T-079 — School journal: grades

**Status:** done (41ec2c0)  
**Component:** Android (`:feature:school-journal`)  
**Depends on:** T-076, T-077. Spec §4.4.

## Steps

1. Semester chips (1 / 2 / Whole year; default = semester of the newest grade).
2. One view by subject (`/grades/subjects`): cards with weighted average, grade chips colored by
   `numericValue` (theme tokens), weight, non-regular kinds as labelled values; subjects with a
   new grade first. Removed grades are not shown ("Show removed" is for messages only).
   No Recent list, no filters (spec §4.4).
3. New marker: grades whose id appears in grade change events after the seen cursor.
4. Grade detail sheet (all fields).
5. Showing the tab → `markGradesSeen(lastEventId)`.

## Tests

- Semester default, New markers, subject order, average "—" for null.
- Paparazzi: by subject (with New), sheet, empty, dark.

## Result (2026-11-17)

- [x] Semester chips (default = semester of the newest grade), subject cards with average ("—" for
  none), chips colored by band (light and dark), weight, labelled non-regular kinds, New dots, subjects
  with a new grade first, grade sheet with every field. Opening Grades reads the New ids first, then
  marks grades seen (the dot goes, the markers stay for the visit).
- [x] Unit tests (default semester, order, kinds, bands, averages, new ids across pages and without
  removals) and Paparazzi (one child with New, dark, all children in sections, empty semester, sheet).
- [x] Emulator + fake server: All → a section per child, Ola only, a new grade (hook) → dot on Wall and
  the New dot on the chip, sheet, empty Semester 2, dot gone after leaving, no New on the next visit.
- Decided here (not in the spec before): with All and several children, one section per child — the
  server merges subjects and averages across children without `child`. Spec §4.4 updated.

## Done when

- Grades by subject with averages and New markers; opening Grades clears the grade part of the badge.
