# T-029 — School timetable: Android

**Status:** done (f9b2775)  
**Component:** Android app  
**Depends on:** T-017 (School tab), T-028 (API), T-019 (`PicfortServerClient`,
`PicfortAuthInterceptor`, the offline-copy pattern)

## Summary

The School tab shows one child's timetable at a time: when the child finishes today, and the
week day by day, with extracurricular activities next to the lessons. Any family member can
enter and change the timetable; entering a whole week must be quick.

## Mockup first

Skipped: on 2026-11-14 the owner asked to implement the tab straight away; the Paparazzi
snapshots in `feature/school/src/test/snapshots/` stand in for the mockup.


`docs/wireframes/school-timetable-v1.png`: the tab (school day, weekend, no children), the
entry sheet (lesson, activity) and the bell schedule screen. Owner approval before coding.

## Screen (tab root)

```
┌─────────────────────────────┐
│ School              [✎] [⚙] │  ✎ = edit mode on/off
│ (● Ola) (● Staś) (+)        │  child chips; (+) adds a child
│ ┌─────────────────────────┐ │
│ │ Today Ola finishes      │ │  summary card (always about today)
│ │ school at 13:40         │ │
│ │ then Swimming 15:00–16:00│ │
│ │ (Aquapark)              │ │
│ └─────────────────────────┘ │
│ [Mon][Tue][Wed][Thu][Fri]   │  day chips, today marked and preselected
│ 1  08:00–08:45  Maths   r.12│
│ 2  08:55–09:40  Polish  r.7 │  lessons
│ …                           │
│ ★  15:00–16:00  Swimming    │  activity: tonal container + icon, place, note
│      Aquapark · bring swimsuit │
└─────────────────────────────┘
```

- **Child chips:** the child's photo (`photo_url`, through the authenticated Coil loader) or a
  colored dot with the initial, + name, in name order. The chosen child is
  remembered across sessions (preferences); if it no longer exists, the first one. With one
  child the row still shows (it is where children are added).
- **Week:** day chips above a `HorizontalPager`, one page per day, kept in sync (tap a chip or
  swipe). Mon–Fri always; Sat and Sun chips appear only when that child has entries there
  (and always in edit mode). Opening the tab selects today, or Monday when today has no chip.
  Chips rather than a whole-week grid: a phone shows one day legibly, and "today" is the
  common question.
- **Day list:** all entries by start time. Lesson row: period number (blank without one),
  times, subject, room, teacher (second line when present). Activity row: visually distinct
  by container color **and** an icon, with place and note. On today's page, the entry going
  on now is highlighted and finished ones are dimmed.
- Empty day: "No lessons on Wednesday". No children: "Add a child to start a timetable" with
  an **Add child** button. No server address → the T-017 empty state.

### Summary card (selected child, today, phone's local date and time)

With `end` = the latest lesson end today and `start` = the earliest lesson start:

| Situation | Text |
|---|---|
| Lessons today, now < `end` | "Today Ola finishes school at 13:40" (before `start` also a second line "Starts at 08:00") |
| Lessons today, now ≥ `end` | "Ola finished school at 13:40" |
| No lessons today | "No school today" |

