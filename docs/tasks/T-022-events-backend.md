# T-022 — Events: Picfort server API

**Status:** done  
**Component:** ImageFrameServer  
**Depends on:** T-018 (`store.py`, auth prefixes), [ADR 0008](../decisions/0008-family-hub-storage.md)

## Summary

Family events (trips, holidays, birthdays) with dates and an optional Immich album, stored in
`events.db`.

## Data (`events.db`, `SCHEMA_VERSION = 1`)

```sql
CREATE TABLE events (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  title       TEXT NOT NULL,          -- 1..100 chars, trimmed
  start_date  TEXT NOT NULL,          -- YYYY-MM-DD (local date, no time zone)
  end_date    TEXT,                   -- YYYY-MM-DD > start_date; null = one day
  start_time  TEXT,                   -- HH:MM (24 h), optional (a party at 18:00)
  location    TEXT,                   -- free text, up to 200 chars
  note        TEXT,                   -- up to 2000 chars
  album_id    TEXT,                   -- Immich album id (UUID), optional
  created_by  TEXT NOT NULL,          -- Immich user id (from the token)
  created     TEXT NOT NULL,          -- ISO 8601 UTC
  updated     TEXT NOT NULL
);
CREATE INDEX events_start ON events(start_date);
```

Dates are plain local dates (ADR 0008): the server never decides what "today" is; the phone
passes its date.

`EVENTS_DB_PATH` (default `events.db`); add `EVENTS_DB_PATH=/data/events.db` to the
`Dockerfile` `ENV` block.

## API

An event in JSON has exactly the columns above (`snake_case`; unset fields are `null`).

| Endpoint | Result |
|---|---|
| `GET /events?after=2026-11-11` | Events with `coalesce(end_date, start_date) >= after` (upcoming and ongoing), by `start_date`, then `start_time` (untimed first), then `id`. Not paged. |
| `GET /events?before=2026-11-11&limit=50&offset=0` | Events with `coalesce(end_date, start_date) < before`, latest end first. `limit` 1–100, default 50. |
| `POST /events` `{title, start_date, end_date?, start_time?, location?, note?, album_id?}` | `201` event. |
| `GET /events/<id>` | One event. |
| `PATCH /events/<id>` | Any of the fields; `null` clears an optional one. Anyone. Returns the event. |
| `DELETE /events/<id>` | `204`. Creator only (`403`). Does not touch the Immich album. |

- `GET /events` needs exactly one of `after` / `before` (`400` otherwise).
- Validation → `400` with a message: dates not real `YYYY-MM-DD` dates; `end_date` before
  `start_date` (an `end_date` equal to `start_date` is stored as null); `start_time` not
  `HH:MM`; too long or empty `title`; `album_id` not a UUID; body not a JSON object. A
  `PATCH` is validated against the resulting event (changing only `start_date` past the
  stored `end_date` is `400`).
- The server does not check `album_id` against Immich: albums are per user there, and the app
  checks with the user's own token (T-026).
- `404` unknown event; `401`/`502` as `/wall`.

## Tests

`tests/test_events.py`: validation (each rule above, including PATCH against stored values);
upcoming/past split around an ongoing trip (started yesterday, ends tomorrow → upcoming;
ended yesterday → past; one-day event today → upcoming); ordering with and without times;
paging; clearing fields with `null`; creator-only delete; `401` without a token; schema
version change keeps a `.bak` and leaves `wall.db` and `shopping.db` alone.

## Acceptance criteria

- [x] Endpoints above with token auth and the optional Picfort key.
- [x] `events.db` at `EVENTS_DB_PATH`; `/data/events.db` in the Docker image.
- [x] Tests pass in CI; README (API table, backup line) and the `DATA_LOCATION` comment in
      `docker/example.env` updated.

## Out of scope

- Recurring events (birthdays every year): v2 candidate.
- Reminders (owner decision 2026-11-11: not in v1), iCal feed.
- Events on the frame or the wall.
