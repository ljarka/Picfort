# T-006 — Backup: fix Android 12+ 10-minute background limit

**Status:** ready  
**Component:** Android app  
**Depends on:** —

## Summary

On Android 12+, WorkManager background tasks started without user interaction
are stopped after ~10 minutes. Large backups span multiple runs. Fix this by
running backup as a foreground service when the user explicitly starts it.

## Why

Listed in README as a known limitation. Users with many photos never complete
a full initial backup.

## Design

### Two backup modes

1. **Background (scheduled)** — existing WorkManager periodic task; fine for incremental
   daily backups (small number of new photos); stops at 10 min limit but resumes next run.

2. **Foreground (user-initiated)** — new "Back up now" button in Settings launches a
   `ForegroundServiceCompat`-based worker that shows a persistent notification with progress.
   Not subject to the 10-minute limit. Runs until complete or user cancels.

### Implementation

- Create `BackupForegroundService : Service` that calls the existing backup engine logic
- Notification: "Backup in progress — 234/1200 photos" with a progress bar and Cancel action
- `startForeground()` with `ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC`
- On completion: notification updates to "Backup complete — 1200 photos uploaded"
- On cancel: graceful stop (finish current upload, then stop)

### Permissions

`android.permission.FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` already
required; add to manifest if not present.

## Acceptance criteria

- [ ] "Back up now" button in Settings
- [ ] Tapping it starts the foreground service and shows a notification
- [ ] Notification shows real-time upload count
- [ ] Cancel button in notification stops the service
- [ ] Backup runs beyond 10 minutes without being killed
- [ ] Scheduled background backup still works as before
- [ ] On completion, notification summarizes result
