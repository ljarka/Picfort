# T-093 — Review fixes for T-088..T-092 (photo AI worker, frame privacy, tags)

**Status:** done (`84fe55a`)  
**Component:** Server (`photo_ai.py`, `slideshow.py`), Android (`:feature:frame`, `:feature:timeline`, `:core:database`)  
**Depends on:** T-088, T-089, T-090, T-092

## Summary

Fixes found while reviewing the work of 2026-11-28:

1. **No poison pill:** a failure tied to one photo (preview 404, invalid model JSON) is recorded in
   `photo_failures` and the batch goes on; after `MAX_ATTEMPTS` (3) the photo is skipped. Ollama or
   Immich unreachable (connection error, timeout) stops the batch without blaming the photo.
2. **Whole library covered:** the worker reads the newest page, then a deep scan that resumes from
   `worker_state.scan_page` (`MAX_SCAN_PAGES` per batch) and starts over at the end.
3. **Sensitive photos never reach the frame:** `Picker` drops `is_sensitive` assets in every mode
   (album too), whatever `filter_photos` is.
4. **"Best only" + memories keeps the day:** with no frame-worthy photo from that day, the day's
   photos are used, not random "best" ones.
5. **"Keep" returns a photo to the frame:** `slideshow_excluded_ids` ignores low score/cleanup for
   dismissed photos (non-photos and sensitive stay excluded).
6. **Deleted `ai/*` tags:** `ImmichTagger` re-creates tags once when Immich rejects a cached id
   (400/404); Settings -> Timeline warns that the server re-creates `ai/*` tags.
7. **Frame `filter_photos` off by default:** "no camera EXIF" also matches photos from messengers.
8. Small: invalid `OLLAMA_*` values log and fall back instead of crashing startup; additive Room
   migration 23 -> 24 (no full re-sync); unused code and awkward formatting removed.

`photo_ai.db` schema 2 -> 3 (`photo_failures`, `worker_state`), tested migration.

## Acceptance criteria

- [x] Server tests: failing photo, Ollama down, deep scan, tag re-creation, env fallback, v2 -> v3
      migration, sensitive in all modes, best-only memories, kept photo back on the frame.
- [x] `testDebugUnitTest`, `verifyPaparazziDebug` (frame, timeline), `ktlintCheck`, `lintDebug` green.
- [ ] Owner: on the real server, watch one night of the worker (`/photo-insights/status`, logs).
