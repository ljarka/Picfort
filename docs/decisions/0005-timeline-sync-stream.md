# ADR 0005 — Timeline from a local copy synced with Immich's sync stream

**Status:** accepted (implemented in T-015, PR #27)  
**Date:** 2026-11-09

## Context

The timeline asks Immich for data while the user scrolls. Each month is a separate request:
`GET /api/timeline/buckets` gives month counts, and `GET /api/timeline/bucket?timeBucket=…`
gives a month's assets. Consequences:

- A cold start shows nothing from the server until the network answers.
- Every month that comes into view costs a request. A cached month is re-fetched in the
  background each time it is shown.
- Offline, only months that were opened before are visible.
- Merging phone photos with the server's months needs estimates that are corrected later.
  These async corrections caused the grid crash fixed in PR #25.

Immich (stable since v2, the server here runs v3.3.5) has a sync protocol made for this. The
official mobile app keeps a full local copy of the library with it:

- `POST /api/sync/stream` with `{"types": [...], "reset"?: bool}` returns JSON lines
  (`application/jsonlines+json`): `{"type": "<entity>", "data": {...}, "ack": "<type>|<updateId>[|<extraId>]"}`.
- `POST /api/sync/ack` with `{"acks": [...]}` (max 1000) stores the client's checkpoint
  **per login session**. The next stream only sends what changed after it.
- `SyncResetV1` tells the client to drop its copy and sync from scratch. The server sends it
  when it has flagged the session for a reset, and when the last `SyncCompleteV1` ack is older
  than 30 days. It is the only line of that stream. The client acks it to clear the flag.
  A client-requested `reset: true` is different: it sends **no** `SyncResetV1`, it just clears
  the checkpoints and streams everything again (checked live, see T-015).
- `SyncCompleteV1` ends every stream.
- Sync endpoints refuse API keys (`403 "Sync endpoints cannot be used with API keys"`). They
  need a session token, which the app gets from `/api/auth/login`.

Source: `server/src/services/sync.service.ts`, `dtos/sync.dto.ts`, `utils/sync.ts` and
`controllers/sync.controller.ts` at tag `v3.3.5`.

## Decision

On servers v2 and newer, the timeline reads only from Room. Room holds a copy of the user's
(and timeline partners') assets, kept up to date with the sync stream. The bucket endpoints
stay only as a fallback for servers older than v2.

Requested types (v3.3.5 names):

| Type | Why |
|---|---|
| `AuthUsersV2` | own user id, so own assets can be told apart from partners' |
| `PartnersV1` | which partners are shown in the timeline (`inTimeline`) |
| `AssetsV2` | own assets and deletions (`AssetV2`, `AssetDeleteV1`) |
| `PartnerAssetsV2` | partners' assets (backfilled when a partner is added) |
| `StacksV1`, `PartnerStacksV1` | stacked assets show once, like `withStacked=true` today |

Exif, albums, faces, memories and the other types are left for later features.

Rules:
1. **Every line type is handled.** Besides the entities (`AssetV2`, `AssetDeleteV1`,
   `PartnerAssetV2`, `PartnerAssetDeleteV1`, `StackV1`, …), the stream sends backfill variants
   (`PartnerAssetBackfillV2`, `PartnerStackBackfillV1`), handled as upserts, and `SyncAckV1`
   lines (a backfill finished), which have no data but whose ack must be sent like any other.
   Unknown types are skipped but still acked, so a newer server can't stall the client.
2. **Write first, ack second.** Lines are written to Room in transactions of a few hundred.
   The latest ack per type is sent only after that transaction commits. If the app dies
   mid-sync, the server re-sends what was not acked, and upserts make that harmless.
3. **Reset.** On `SyncResetV1`: delete all synced rows, ack it, start a new stream. Before
   asking for `reset: true` yourself, delete the synced rows first, because no `SyncResetV1`
   will come. Either way, a full stream includes `AssetDeleteV1` for assets the client never
   had, so applying a delete for an unknown id must do nothing.
4. **Logout or server change:** delete the synced rows. The checkpoints belong to the old
   session anyway.
5. **When to sync:** on app start, on resume, on pull-to-refresh and after a backup. One
   stream at a time.
6. **What the timeline shows:** the timeline rows are rows with `visibility = timeline` and
   `deletedAt IS NULL`, owned by the user or by a partner with `inTimeline`, and not a
   non-primary member of a stack. Month buckets come from a SQL `GROUP BY` on
   `localDateTime`, merged with MediaStore months the same way as today.

## Consequences

- Cold start and offline show the whole timeline straight from Room. Scrolling makes no
  network calls except for thumbnails.
- First sync of a large library streams every asset once (about 1 KB of JSON per asset).
  It has to run in the background with progress in the UI, and must survive interruption
  (rule 2).
- Server jobs (thumbnails, metadata, ML) update assets after upload, and each update re-sends
  the asset. Writes are upserts by id.
- Room grows with the library (about 100k rows for a large one). That is fine for SQLite,
  but the month and timeline queries need indices on `localDateTime` and `ownerId`.
- Partners' photos follow `PartnersV1.inTimeline` instead of the `withPartners=true` query flag.
- Servers older than v2 keep the old code path until support for them is dropped. That is two
  ways to fill the timeline for a while.
- Sync needs a session token. If login ever moves to API keys, the timeline cannot use this.
