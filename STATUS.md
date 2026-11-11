# Picfort — Project State

## Current Status (2026-11-11)
Mature Android app. Core features complete. Working on improvements.

## Completed features
- Timeline (Immich + MediaStore merged)
- Fast scroll, offline-first cache
- Full-screen viewer with zoom
- Backup to Immich (WorkManager)
- Video support (Media3)
- Albums, search, selection actions
- Send-to-frame (e-paper Waveshare 13.3")
- ImageFrameServer (Python/Flask)

## Task queue

| Task | Description | Priority | Status |
|---|---|---|---|
| PF-001 | Send phone-only photos to frame (no Immich) | high | done |
| PF-002 | Backup: select which albums/folders to back up | high | ready |
| PF-003 | Background backup on Android 12+ > 10 min (WorkManager foreground service) | medium | ready |
| PF-004 | Offline months: show cached placeholder count instead of empty | low | ready |
| PF-005 | Frame slideshow: add "favorites" mode (starred Immich assets) | low | draft |

## Next task
**PF-002**: Backup: select which albums/folders to back up.
- Currently backup uploads all photos.
- Add settings UI to pick which MediaStore buckets/albums are included.
- WorkManager backup job should respect the selection.

## Notes
- `./gradlew testDebugUnitTest` — unit + screenshot tests
- `./gradlew verifyPaparazziDebug` — fails if UI changed without updating screenshots
- After UI change: `./gradlew recordPaparazziDebug` + commit updated snapshots
