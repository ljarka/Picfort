# T-006 — Backup: fix Android 12+ 10-minute background limit

**Status:** in-progress (code `0e173d1`; left: a run longer than 10 minutes on a real phone, see below)  
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

## What was already there (2026-11-14)

Earlier backup tasks had done most of this with WorkManager instead of a separate service:
`BackupWorker` calls `setForeground` (type `dataSync`, "Backing up to Immich · 12 of 340"), so
a run started from the app is a foreground service and not held to the 10-minute job limit;
"Back up now" and Stop in Settings came with T-063. A run that starts in the background on
Android 12+ may not get the foreground service; it then stops after ~10 minutes and the next run
continues (README, Known limitations).

T-006 adds:
- **Stop on the notification** (`BackupStopReceiver` → `BackupScheduler.stop()`, as in Settings).
- **A summary when "Back up now" ends** ("Backup complete · Uploaded 20", or "Backup didn't
  finish" with the reason), `backupFinishedTitle`. Scheduled runs stay quiet.
- **5-minute timeouts for `POST /api/assets`** (`UploadTimeoutInterceptor`). Found on the
  emulator: on a slow uplink OkHttp has "written" the body once the send buffer took it, and the
  default 10 s read timeout ran out before Immich answered, so the run retried forever.

## Acceptance criteria

- [x] "Back up now" button in Settings
- [x] Tapping it starts the foreground service and shows a notification
      _Emulator: `SystemForegroundService` `isForeground=true`, `types=0x1` (dataSync)._
- [x] Notification shows real-time upload count
- [x] Stop button in notification stops the service
      _Emulator: tapped on the notification; Settings shows "Last backup: Stopped"._
- [ ] Backup runs beyond 10 minutes without being killed
      _Emulator, uplink throttled to 100 kb/s: a 7-minute run of 20 photos finished in the
      foreground. Longer runs couldn't be made reliably: below ~100 kb/s ordinary calls time out,
      the tunnel in front of the test Immich ends a single upload after ~2.7 min (HTTP 524), and
      the emulator doesn't throttle traffic to the host. Left: a first backup of many photos on a
      real phone (screen off), checking it goes on past 10 minutes._
- [x] Scheduled background backup still works as before (same worker; no notification at the end)
- [x] On completion, notification summarizes result
      _Emulator: "Backup complete · Uploaded 20" / "Home network · Uploaded 1"._
- [x] Unit tests: `BackupFinishedTest`, `UploadTimeoutTest`.
