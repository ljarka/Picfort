# T-079 — School journal: grades

**Status:** ready  
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

## Done when

- Grades by subject with averages and New markers; opening Grades clears the grade part of the badge.
