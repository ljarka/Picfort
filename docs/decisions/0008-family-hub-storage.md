# ADR 0008 — Shopping lists, receipts, events and school timetables live on the Picfort server

**Status:** proposed  
**Date:** 2026-11-11  
**Extends:** [ADR 0002](0002-family-wall-storage.md) (wall in SQLite), [ADR 0006](0006-family-wall-auth.md) (Immich token), [ADR 0007](0007-server-api-key.md) (optional key)

## Context

The app becomes a family hub (T-017): Wall, Shopping, Events, School, Photos. Shopping lists,
receipts, events and the children's school timetables are shared by the whole family and
edited from several phones, like the
Family Wall. Immich has no place for them.

Unlike wall posts, this data has lasting value: receipts and spending history, future
events. ADR 0002 drops the wall when its schema changes, which is acceptable for chat-like
posts but painful for a year of receipts.

## Decision

### Storage

- Shopping lists, items, receipts, events, school timetables and family profiles are stored on the Picfort
  server, in SQLite,
  next to the wall. Every phone and (later) the frame see the same data.
- **One SQLite file per feature:** `wall.db` (existing), `shopping.db` (lists, items,
  receipts), `events.db`, `school.db` (children, bell schedules, timetables), `family.db` (display
  name, photo and hidden flag per Immich user, T-031).
  Each has its own `SCHEMA_VERSION` (`PRAGMA user_version`, as
  `wall.py`), so a schema change in one feature never touches another.
- Paths come from the environment, as the wall's do: `SHOPPING_DB_PATH` (default
  `shopping.db`), `EVENTS_DB_PATH` (`events.db`), `SCHOOL_DB_PATH` (`school.db`), `FAMILY_DB_PATH` (`family.db`),
  `RECEIPTS_PATH` (`receipts`, the receipt
  photos). The Docker image sets them to `/data/…` in the `Dockerfile` `ENV` block, next to
  `WALL_DB_PATH`; `/data` is the volume that `DATA_LOCATION` maps in `docker-compose.yml`.
- Every connection runs `PRAGMA foreign_keys = ON` (SQLite leaves it off by default; the
  wall has no foreign keys, these stores do).
- `shopping.db` gets its **whole** schema, including `receipts`, in T-018, so adding receipts
  later (T-020) is not a schema change.

### Schema changes

No migrations (project rule), but no silent loss either. When `user_version` differs from
`SCHEMA_VERSION`, the store **renames the old file** to `<name>.v<old>.bak` (replacing an older
backup of that version), logs a warning and starts empty. Receipt photos are left in place.
The release notes call out every version bump. The old data stays readable with `sqlite3`.
(Owner decision 2026-11-11: enough, including for spending history; no migrations.)

### Auth and permissions

- As ADR 0006 + 0007: every call to `/lists`, `/receipts`, `/spending`, `/events` and `/school`
  carries
  the Immich token (who) and, if set, the Picfort key. The server takes `created_by` from the
  token; the client never sends it.
- The token check in `app.py` covers a list of path prefixes, not only `/wall`. A test
  asserts that every route outside the frame endpoints of ADR 0004 answers `401` without a
  token, so a new endpoint cannot be forgotten.
- Every family member reads and edits every list, item, receipt, event and timetable (it's a
  shared fridge note, not a document). Only the creator **deletes** a list, an event or a
  receipt (`403` otherwise), as on the wall. Anyone can archive a list.
- **Children are not Immich users.** Kids often have no account, so `school.db` has its own
  `children` table (name, color), managed from the app. It is the first family data that is
  not keyed by an Immich user id. Anyone can delete a child (a parent who didn't create the
  entry must be able to); the app asks for confirmation.

### Concurrent edits

- Small per-object endpoints (one item, one field), last write wins per field. `PATCH` changes
  only the fields present in the body; a JSON `null` clears an optional field.
- A change to an object another phone has deleted answers `404`; the app drops the object
  quietly.
- Clients poll while a screen is visible: 10 s on an open list, 60 s elsewhere. The open
  list's poll uses an ETag built from a per-list **revision counter** (timestamps have
  one-second resolution and would miss two changes in the same second).

### Conventions

- JSON in and out, `snake_case` fields. Errors as today: `{"status":"error","message":…}`.
- Timestamps: ISO 8601 UTC with seconds (`2026-11-11T17:03:00Z`), as the wall.
- Calendar dates (`YYYY-MM-DD`) are plain local dates without a time zone. The family lives
  in one time zone; "today" is always the phone's date, never the server's (a Docker
  container usually runs in UTC).
- Money: integer minor units (grosze), never floats. One currency per server (`CURRENCY`,
  default `PLN`), returned by the API so the app can format amounts.

## Consequences

- Backing up `DATA_LOCATION` now also backs up lists, receipts, spending, events and timetables (README
  and `example.env` must say so).
- The server must be reachable to use a list, and the shop is outside the home network.
  Without a tunnel (ADR 0007) or a VPN the phone has no live list there, so T-019 keeps the
  last loaded lists on the phone, read-only. Checking items off without a connection is out
  of scope for v1.
- A bug in a schema bump costs a restore from `.bak`, not the data.
- Users removed from Immich stay as ids in `created_by` / `payer_id`; the app shows them as
  "Former member". Nobody can delete their lists and events then (archive still works);
  accepted for a family.
