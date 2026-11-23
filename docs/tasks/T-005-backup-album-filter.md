# T-005 — Backup: filter by album

**Status:** in-progress (code done; screenshot test not run — `:app` module OOMs on this host at compile time; device test left)  
**Component:** Android app  
**Depends on:** —

## Summary

Currently backup uploads every photo and video from the phone. Add the ability to
select specific MediaStore albums (folders) to include or exclude.

## Why

Users may not want to back up screenshots, WhatsApp images, or other folders.
Listed in README as a known limitation.

## Design

### Settings screen

New section "Backup albums" with:
- "Back up all photos" toggle (default on)
- When off: list of discovered MediaStore buckets (folder names) with checkboxes
- Persist selection in DataStore as `Set<String>` of bucket IDs

### Backup engine (`backup/`)

`LocalImagesProvider` already queries MediaStore. Add a `bucketFilter: Set<String>?` parameter:
- `null` → all buckets (current behavior)
- non-null → `WHERE bucket_id IN (...)` added to the query

### Bucket discovery

New function: `fun getAllBuckets(context: Context): List<MediaBucket>` —
query `MediaStore.Images.Media.BUCKET_ID` + `BUCKET_DISPLAY_NAME` with `GROUP BY`.

## Acceptance criteria

- [x] "Folders to back up" row in Settings (taps open the folder-selection dialog)
- [x] Dialog shows MediaStore buckets with checkboxes; all checked by default means back up everything
- [x] Selection persisted in SharedPreferences across app restarts (`selectedBucketIds`)
- [x] `BackupEngine.scanAll()` and `LocalMediaScanner.count()` both respect the bucket filter
- [x] When all folders are checked the selection is treated as "all" (empty set → no filter)
- [ ] *(Owner)* Device test: select a subset of folders, run backup, confirm only those folders' files appear
