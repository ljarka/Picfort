# T-109 — Server & timeline performance and reliability (P1)

**Status:** ready  
**Component:** Picfort server (`picfort-server/`), Android (`:feature:timeline`)  
**Origin:** Code review of T-088..T-107 and earlier server/Android foundations (2026-11-28).

## Summary

Improve SQLite concurrency, HTTP connection reuse, background worker thread safety, and Android
timeline sync/scroll smoothness.

## Items to address

### 1. SQLite WAL mode & safe pre-migration backups
- `picfort-server/store.py`:
  - Enable `PRAGMA journal_mode = WAL` and `PRAGMA synchronous = NORMAL` in `_init_schema()` so
    Waitress threads, `PhotoAiWorker`, and `Slideshow` do not block readers during writes.
  - Replace `shutil.copy2(self.db_path, backup)` in `_migrate()` with `sqlite3.Connection.backup()`
    so pre-migration backups are always consistent with WAL enabled.

### 2. HTTP connection pooling & `PhotoAiWorker` concurrency cleanup
- `picfort-server/immich.py` & `picfort-server/photo_ai.py`:
  - Use a shared `requests.Session()` in `ImmichClient` and `OllamaAnalyzer` for TCP/TLS reuse.
  - Guard shared `PhotoAiWorker` state (`running`, `last_error`, `_member_clients`, `_server_user`)
    with a `threading.Lock()`.
  - Fix duplicate `self.store.stale_best_tags()` call in `reconcile_best_tags()`.
  - Remove the blocking `while self.fill_unknown_owners(limit=200): pass` loop in
    `PhotoAiWorker.start()` — `run_batch()` already backfills owners incrementally without stalling
    new photo analysis at startup.
  - Chunk `IN (?, ...)` queries in `PhotoAiStore` (`existing_ids`, `owners_of`, `scenes_for`,
    `best_cover`) in batches of <= 500 IDs.

### 3. Decouple `SyncTimelineSource` refresh intervals & incremental AI labels
- `feature/timeline/.../SyncTimelineSource.kt`, `picfort-server/app.py`:
  - Currently `lastOcrRefreshAt` (in-memory only) gates OCR, Best tags, and AI labels together, and
    only updates when `refreshDocumentsQuietly()` succeeds.
  - Track separate timestamps (persisted across process restarts) for OCR, Best, and AI labels.
  - Add `ETag` (or `?since=<analyzed_at>`) to `GET /photo-insights/labels` so the app avoids
    downloading and rebuilding the entire `ai_labels` Room table on every refresh.

### 4. Timeline scroll smoothness & batch cleanup deletion
- `feature/timeline/.../TimelineScreen.kt`:
  - Debounce `backdropUrl` changes (~150 ms via `snapshotFlow`) so fast flings and
    `FastScrollComponent` drags do not trigger a burst of Coil loads and 900 ms full-screen blur
    crossfades for skipped months.
- `feature/timeline/.../CleanupViewModel.kt`, `picfort-server/app.py`:
  - Replace the sequential `assetIds.forEach { insights.deleteRecord(it) }` loop with a batch
    endpoint (`POST /photo-insights/delete-batch`) or bounded parallel requests.

## Acceptance criteria

- [ ] SQLite WAL enabled and tested with pre-migration `Connection.backup()` in `picfort-server/tests/`.
- [ ] `PhotoAiStore` chunking tested with >1000 IDs.
- [ ] `SyncTimelineSource` refreshes OCR, Best, and AI labels independently; `/photo-insights/labels`
      supports `ETag` / `304`.
- [ ] All Python and Android unit/Paparazzi/boundary/ktlint checks green.
