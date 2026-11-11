# ADR 0002 — Family Wall storage in ImageFrameServer

**Status:** accepted  
**Date:** 2026-11-08

## Context

Family Wall needs a persistent store for posts (text + optional photo).
Options considered:

1. **Extend ImageFrameServer** — add SQLite + REST endpoints to the existing Python service
2. **Store in Immich** — use a dedicated Immich album; posts are photos with text captions
3. **New Kotlin/Ktor microservice** — separate service, consistent with compose-cast

## Decision

Extend ImageFrameServer with SQLite and new REST endpoints.

Rationale:
- ImageFrameServer is already the service that runs on the display host (Raspberry Pi or home server)
- It is already required to be running for the frame to work; no new host to manage
- Python + SQLite is trivial to extend; no new dependencies
- Immich captions are not designed for structured posts; searching/sorting would be fragile
- A new Kotlin microservice adds operational overhead for a simple feature

## Schema

```sql
CREATE TABLE posts (
  id      INTEGER PRIMARY KEY AUTOINCREMENT,
  author_id TEXT  NOT NULL,            -- Immich user id
  author  TEXT    NOT NULL,            -- display name at the time of posting
  text    TEXT,
  photo   TEXT,                        -- relative path in wall_photos/
  photo_width  INTEGER,
  photo_height INTEGER,
  pinned  INTEGER NOT NULL DEFAULT 0,
  created TEXT    NOT NULL             -- ISO 8601 UTC
);
```

## API surface

| Method | Path | Description |
|--------|------|-------------|
| GET | /wall | List posts, newest first; `?limit=20` |
| POST | /wall | Create post by the caller (multipart: text and/or photo) |
| DELETE | /wall/`<id>` | Delete post |
| PUT | /wall/`<id>`/pin | Toggle pin |
| GET | /wall/photo/`<filename>` | Serve attached photo |

## Photo storage

Uploaded photos are stored in `wall_photos/` relative to the ImageFrameServer working
directory. Files are named `<uuid>.<ext>`. The database stores the filename only.

## Consequences

- Posts are stored only on the ImageFrameServer host; they are not backed up to Immich.
- If ImageFrameServer is unavailable, the wall keeps showing the posts already loaded in this
  session (in memory only; T-002 v1). A copy on disk is a planned improvement, needed once the
  wall is the home screen (T-017).
- Family members are the Immich users of the server (2026-11-10). A post carries the
  author's Immich user id and name, taken from the caller's Immich token (ADR 0006).
