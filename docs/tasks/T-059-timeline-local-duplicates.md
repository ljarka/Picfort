# T-059 — Photos: phone copies already in Immich show once

**Status:** done (85cdf30)  
**Component:** Android app (`:feature:timeline`, `:core:database`)  
**Depends on:** ADR 0005 (sync stream), T-015

## Summary

The timeline merges Immich assets with the phone's media. Phone media was hidden only when
Picfort's own backup had linked it to an Immich asset, so photos uploaded another way (Immich's
app, or before Picfort's backup ran) showed twice. Now any phone photo already on the server is
left out and shows once, as the Immich copy.

## Design

- `localIdsOnServer` (`ui/timeline/LocalDuplicates.kt`): a phone file is on the server when
  1. Picfort's backup linked it to an asset in the synced copy (as before);
  2. its SHA-1 from `local_backup` (hex) equals an asset's `checksum` (base64), also partners';
  3. an asset has the same file name (case-insensitive) and the same time taken: the phone's
     wall-clock time vs `localDateTime`, equal up to 1 s or apart by a time zone offset (whole
     quarter hours, at most 14 h), since the phone reads the date in the current zone.
- `RemoteAssetEntity.originalFileName` from `AssetV2` (Room v23; destructive migration, re-sync).
- `buildTimelineIndex(…, onServerLocalIds, …)` leaves those out.

## Acceptance criteria

- [x] Unit tests: name + time match (case, sub-second, zone shift), different time/year not matched,
  checksum and backup-link match, hex → base64 (`LocalDuplicatesTest`).
- [x] Device check: a photo uploaded with Immich's app shows once on the timeline.

## Device check (2026-11-14, emulator, test account)

- Phone: `T059_SAME.jpg` (red, EXIF 12:00 +01:00) and `T059_PHONE_ONLY.jpg` (green). Immich:
  `T059_SAME.jpg` with other pixels (blue, so another checksum), uploaded straight through the API.
- Before (old build): red and blue both on the timeline. After: blue only, green still there.
  Backup was off, so the match came from name + time. Test files removed afterwards.
- Note: MediaStore leaves `datetaken` empty for a JPEG without `OffsetTimeOriginal`.
