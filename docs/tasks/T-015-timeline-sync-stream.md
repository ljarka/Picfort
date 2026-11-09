# T-015 — Timeline from Immich's sync stream

**Status:** draft  
**Component:** Android app  
**Depends on:** — (decision: [ADR 0005](../decisions/0005-timeline-sync-stream.md))

## Summary

Keep a local Room copy of the user's and timeline partners' Immich assets, synced with
`POST /api/sync/stream` and `POST /api/sync/ack`, and build the timeline from it instead of
requesting months while the user scrolls. Servers older than v2 keep the current bucket
endpoints.

## Why

See ADR 0005. In short: an instant timeline on cold start and offline, no requests while
scrolling, deletions and edits made elsewhere show up on the next sync, and month counts no
longer change after the grid is laid out.

## Before implementing: check the protocol live

The ADR is based on the server source at `v3.3.5`. Before writing code, run the stream once
against the real server with a **session token** (API keys get 403):

- [ ] First stream: record the line types, line count and total bytes for a known library
      (test account: 256 assets).
- [ ] Ack the last line of each type, stream again: only `SyncCompleteV1` should come back.
- [ ] Upload, delete (trash) and archive one asset each, stream again: check which lines arrive.
- [ ] `reset: true`: check that the stream starts with `SyncResetV1`.
- [ ] Note real field formats: dates (offset or not), `duration`, `thumbhash`, `checksum`.

Put the findings in this file and change anything in the design below that turns out wrong.

## Design

### Network
- `sync/SyncService.kt` (Retrofit): `@Streaming @POST("/api/sync/stream")` returning
  `ResponseBody`, `@POST("/api/sync/ack")`, `@DELETE("/api/sync/ack")`.
- `sync/SyncLine.kt`: `{type, data: JsonElement, ack}`. Parse lines one by one from the body's
  source (`readUtf8Line`), never the whole body at once (the first sync of a large library is
  tens of MB).

### Storage (Room, same `ImagesDatabase`, a real migration)
- `remote_assets`: id (PK), ownerId, localDateTime, fileCreatedAt, type, visibility, deletedAt,
  width, height, thumbhash, duration, livePhotoVideoId, stackId, isFavorite, checksum.
  Indices on `(localDateTime)` and `(ownerId)`.
- `remote_stacks`: id, primaryAssetId, ownerId.
- `partners`: sharedById, inTimeline.
- `sync_state`: the logged-in user id and the server + session the rows belong to.
- Checkpoints live on the server (per session), not in Room.

### Sync engine
- `sync/SyncEngine.kt`: opens the stream, applies lines in transactions of 500, then acks the
  latest ack per type of that batch (≤ 1000 acks per call). Handles `SyncResetV1` (wipe, ack,
  restart) and `SyncAckV1`, and acks unknown types without applying them. One run at a time
  (`Mutex`). Reports progress (lines applied) as a `StateFlow`.
- Triggers: app start/resume (`ProcessLifecycleOwner`), pull-to-refresh, after a backup.
  A long first sync runs in WorkManager (expedited, like backup), so it isn't killed when the
  user leaves the app.
- Logout / server change: wipe the synced tables (hook into `SessionManager.logout` and
  `LoginViewModel.changeServer`).

### Timeline
- `TimelineRepository`: on v2+ servers, month buckets come from
  `SELECT strftime(...) month, COUNT(*) FROM remote_assets WHERE <timeline filter> GROUP BY month`,
  merged with MediaStore months as today. A month's tiles come from a query on the same filter,
  ordered by `localDateTime DESC`, plus phone-only media.
- Timeline filter (ADR 0005 rule 6): `visibility = 'timeline' AND deletedAt IS NULL AND
  (ownerId = me OR ownerId IN partners with inTimeline) AND (stackId IS NULL OR id = the
  stack's primaryAssetId)`.
- Months are Room `Flow`s, so a sync updates the grid by itself. Tiles always come from the
  composed state (see the crash fix in PR #25).
- First sync with an empty Room: show progress ("Syncing 1 200 of ~…") instead of an empty
  timeline. The empty state from PR #24 only shows after a completed sync.

### Version gate
- `ServerVersion` (from `/api/server/version`, already used by backup): `major >= 2` → sync
  stream, otherwise the current bucket path. Keep both behind one `TimelineSource` interface
  so the old path can be deleted later in one place.

## Acceptance criteria

- [ ] Cold start with a synced Room shows the full timeline with no network (airplane mode).
- [ ] Scrolling the whole timeline makes no `/timeline/bucket` requests (check OkHttp logs).
- [ ] Deleting, archiving or uploading an asset in the Immich web UI shows up after
      pull-to-refresh.
- [ ] Partners with "show in timeline" appear, and disappear when the setting is turned off.
- [ ] Stacked assets show once (primary only).
- [ ] Killing the app during the first sync and starting it again continues without
      duplicates or gaps.
- [ ] `SyncResetV1` (trigger it with `reset: true`) rebuilds the copy.
- [ ] Logout, then log in as another user: no assets of the first user remain.
- [ ] On a v1 server the old path still works.
- [ ] Timeline benchmark (`:benchmark`) passes. Compare frame times with `main` interleaved
      (emulator noise is large, see PR #25).
- [ ] Unit tests: line parsing, ack batching (latest per type, ≤ 1000), reset handling and the
      timeline filter query (Room in-memory DB).

## Notes for implementing agent

- Use a session token. API keys are rejected by every sync endpoint.
- Never ack before the batch's transaction has committed.
- Upserts must be idempotent: the server re-sends anything that wasn't acked.
- `PartnerAssetBackfillV2` and `PartnerStackBackfillV1` are upserts like their non-backfill types.
- The server sends `SyncResetV1` on its own when the last completed sync is older than 30 days.
- Don't request Exif, albums, faces, memories or OCR yet; each adds a lot of data to the
  first sync.
