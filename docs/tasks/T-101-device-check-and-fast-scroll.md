# T-101 — Device check of today's photo features; fast scroll fixes

**Status:** done (`3055119`)  
**Component:** Android (`:feature:timeline`), Maestro  
**Depends on:** T-094, T-097, T-100

## Summary

1. **Maestro** `maestro/best-and-tags.yaml` (test account, test Immich): `test-data.js BEST=1`
   tags 3 photos of one month `ai/best/frame-worthy`; the flow finds the "Best of the month"
   strip (tiles now carry that content description), opens a photo, Details shows the "Best" chip,
   tapping it opens Search with the 3 photos. Passed on `Medium_Phone_API_37.0`.
2. **Fast scroll vs. the Best strip:** markers still point at month headers and every row up to
   the next marker belongs to that month (unit test); checked by dragging on the emulator.
3. **Fast scroll fixes:**
   - The pill stopped under the floating tab bar at the end of the track, out of reach: the
     scroller now gets `navigationBarsPadding().aboveFloatingTabBar()` (seen on the emulator).
   - Dragging past either end built up a dead zone: the offset is kept on the track
     (`fastScrollIndex`, unit tests).
   - The label could show a month that was gone after a filter or sync: keyed on the months.
   - A light haptic tick per month while dragging.

## Not covered

Memories (T-096/T-098) need the Wall's frame card, which needs `PICFORT_API_KEY` on the server;
the test server has none and its app setup sends no key. Enabling it means changing the test
server and the login subflow for every flow: left for the owner to decide.

## Acceptance criteria

- [x] `best-and-tags.yaml` green on the emulator.
- [x] Fast scroll: unit tests + manual drag to the end and back on the emulator.
- [x] All checks green.
