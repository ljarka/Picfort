# T-015 — Timeline from Immich's sync stream

**Status:** in-progress (code merged in PR #27; check the criteria below on a device)  
**Component:** Android app  
**Depends on:** — (decision: [ADR 0005](../decisions/0005-timeline-sync-stream.md))

## Summary

Keep a local Room copy of the user's and timeline partners' Immich assets, synced with
`POST /api/sync/stream` and `POST /api/sync/ack`, and build the timeline from it instead of
requesting months while the user scrolls. Servers older than v2 are not supported (owner
decision 2026-11-09), so there is no fallback to the bucket endpoints; they are still used for
album contents.

## Why

See ADR 0005. In short: an instant timeline on cold start and offline, no requests while
scrolling, deletions and edits made elsewhere show up on the next sync, and month counts no
longer change after the grid is laid out.

## Protocol checked live (2026-11-09, Immich v3.3.5, test account)

Run with a session token against `photos.jarka.club`, with types `AuthUsersV2, PartnersV1,
AssetsV2, PartnerAssetsV2, StacksV1, PartnerStacksV1`:

| Step | Result |
|---|---|
| First stream, no acks | 258 lines, 167 KB, 0.19 s: 1 `AuthUserV2`, 256 `AssetV2` (avg 652 B/line), 1 `SyncCompleteV1` |
| Ack the last line of each type, stream again | only `SyncCompleteV1` (96 B) |
| Upload one asset | new `AssetV2` |
| Move one asset to the trash | `AssetV2` with `deletedAt` set (not a delete line) |
| Archive one asset | `AssetV2` with `visibility: "archive"` |
| Delete permanently (`force: true`) | `AssetDeleteV1 {assetId}` |
| Nothing changed by the user | an already-synced asset can arrive again (server jobs update it) |
| `reset: true` | **no `SyncResetV1`**: the full stream again, incl. `AssetDeleteV1` for an asset the client never had |
| API key instead of session token | 403 on every sync endpoint |
| `GET /api/sync/ack` | the stored acks per type, `{type, ack}` |

Field formats seen:
- Dates: ISO 8601 UTC with milliseconds (`2024-01-26T21:10:32.000Z`). `localDateTime` is the
  photo's wall-clock time written as if it were UTC. Group months on it as-is, without
  converting zones.
- `checksum` and `thumbhash` are base64. `width` and `height` are present. `type` is `IMAGE`/`VIDEO`.
- Acks: `<type>|<UUIDv7>`. Each line has its own ack, increasing within a type.
- `AuthUserV2` comes again when quota usage changes.

Not seen yet (the test account has no videos, partners or stacks): the unit of `AssetV2.duration`
(an int in V2; probably ms, check against a video), and the partner, stack and backfill lines.
Check these with a second account before relying on them.

## Design

### Network
- `sync/SyncService.kt` (Retrofit): `@Streaming @POST("/api/sync/stream")` returning
  `ResponseBody`, `@POST("/api/sync/ack")`, `@DELETE("/api/sync/ack")`.
- `sync/SyncLine.kt`: `{type, data: JsonElement, ack}`. Parse lines one by one from the body's
  source (`readUtf8Line`), never the whole body at once (the first sync of a large library is
  tens of MB).

### Storage (Room, same `ImagesDatabase`; no migrations, a schema change wipes it)
- `remote_assets`: id (PK), ownerId, localDateTime, fileCreatedAt, type, visibility, deletedAt,
  width, height, thumbhash, duration, livePhotoVideoId, stackId, isFavorite, checksum.
  Indices on `(localDateTime)` and `(ownerId)`.
- `remote_stacks`: id, primaryAssetId, ownerId.
- `partners`: sharedById, inTimeline.
- `sync_state`: the logged-in user id and the server + session the rows belong to. It is in the
  same database (not SharedPreferences), so a wipe also forgets it and the next sync is a full one.
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

- [x] Cold start with a synced Room shows the full timeline with no network (airplane mode).
- [x] Scrolling the whole timeline makes no `/timeline/bucket` requests (check OkHttp logs).
- [x] Deleting, archiving or uploading an asset in the Immich web UI shows up after
      pull-to-refresh.
- [ ] Partners with "show in timeline" appear, and disappear when the setting is turned off.
      _Not checked: needs another Immich user sharing with the test account._
- [x] Stacked assets show once (primary only).
- [x] Killing the app during the first sync and starting it again continues without
      duplicates or gaps.
      _Emulator 2026-11-11: killed 0.4 s into the stream; the test account (261 assets) fits one batch, so a half-applied copy is covered only by `SyncEngineTest`._
- [ ] A `SyncResetV1` from the server rebuilds the copy, and a client-requested `reset: true`
      wipes the copy first (no `SyncResetV1` comes for it).
      _Emulator 2026-11-11: the client-requested half passes (empty copy → `"reset":true`, no `SyncResetV1`). A server-sent `SyncResetV1` was not triggered (needs a flag in the Immich DB)._
- [x] Logout, then log in as another user: no assets of the first user remain.
- ~~On a v1 server the old path still works.~~ Dropped: Immich older than v2 is not supported.
- [ ] Timeline benchmark (`:benchmark`) passes. Compare frame times with `main` interleaved
      (emulator noise is large, see PR #25).
      _2026-11-11: the benchmark now opens the Photos tab (the app starts on the Wall) and passes on the emulator (frame CPU P90 10.5 ms, overrun P90 1.6 ms). The interleaved comparison with the pre-T-015 build is still to do._
- [ ] Unit tests: line parsing, ack batching (latest per type, ≤ 1000), reset handling and the
      timeline filter query (Room in-memory DB).
      _Parsing, ack batching and reset are covered by `SyncEngineTest`; the timeline filter query has no Room in-memory test._

## Notes for implementing agent

- Use a session token. API keys are rejected by every sync endpoint.
- Never ack before the batch's transaction has committed.
- Upserts must be idempotent: the server re-sends anything that wasn't acked.
- `PartnerAssetBackfillV2` and `PartnerStackBackfillV1` are upserts like their non-backfill types.
- The server sends `SyncResetV1` on its own when the last completed sync is older than 30 days.
- A delete line for an unknown asset id is normal (see the live check); ignore it.
- Don't request Exif, albums, faces, memories or OCR yet; each adds a lot of data to the
  first sync.
