# T-098 — Memories never show photos the AI marked sensitive

**Status:** done (`f2eea60`)  
**Component:** Server (`/photo-insights/labels`), Android (`:feature:timeline`, `:core:database`)  
**Depends on:** T-093 (sensitive never on the frame), T-096 (Memories)

## Summary

Memories open from the shared Family Wall, so they must follow the frame's rule: photos the
photo AI marked `is_sensitive` (ID cards, bank or medical documents, intimate moments) never
show. A memory left without photos disappears.

## Design

- Server: `GET /photo-insights/labels` (Immich token, like the other `/photo-insights`) returns
  every analyzed asset as `[id, category, sensitive 0/1]`, compact (~1.5 MB for 30k photos).
- App: `SyncTimelineSource.refreshAiLabelsQuietly()` (daily, with the OCR/Best refresh) stores
  them in Room `ai_labels` (`AiLabelEntity`, DB v26, additive `MIGRATION_25_26`); failures keep
  the cache. Works for ordinary photos too (they have no Immich tag).
- `MemoriesViewModel` drops `aiLabels().sensitive` ids (`withoutSensitive`) before building sections.

## Acceptance criteria

- [x] Server test: labels endpoint (token required, shape). App test: sensitive photos dropped,
      empty memory gone, photo count updated.
- [x] All checks green.
- [ ] Device: Memories need a Wall frame card, which the test server (no `PICFORT_API_KEY`) lacks;
      owner checks on the family setup.

## Notes

Photos not analyzed yet are not filtered; the first big AI pass (from 2026-11-28) covers the library.
