# T-064 — Backup: large videos through the home network

**Status:** done (bd6f484)  
**Component:** Android app (`:app`, `:core:database`)  
**Depends on:** T-063

## Summary

Immich behind Cloudflare takes uploads up to 100 MB; bigger ones get HTTP 413 (checked on the
test server: the 413 comes after ~1 MB). Backup saved such a file as refused for good, so it never
went up. At home the phone can reach Immich directly, without that limit: backup now uses the
home-network address when it answers.

Found on the way: in debug builds the HTTP logger read every request body into memory, so a
200 MB video crashed the app (OutOfMemoryError), and WorkManager retried into the same crash.

## Design

- Settings → "Home network address" (`BackupSettingsStore.homeUrl`, dialog with "Will connect to
  …", empty = none). `AuthInterceptor` sends the Immich token to it too (same server).
- `BackupRoute.reachableHome()`: `GET /api/server/ping` with a 3 s timeout; if it answers, the
  whole run (check + upload) goes through it ("Home network · Uploaded N").
- 413 through the public address → `rejected = "HTTP 413"` (`NEEDS_HOME_NETWORK`): retried only
  through the home address; older rows refused with 413 count as waiting too. 413 at home →
  `"HTTP 413 (home network)"`, refused for good.
- Away with a home address set, files over 99 MB (`PUBLIC_UPLOAD_LIMIT`) wait without a try.
  Without one, a big file is tried once (the server may not have the limit).
- Settings: "N over 100 MB wait for the home network"; the summary says to set the address when
  none is set.
- `DebugLoggingInterceptor`: bodies logged only for API calls ≤ 64 KB; headers only for uploads,
  files, thumbnails, video and the sync stream.

## Acceptance criteria

- [x] Unit tests: `BackupRouteTest` (hold rules, summary), `DebugLoggingTest`.
- [x] Paparazzi `settings`: home address, waiting count.
- [x] Emulator, test account, real Cloudflare: a 213 MB photo crashed the old debug build; then
  413 → waits ("set the home network address"); with home address `10.0.2.2:2283` (the same
  Immich on this Mac) → "Home network · Uploaded 1", 213 520 183 bytes on the server; unreachable
  home address → public address, "Up to date". Test files removed.
