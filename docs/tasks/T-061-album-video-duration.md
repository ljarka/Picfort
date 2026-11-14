# T-061 — Albums with a video wouldn't open

**Status:** in-progress  
**Component:** Android app (`:feature:timeline`)  
**Depends on:** T-015

## Summary

Some albums showed "Something went wrong" instead of their photos. Album contents come from
`timeline/bucket?albumId=…`, whose `duration` is milliseconds (a number) since Immich v3, but
`BucketResponse.duration` was `List<String?>`: decoding failed for every album with a video. The
main timeline was fine (it reads the sync stream); search already took both formats.

## Design

- `BucketResponse.duration: List<JsonElement?>`, read with `parseSearchDuration` (text or number).

## Acceptance criteria

- [x] `AlbumBucketTest`: a v3 bucket with a video (number) and an older one (text) parse.
- [x] Device check (2026-11-14, emulator, test account, Immich 3.3): album with a video and a photo.
  Old build: "Something went wrong"; new build: both shown, the video with "0:02".
  Test album and assets removed afterwards.
