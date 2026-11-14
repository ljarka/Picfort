# T-060 — Search: the people row keeps its order

**Status:** done (5da8590)  
**Component:** Android app (`:feature:timeline`)  
**Depends on:** T-057

## Summary

Picking a person in Search moved them to the front of the "People" row, so the others shifted
under the finger. Now nobody moves: the row is the first 12 people in Immich's order, and a person
picked under "All people" (not among those 12) joins at the end, on the right.

## Design

- `peopleRow(people, added)`: `people.take(12)` + `added` (in the order picked, those not in the 12).
- `addedToRow` keeps `SearchUi.addedToRow`; `SearchViewModel.togglePerson` appends on pick. Dropping
  a person keeps them in the row (no jump) until the screen is closed.
- When `addedToRow` grows, the row scrolls to its end (`animateScrollToItem`), so the new person is
  seen. `MemberCarousel` (`:core:ui`) takes an optional `LazyListState` for this.

## Acceptance criteria

- [x] Unit tests: `SearchPeopleRowTest` (order kept, picks from All people at the end, re-pick no duplicate).
- [x] Paparazzi `search_people_nothing` re-recorded (Babcia Ela stays in her place).
- [x] Device check (2026-11-14, emulator, test account): Osoba 05 picked and dropped stays 5th;
  Osoba 14 from "All people" appears after Osoba 12; `maestro/search-people.yaml` passes.
- [x] Auto-scroll: after Done the row shows Osoba 14 without swiping; picking Osoba 12 in the row
  doesn't scroll. Asserted at the end of `maestro/search-people.yaml`.
