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
| POST | /wall | Create post (multipart: author_id, author, text, photo optional) |
| DELETE | /wall/`<id>` | Delete post |
| PUT | /wall/`<id>`/pin | Toggle pin |
| GET | /wall/photo/`<filename>` | Serve attached photo |

## Photo storage

Uploaded photos are stored in `wall_photos/` relative to the ImageFrameServer working
directory. Files are named `<uuid>.<ext>`. The database stores the filename only.

## Consequences

- Posts are stored only on the ImageFrameServer host; they are not backed up to Immich.
- If ImageFrameServer is unavailable, the Wall tab in the app shows a cached copy of the last fetch.
- Family members are the Immich users of the server (2026-11-10). A post carries the
  author's Immich user id, which the app takes from its login; the server trusts it.
- No authentication: any device on the local network can post and delete.
  See ADR 0004 for the auth decision.
