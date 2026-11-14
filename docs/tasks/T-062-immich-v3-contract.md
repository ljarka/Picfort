# T-062 — Immich v3 responses: contract tests, readable errors

**Status:** in-progress  
**Component:** Android app (`:feature:timeline`, `:app`, `:core:database`)  
**Depends on:** T-061, T-015

## Summary

T-061 (albums with a video) was a response field whose type changed in Immich v3. Every Immich
call the app makes was checked against real Immich 3.3 responses, and the unknown-error message
now names the error.

## Design

- Fixtures `src/test/resources/immich-v3/` (test account; email, names, paths, token replaced):
  albums, album create/add, buckets, album bucket with a video, people, smart and metadata
  search, asset details (photo, video), sync stream lines (one per type); in `:app` login,
  version, bulk-upload-check, upload.
- `ImmichV3ContractTest` (`:feature:timeline`) and `ImmichV3AppContractTest` (`:app`) read them
  with the app's own DTOs and `Json { ignoreUnknownKeys = true }`. Refresh on a new Immich major.
- `describeError`: an unexpected error is logged (`Log.w`) and named: "Something went wrong
  (SerializationException)".
- `AssetV2.duration` confirmed as milliseconds (comments updated, T-015 criterion).
- `RemoteAssetsDaoTest` (`:app` androidTest) compiles again (imports from `:core:database`).

## Findings

- No other type mismatch: every DTO reads the 3.3 responses (only `duration` in buckets, T-061).
- Not covered by CI: `:app` androidTest (it had silently stopped compiling).

## Acceptance criteria

- [x] Contract tests green (6 tests); unit tests, Paparazzi, ktlint, boundaries green.
- [x] Emulator: the test video shows "0:02" on the timeline and opens in the viewer.
- [x] `RemoteAssetsDaoTest` 4/4 on the emulator.
