# T-018 — Shopping lists: Picfort server API

**Status:** done (PR #40; released with the next server version)  
**Component:** ImageFrameServer (Python, Flask, SQLite)  
**Depends on:** — (decision: [ADR 0008](../decisions/0008-family-hub-storage.md); auth as
`/wall`, ADR 0006 and 0007)

## Summary

Shared shopping lists with checkable items, stored in `shopping.db`. Every family member can
read and edit every list. This task also creates the storage foundations the other Phase 7
server tasks reuse.

## Foundations (reused by T-020, T-022)

- `store.py`: a small base for SQLite stores, taken from the pattern in `wall.py`: one
  connection per call, `PRAGMA foreign_keys = ON` on every connection, `user_version` check.
  On a version mismatch it renames the file to `<name>.v<old>.bak`, logs a warning and creates
  an empty database (ADR 0008). `wall.py` stays as it is.
- Token auth for more than `/wall`: the `before_request` check in `register_wall` (`app.py`)
  tests `request.path.startswith("/wall")` today. Move it to `register_auth(app, auth,
  prefixes)` with `AUTH_PREFIXES = ("/wall", "/lists", "/receipts", "/spending", "/events",
  "/school", "/family")`.
  The Picfort key check (`require_api_key`) already covers every route.
- `Dockerfile`: add `SHOPPING_DB_PATH=/data/shopping.db` to the `ENV` block. `app.py` reads
  `SHOPPING_DB_PATH` (default `shopping.db`) like `WALL_DB_PATH`.

## Data (`shopping.db`, `SCHEMA_VERSION = 1`)

The whole file's schema is created here, `receipts` included, so T-020 does not bump the
version (which would empty the lists).

```sql
CREATE TABLE lists (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,  -- ids are never reused
  title       TEXT    NOT NULL,            -- 1..100 chars, trimmed
  archived    INTEGER NOT NULL DEFAULT 0,
  revision    INTEGER NOT NULL DEFAULT 1,  -- +1 on any change to the list or its items
  created_by  TEXT    NOT NULL,            -- Immich user id (from the token)
  created     TEXT    NOT NULL,            -- ISO 8601 UTC
  updated     TEXT    NOT NULL
);
CREATE TABLE items (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  list_id     INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
  text        TEXT    NOT NULL,            -- 1..200 chars, trimmed
  checked     INTEGER NOT NULL DEFAULT 0,
  position    REAL    NOT NULL,            -- ordering of unchecked items
  checked_by  TEXT,                        -- Immich user id
  checked_at  TEXT,
  created_by  TEXT    NOT NULL
);
CREATE INDEX items_list ON items(list_id);
CREATE TABLE receipts (                    -- used from T-020 on
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  list_id     INTEGER REFERENCES lists(id) ON DELETE SET NULL,
  photo       TEXT,                        -- file name in RECEIPTS_PATH
  shop        TEXT,
  date        TEXT    NOT NULL,            -- YYYY-MM-DD, purchase date
  total_minor INTEGER,                     -- grosze; null = not known yet
  payer_id    TEXT    NOT NULL,
  status      TEXT    NOT NULL,            -- pending | read | manual | confirmed (ADR 0009)
  read_error  TEXT,
  created_by  TEXT    NOT NULL,
  created     TEXT    NOT NULL,
  updated     TEXT    NOT NULL
);
CREATE INDEX receipts_date ON receipts(date);
```

## API

Bodies and responses are JSON. Errors `{"status":"error","message":…}` as today. A list:

```json
{"id": 3, "title": "Biedronka", "archived": false, "revision": 17,
 "created_by": "<immich user id>", "created": "2026-11-11T17:03:00Z", "updated": "…",
 "item_count": 7, "checked_count": 2,
 "items": [{"id": 41, "text": "milk", "checked": false, "position": 1.0,
            "checked_by": null, "checked_at": null, "created_by": "<immich user id>"}]}
```

`items` is always complete: unchecked by `position`, then checked by `checked_at`, newest
first. A family has a handful of short lists, so `GET /lists` returns them whole (the app
shows card previews and keeps an offline copy from one request).

| Endpoint | Result |
|---|---|
| `GET /lists` | Active lists, newest `created` first. `?archived=1` returns the archived ones instead. |
| `POST /lists` `{title, items?: [text, …]}` | `201` list. |
| `GET /lists/<id>` | List. Sends `ETag: "<id>-<revision>"`; with a matching `If-None-Match` answers `304` without a body. |
| `PATCH /lists/<id>` `{title?, archived?}` | Updated list. Anyone. |
| `DELETE /lists/<id>` | `204`. Creator only (`403`). Deletes its items; its receipts stay with `list_id` null. |
| `POST /lists/<id>/items` `{text, position?}` | `201` item. Without `position`: after the last item. |
| `PATCH /lists/<id>/items/<item>` `{text?, checked?, position?}` | Updated item. `checked: true` sets `checked_by` (the caller) and `checked_at`; `false` clears them. |
| `DELETE /lists/<id>/items/<item>` | `204`. Anyone. |
| `POST /lists/<id>/uncheck-all` | Updated list. |
| `POST /lists/<id>/delete-checked` | Updated list. |

- Every change to a list or its items bumps `revision` and `updated` in the same transaction.
- Validation → `400` with a message: body not a JSON object, empty or too long `title`/`text`,
  `checked`/`archived` not a boolean, `position` not a number, more than 300 items in a list.
  Unknown fields are ignored.
- `404`: unknown list, or an item that is not in that list.
- No token → `401`, Immich unreachable → `502`, exactly as `/wall`.

## Tests

`tests/test_shopping.py` (unittest, like `test_wall.py`, using `tests/helpers.py`):
- CRUD for lists and items; item order (unchecked by position, checked by `checked_at`);
  `checked_by`/`checked_at` set and cleared; uncheck-all; delete-checked.
- Creator-only delete (`403` for another user); anyone can patch and archive.
- `revision` grows with every change; `If-None-Match` → `304`, then `200` after a change made
  in the same second.
- Deleting a list deletes its items (foreign keys are on) and keeps a receipt row with
  `list_id` null.
- A different `user_version` renames the file to `.bak` and starts empty; `wall.db` is
  untouched.
- Every route under `AUTH_PREFIXES` answers `401` without a token; the frame endpoints
  (`/status`, `/run`, …) still don't need one.

## Acceptance criteria

- [ ] All endpoints above, with token auth and the optional Picfort key.
- [ ] `shopping.db` at `SHOPPING_DB_PATH`; `/data/shopping.db` in the Docker image.
- [ ] Existing wall tests still pass after the auth move.
- [ ] Tests pass in CI on Python 3.11 and 3.13.
- [ ] README: API table, the storage paragraph (schema change keeps a `.bak`), the "Back up
      that folder" line; `docker/example.env`: the `DATA_LOCATION` comment lists shopping
      lists.

## Out of scope

- Per-list sharing (all lists are family lists).
- List colors, item quantities/units as fields (written in the text: "Milk 2x").
- Receipt endpoints (T-020); only the table is created here.
