# T-063 — Backup: right count, the upload queue, Stop

**Status:** done (bdab0cf)  
**Component:** Android app (`:app`, `:feature:timeline`, `:core:database`)  
**Depends on:** T-059

## Summary

- "0 of 1195 phone items are in Immich" counted only files Picfort's backup had linked; photos
  uploaded by Immich's app (or before this backup ran) counted as missing. Now it also counts the
  timeline's match (T-059: checksum, file name + time).
- While a backup runs, a row of thumbnails under "Back up now" shows the queue: the file being
  uploaded first, with its progress, then the waiting ones; each leaves once done.
- "Stop" ends a running or waiting backup.

## Design

- `TimelineRepository.phoneOnServer` (local ids matched by `localIdsOnServer`) and
  `loadPhoneMediaIfNeeded()` (Settings opened before Photos). `countInImmich` = backup links ∪
  timeline matches, at most the phone's count; recounted when the match changes.
- `BackupQueue` (singleton, the worker runs in the app's process): `start` with the to-do list,
  `uploading`/`progress` (from the request body, in whole percents), `finished` per file and per
  batch, `clear` in `finally`. `BackupQueueRow`: `LazyRow` of MediaStore thumbnails, slide only
  (fading tiles overlapped when small files went fast).
- "Backing up… X of Y" takes the further of the worker's batch report and the queue (`backupDone`).
- `BackupScheduler.stop()`: cancels the one-off and the periodic work, schedules the periodic one
  again with a 6 h initial delay (else WorkManager starts it at once), last result "Stopped"; a
  stopped worker doesn't overwrite it.

## Notes

- A phone file matched by name + time but with other bytes (e.g. edited) is still uploaded:
  backup goes by checksum, and keeping both is the safe side.
- `connectedAndroidTest` is not involved; the emulator's 150 test photos are now on the test account.

## Acceptance criteria

- [x] Unit tests: `BackupQueueTest` (order, finished, late progress, count union, progress from queue).
- [x] Paparazzi `settings`: queue row with an upload in progress, Stop.
- [x] Emulator (test account): a phone photo uploaded by another app counts ("1 of 151", was 0);
  backup of 151 + 15 files: queue shrinks file by file, big files fill the ring, "166 of 166" at the
  end; Stop during a periodic run: "Stopped", no restart within 20 s. Test files removed.
