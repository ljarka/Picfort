# T-079 — School journal: grades

**Status:** ready  
**Component:** Android (`:feature:school-journal`)  
**Depends on:** T-076, T-077. Spec §4.4.

## Steps

1. Semester chips (1 / 2 / Whole year; default = semester of the newest grade).
2. By subject view (`/grades/subjects`): cards with weighted average, grade chips colored by
   `numericValue` (theme tokens), weight, non-regular kinds as labelled values.
3. Recent view (`/grades`): filters subject, kind, New since last visit (`since` = previous visit
   time, kept in prefs `schoolJournal.lastGradesVisit`), Show removed.
4. New marker: grades whose id appears in grade change events after the seen cursor.
5. Grade detail sheet (all fields).
6. Showing the tab → `markGradesSeen(lastEventId)`.

## Tests

- Semester default, filters → query params, New markers, average "—" for null, removed grades.
- Paparazzi: by subject, recent, sheet, empty, dark.

## Done when

- Both grade endpoints with every filter are reachable from the UI; opening Grades clears the grade part of the badge.
