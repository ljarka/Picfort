# T-001 — Family Wall: ImageFrameServer backend

**Status:** done  
**Component:** ImageFrameServer (Python)  
**Depends on:** —

## Summary

Add a Family Wall REST API to ImageFrameServer. The wall stores posts (text + optional photo)
in a local SQLite database. The Android app reads and writes posts; the display device polls
and shows recent posts.

## Why

The Family Wall is Picfort's defining differentiator. No other Immich client or frame app
has a shared family message board. The backend lives in ImageFrameServer because that service
already runs on the display host and must be available for the frame to work.

See ADR 0002 for the storage decision.

## Design

### New files
- `wall.py` — SQLite init, CRUD functions, photo file management
- `wall_photos/` — directory created at startup; stores uploaded photo files

### Changes to `app.py`
Register new Blueprint or routes from `wall.py`.

### Database schema

```sql
CREATE TABLE IF NOT EXISTS posts (
  id      INTEGER PRIMARY KEY AUTOINCREMENT,
  author_id TEXT  NOT NULL,            -- Immich user id of the author
  author  TEXT    NOT NULL,            -- author's name at the time of posting
  text    TEXT,
  photo   TEXT,                        -- filename in wall_photos/, NULL if none
  photo_width  INTEGER,                -- pixels, NULL if no photo
  photo_height INTEGER,
  pinned  INTEGER NOT NULL DEFAULT 0,
  created TEXT    NOT NULL             -- ISO 8601 UTC, e.g. 2026-11-08T12:00:00Z
);
```

### API endpoints

**GET /wall**
Returns recent posts, newest first.
Query params: `limit` (default 20, max 100).

Response:
```json
[
  {
    "id": 1,
    "author_id": "7f3c…",
    "author": "Mama",
    "text": "Pierwsze pomidory!",
    "photo_url": "/wall/photo/abc123.jpg",
    "photo_width": 1920,
    "photo_height": 1440,
    "pinned": false,
    "created": "2026-11-08T12:00:00Z"
  }
]
```

**POST /wall**
Create a new post. `multipart/form-data`:
- No author fields: the author is the user of the request's Immich token (see Authentication)
- `text` (optional, string)
- `photo` (optional, file; JPEG or PNG; max 10 MB)

Returns 201 with the created post JSON.
Returns 400 if neither text nor photo is provided.

**DELETE /wall/`<id>`**
Delete a post and its photo file (if any). Returns 204.
Returns 404 if not found.

**PUT /wall/`<id>`/pin**
Toggle pinned status. Returns the updated post JSON.

**GET /wall/photo/`<filename>`**
Serve the attached photo file. Returns 404 if not found.

### Photo handling
- Accepted types: JPEG, PNG, HEIC (via `pillow-heif` if installed; otherwise reject with 415)
- Stored as-is; no resizing on upload (app resizes before upload if needed)
- Read width and height with Pillow on upload (after EXIF rotation) and store them, so
  clients can lay out the photo before it loads
- Filename: `<uuid4>.<original_ext>`

### Authentication (ADR 0006)
Every `/wall` request needs `Authorization: Bearer <Immich access token>`. The server asks
`GET <IMMICH_URL>/api/users/me` who it is and caches the answer for 5 minutes (keyed by a hash
of the token). No token or rejected → 401; Immich unreachable → 502. Only the author may
delete or pin a post (403). The frame endpoints stay open (ADR 0004).

### Error handling
All error responses use the server's existing shape, `{"status": "error", "message": "<message>"}`,
with the appropriate HTTP status.

## Acceptance criteria

- [x] `GET /wall` returns empty list when no posts exist
- [x] `POST /wall` with text only creates a post; `photo_url` is null
- [x] `/wall` without a valid Immich token returns 401; the author comes from the token
- [x] Deleting or pinning someone else's post returns 403
- [x] A photo post returns `photo_width`/`photo_height` of the upright image
- [x] `POST /wall` with photo saves the file and returns a valid `photo_url`
- [x] `GET /wall/photo/<filename>` serves the uploaded file
- [x] `DELETE /wall/<id>` removes the post and the photo file from disk
- [x] `PUT /wall/<id>/pin` toggles pinned; second call toggles back
- [x] Posts are ordered newest-first in `GET /wall`
- [x] Pinned posts appear first in `GET /wall`, then newest-first among the rest
- [x] Existing tests pass: `python3 -m unittest discover -s tests -t tests`
- [x] `GET /status` still works (no regression)

## Notes for implementing agent

- Do not use Flask-SQLAlchemy or any ORM; use `sqlite3` from the standard library directly.
- No migrations: if the schema changes, the wall database is recreated (single family, see
  ADR 0002). Nothing is deployed yet, so this matters only after the first release.
- Database file path: `wall.db` in the working directory, or configurable via `WALL_DB_PATH` env var.
- `wall_photos/` is created with `os.makedirs(..., exist_ok=True)` at startup.
- Thread safety: Flask dev server is single-threaded; use `check_same_thread=False` on the connection
  or open a new connection per request.
- Add a brief test in `tests/` covering the happy path of POST and GET.