Then one line per activity today that hasn't ended: "then Swimming 15:00–16:00 (Aquapark)"
(the first with "then", further ones without; with no lessons: "Swimming 15:00–16:00
(Aquapark)"). The card is recomputed every minute while visible and on resume. A pure
function `schoolDay(entries, today, now)` produces it (unit-tested; T-027 may reuse it).

## Editing

Edit mode (✎) shows the controls; outside it the timetable is read-only, so nothing is
changed by accident.

- **Child:** (+) or long-press on a chip → sheet with name, color (the ten avatar colors),
  **Bell schedule**, and **Delete child** (confirmation naming the child: "Delete Ola and her
  whole timetable?").
- **Bell schedule** (per child): a list of periods with start and end. **Fill in** generates
  it from: first lesson start (08:00), lesson length (45 min), break (10 min), number of
  lessons (8); every time stays editable (long breaks). **Copy from <other child>** when
  another child has one. Saved with `PUT …/periods`.
- **Add entry** (FAB in edit mode, for the day shown) → bottom sheet:
  - Type: **Lesson** / **Activity**.
  - Lesson: period (dropdown with its times, preselected to the first free period that day)
    or "Custom time"; subject with suggestions from that child's other lessons; room;
    teacher (suggested from the last lesson of the same subject).
  - Activity: name, start, end (M3 `TimePicker`), place, note.
  - **Save and add next** keeps the sheet open on the next period, so a day is entered
    without closing it; **Save** closes.
  - Without a bell schedule, the lesson form offers "Set up lesson times first" (opens the
    bell schedule) and "Custom time".
- Tapping an entry in edit mode opens the same sheet with **Delete**.
- Day menu in edit mode: **Copy lessons to…** (pick days; a day that has lessons is
  replaced after a confirmation) → `POST …/copy-day`.

## Data

- `SchoolApi` on `PicfortServerClient`; `SchoolRepository` (`@Singleton`) with one `StateFlow`
  of the children from `GET /school`. Not optimistic: after a successful change the returned
  child replaces the local one. Poll every 60 s while visible; pull-to-refresh.
- **Offline copy** as the shopping lists (T-019): the last `GET /school` response in a JSON
  file, shown read-only with the banner "Offline. Timetable from 14:32"; edit mode is
  disabled. "When does Ola finish?" is asked away from home. Cleared on logout and when the
  server address changes.
- Errors as the other Picfort features; `404` on `/school` itself → "This Picfort server has
  no school timetables yet, update it". A child or entry deleted by someone else (`404`) →
  reload quietly.
- Times are `LocalTime`, weekdays `DayOfWeek`; shown in the device's 12/24 h format; day
  names and the first chip follow the device locale (the list is still Mon–Sun, ISO).

## Acceptance criteria

- [x] Add, rename, recolor and delete a child; the selected child survives an app restart.
- [x] Two children show different timetables; switching is one tap.
- [x] The summary card shows each row of the table above at the right time (before school,
      during, after, weekend), with activities, and changes within a minute of the end time.
- [x] The week is browsable by chips and by swiping; today is preselected and marked;
      weekend chips appear only with entries (or in edit mode).
- [x] Activities are distinguishable from lessons without relying on color alone.
- [x] A full day of 6 lessons can be entered with the bell schedule and "Save and add next"
      without reopening the sheet; "Copy lessons to…" fills other days.
- [x] Changing a period's time in the bell schedule changes those lessons on every day.
- [x] Outside edit mode nothing can be changed.
- [x] Another phone's change appears within 60 s while the tab is open.
- [x] Server unreachable: the last timetable is shown read-only with the banner, also after
      the app was killed; the summary card still works.
- [x] TalkBack: a lesson is read as "Period 1, 8:00 to 8:45, Maths, room 12"; an activity
      starts with "Activity"; chips announce the selected child and day.
- [x] Unit tests: `schoolDay` (each row, activities before/after, midnight, weekend), bell
      schedule generator, first free period, repository offline copy.
- [x] Paparazzi: school day (before, during, after), weekend with an activity, empty day, no
      children, edit mode, entry sheet (lesson, activity), bell schedule, offline banner, one
      dark.
- [x] README Features and screenshots; CI passes.

## Out of scope

- A/B weeks, holidays and days off, substitutions, grades, homework.
- Import from Librus / Vulcan (v2 candidate).
- Notifications and a home-screen widget (T-012 could show the summary later).
- The summary in the Family Wall today panel (T-027): recommended as a small follow-up once
  both tasks are done, one line per child ("Ola 13:40 · Staś 14:35"); owner decision 2026-11-11.
- Linking a child to an Immich user (the column exists on the server; no UI).
- Choosing a child from Immich people, the face photo and browsing the child's photos: T-030
  (the server side is already in T-028).
- Children seeing only their own timetable.

## Done (2026-11-14)

- Module `:feature:school` (see its `AGENTS.md`); `:app` routes the School tab to `SchoolScreen`.
- Checked on the emulator with the test server (`maestro/school.yaml`): add a child, Fill in the
  bell schedule, two lessons with "Save and add next", "Copy lessons to…" Tuesday, read-only
  outside edit mode, delete the child.
- Device check, part 2 (f893361): `maestro/school-family.yaml` (two children, one-tap switch,
  the chosen child after a restart, swiping Mon ↔ Tue, a lesson added through the API shows up
  within 60 s, rename + recolor) and `maestro/school-offline.yaml` (test server stopped: banner,
  last timetable, no ✎). Found and fixed: changes answered with the child (rename, color, bell
  schedule, copy) didn't update the offline copy, so offline showed the old name; and on a
  12-hour clock "8:00 AM–8:45 AM" wrapped (now "8:00–8:45 AM", wider column).
- Choices: period picked with chips (one tap, all visible) rather than a dropdown; "Copy lessons
  to…" warns inside its dialog ("Lessons on Tuesday will be replaced") instead of a second one;
  eight distinct avatar colors (Immich's amber and yellow look the same, "primary" is no color).
