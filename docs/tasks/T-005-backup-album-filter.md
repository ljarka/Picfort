# T-005 — Backup: filter by album

**Status:** ready  
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

- [ ] "Back up all photos" toggle present in Settings
- [ ] When disabled, list of phone folders shown with checkboxes
- [ ] Selection persisted across app restarts
- [ ] Backup runs only files from selected folders
- [ ] "Back up all" re-enabled → all photos included again
- [ ] Empty selection → backup uploads nothing (not an error; warn user)
