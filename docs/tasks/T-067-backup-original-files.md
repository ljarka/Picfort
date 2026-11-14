# T-067 — Backup uploads the original files (duplicates, lost locations)

**Status:** done (64a1a04)  
**Component:** Android app (`:app` backup, `:feature:timeline` permissions, `:core:database`)  
**Depends on:** T-005, T-063

## Summary

Owner report: photos appeared twice in Immich after Picfort's backup. Without
`ACCESS_MEDIA_LOCATION`, Android 10+ gives apps the photos with GPS stripped from EXIF. Picfort
hashed and uploaded those bytes: a different SHA-1 than the original Immich's own app uploads, so
Immich didn't see a duplicate, and Picfort's copy had no location.

Reproduced on the emulator (test account): a JPEG with GPS uploaded as the original, the same
file on the phone, Picfort backup → 2 copies (`3dfa31…` with latitude 52.225, `db799a…` without).

## Design

- Manifest `ACCESS_MEDIA_LOCATION`; requested with media access (`RequestMediaPermission`, also
  when only it is missing). On the emulator it was granted without a dialog (media already allowed).
- Backup reads `MediaStore.setRequireOriginal(uri)` for the checksum and the upload.
- Without the permission backup stops with "Open Picfort and allow photo locations" instead of
  uploading stripped copies (`BackupOutcome.NoLocationAccess`).
- Once (`BackupSettingsStore.originalChecksums`): rows not yet in Immich are forgotten and hashed
  again from the originals. Rows already uploaded keep their link (uploading again would duplicate).

## Not fixed here

Copies Picfort already uploaded stay in Immich (no location, deviceId = Picfort's). Immich's
**Utilities → Review duplicates** finds them; keep the copy with the location.

## Acceptance criteria

- [x] Emulator: original already in Immich → backup finds it (1 copy, `06440fb1…`, latitude kept).
- [x] Emulator: a new photo with GPS → uploaded with its location (latitude 54.35).
- [x] Permission granted on opening Settings after the update; test files removed afterwards.
- [x] Unit tests, Paparazzi, ktlint, lint green.
