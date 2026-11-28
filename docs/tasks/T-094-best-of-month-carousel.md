# T-094 — Timeline: "Best of the month" strip at the top of each month

**Status:** done (`16f6958`)  
**Component:** Android (`:feature:timeline`, `:core:database`)  
**Depends on:** T-089 (`ai/best/frame-worthy` tag in Immich), T-092 (Settings -> Timeline)

## Summary

Each month on the Photos timeline starts, under its header, with a low horizontal strip of the
month's photos tagged `ai/best/frame-worthy` by the Picfort server (ADR 0019). It gives a quick
"highlights" view while scrolling years of photos.

## Design

1. **Data (one fetch, cached):** in `SyncTimelineSource.refresh()`, next to the OCR documents
   refresh (same 24 h cadence, also after a sync reset), look up the tag id of
   `ai/best/frame-worthy` (`GET /api/tags`) and page through `POST /api/search/metadata` with
   `tagIds` (`size` 1000, follow `nextPage`). Store the ids in a new Room cache table
   `remote_best` (`RemoteBestEntity(assetId)`), replaced in one transaction. No tag (tagging
   off, no permissions) -> empty table, no strips. Failures keep the previous cache.
2. **Room:** `ImagesDatabase` 24 -> 25 with an additive `MIGRATION_24_25` (SQL from Room's
   generated `createAllTables`); cleared in `RoomSyncStore.clear()`.
3. **Index:** `TimelineMonth` gets `best: List<TimelineAsset>`: the month's assets (after the
   "hide screenshots" filter) whose id is in the cached set, newest first, at most
   `BEST_PER_MONTH` (12). A month with fewer than `BEST_MIN` (3) gets none, so sparsely analyzed
   months don't look broken. Local-only media never qualifies.
4. **Layout:** `layoutTimeline` adds a `BestRow(month, tiles, height)` right after the
   `MonthHeaderRow` when `best` is not empty. `rowIndexOf` and the fast scroller still land on the
   month header (a `BestRow` above the first tiles counts like the header).
5. **UI:** `BestRow` is a `LazyRow`, 8 dp spacing, rounded corners, no label. All tiles share one
   height (half the width; at most 40% of the height in landscape) and keep their photo's shape:
   portrait narrow, landscape wide (ratio kept within 0.5..1.8, no tile wider than 85% of the
   width so the next one peeks in). Owner feedback 2026-11-28; the first version had ~112 dp
   squares with a "Best" label.
6. **Setting:** Settings -> Timeline "Show best of the month" switch, default on, stored in
   `timeline_prefs` like `hide_non_photos`.

## Acceptance criteria

- [x] Unit tests: best per month (min/max, filtered photos excluded, local excluded), layout puts
      `BestRow` after the header, `rowIndexOf` still lands on the header.
- [x] Paparazzi snapshot `timeline_best` (Settings -> Timeline has no snapshot test).
- [x] `testDebugUnitTest`, `verifyPaparazziDebug`, `ktlintCheck`, `lintDebug`, boundaries green.
- [ ] Owner: on the phone, months analyzed by the server show a strip; turning the switch off
      hides them.

## Notes

The server analyzes ~160 photos a night by default, so older months fill in gradually; raising
`OLLAMA_BATCH_SIZE` for the first pass speeds it up.
