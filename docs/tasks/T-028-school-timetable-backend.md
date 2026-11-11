# T-028 — School timetable: Picfort server API

**Status:** ready  
**Component:** ImageFrameServer  
**Depends on:** T-018 (`store.py`, auth prefixes incl. `/school`),
[ADR 0008](../decisions/0008-family-hub-storage.md)

## Summary

Each child has a weekly timetable: lessons and extracurricular activities per day of the
week, plus a bell schedule (period times) so a lesson can be entered as "period 3: Maths".
Children are rows on the Picfort server, not Immich users. Stored in `school.db`. A child can
have a photo (a face from Immich, T-030) and, **per parent**, a link to a person in that
parent's Immich library: face recognition is per user in Immich, so the same child has a
different person id in each parent's library.

## Data (`school.db`, `SCHEMA_VERSION = 1`)

```sql
CREATE TABLE children (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  name            TEXT NOT NULL,        -- 1..50 chars, trimmed
  color           TEXT NOT NULL,        -- an Immich avatar color name: primary, pink, red,
                                        -- yellow, blue, green, purple, orange, gray, amber
  immich_user_id  TEXT,                 -- reserved (optional link to an Immich user); no UI in v1
  photo           TEXT,                 -- file name in CHILD_PHOTOS_PATH, optional
  created_by      TEXT NOT NULL,
  created         TEXT NOT NULL,        -- ISO 8601 UTC
  updated         TEXT NOT NULL
);
CREATE TABLE child_people (             -- each parent's link to an Immich person (T-030)
  child_id        INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  immich_user_id  TEXT NOT NULL,        -- the parent (from the token)
  person_id       TEXT NOT NULL,        -- Immich person id (UUID) in that parent's library
  PRIMARY KEY (child_id, immich_user_id)
);
CREATE TABLE periods (                  -- the child's bell schedule
  child_id    INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  number      INTEGER NOT NULL,         -- 0..15 (0 = "zero lesson")
  start_time  TEXT NOT NULL,            -- HH:MM
  end_time    TEXT NOT NULL,            -- HH:MM > start_time
  PRIMARY KEY (child_id, number)
);
CREATE TABLE entries (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  child_id    INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  kind        TEXT NOT NULL,            -- lesson | activity
  weekday     INTEGER NOT NULL,         -- 1 = Monday … 7 = Sunday (ISO)
  start_time  TEXT NOT NULL,            -- HH:MM
  end_time    TEXT NOT NULL,            -- HH:MM > start_time
  title       TEXT NOT NULL,            -- subject or activity name, 1..100 chars
  period      INTEGER,                  -- lessons only: the bell-schedule number, or null
  place       TEXT,                     -- room, or where the activity is; up to 100 chars
  teacher     TEXT,                     -- up to 100 chars
  note        TEXT,                     -- e.g. "bring swimsuit"; up to 500 chars
  created_by  TEXT NOT NULL,
  updated     TEXT NOT NULL
);
CREATE INDEX entries_child_day ON entries(child_id, weekday);
```

- A weekly template: no dates, no terms, no A/B weeks. Times are local wall-clock times.
- A lesson with a `period` stores that period's times too, so reading never joins. The
  times are copied when the lesson is saved and rewritten when the bell schedule changes.
- The color names are the ones the app already maps for Immich users (T-002), so a child
  chip looks like a family member's avatar ring.

`SCHOOL_DB_PATH` (default `school.db`) and `CHILD_PHOTOS_PATH` (default `child_photos`); add
`SCHOOL_DB_PATH=/data/school.db` and `CHILD_PHOTOS_PATH=/data/child_photos` to the
`Dockerfile` `ENV` block. Both tables are in schema version 1, so T-030 needs no schema change.

## API

All under `/school`, JSON, `snake_case`. A child, as returned everywhere:

```json
{"id": 1, "name": "Ola", "color": "pink", "immich_user_id": null,
 "photo_url": "/school/children/photo/<file>", "my_person_id": "<uuid>",
 "created_by": "<immich user id>", "updated": "2026-11-11T17:03:00Z",
 "periods": [{"number": 1, "start_time": "08:00", "end_time": "08:45"}],
 "entries": [{"id": 7, "kind": "lesson", "weekday": 1, "start_time": "08:00",
              "end_time": "08:45", "title": "Maths", "period": 1, "place": "12",
              "teacher": "Mrs Nowak", "note": null}]}
```

`periods` by `number`; `entries` by `weekday`, `start_time`, `id`. `photo_url` is null without
a photo. `my_person_id` is the **caller's** link (from `child_people` for the token's user), or
null; other parents' links are never returned.

| Endpoint | Result |
|---|---|
| `GET /school` | `{"children": [child, …]}` by name: everything in one response (a few children, ~40 entries each). |
| `POST /school/children` `{name, color}` | `201` child. |
| `PATCH /school/children/<id>` `{name?, color?, immich_user_id?}` | Updated child. |
| `DELETE /school/children/<id>` | `204`; deletes its periods, entries, links and photo file. Anyone (ADR 0008). |
| `PUT /school/children/<id>/photo` multipart `photo` | The child with `photo_url`. JPEG or PNG up to 2 MB, checked like wall photos; replaces (and deletes) the old file. Anyone. |
| `DELETE /school/children/<id>/photo` | `204`. Anyone. |
| `GET /school/children/photo/<file>` | The photo, `Cache-Control: private`, long max-age (new file name on change). |
| `PUT /school/children/<id>/person` `{person_id}` | Sets the caller's link (replaces it); returns the child. `person_id` must be a UUID; the server doesn't ask Immich (the app chose it from the caller's own people). |
| `DELETE /school/children/<id>/person` | `204`; removes the caller's link only. |
| `PUT /school/children/<id>/periods` `[{number, start_time, end_time}, …]` | Replaces the bell schedule; returns the child. Lessons with a `period` still in the schedule get its new times; lessons whose period was removed keep their times and get `period: null`. One transaction. |
| `POST /school/children/<id>/entries` `{kind, weekday, title, period? \| start_time + end_time, place?, teacher?, note?}` | `201` entry. A lesson with `period` takes its times from the bell schedule (`400` if that period doesn't exist); otherwise both times are required. `period` on an activity is `400`. |
| `PATCH /school/children/<id>/entries/<entry>` | Same fields; `null` clears an optional one. Validated against the resulting entry. |
| `DELETE /school/children/<id>/entries/<entry>` | `204`. |
| `POST /school/children/<id>/copy-day` `{from, to, kinds?: ["lesson"]}` | Replaces the `to` day's entries of those kinds with copies of the `from` day's; returns the child. `kinds` defaults to lessons only. `from == to` is `400`. |
| `POST /school/children/<id>/copy-periods` `{from_child}` | Copies another child's bell schedule (same school); returns the child. Same lesson-time rule as `PUT …/periods`. |

- Validation → `400` with a message: times not `HH:MM` or `end_time <= start_time`; `weekday`
  outside 1..7; `kind` not `lesson`/`activity`; unknown `color`; duplicate `number` in a bell
  schedule or `number` outside 0..15; empty or too long texts; more than 20 entries on one
  day; body not the expected JSON type.
- Overlapping entries are allowed (the server doesn't judge a timetable).
- `404`: unknown child, or an entry that is not that child's. `401`/`502` as `/wall`.

## Tests

`tests/test_school.py`: children CRUD and cascade; bell schedule replace (lesson times
follow, removed period → `period: null` with times kept); lesson by period and by explicit
times; activity rules; every validation rule; ordering; photo upload/replace/delete removes
files; person links are per caller (user A's link is not visible to user B, B can set their
own, deleting the child removes both); copy-day (replaces only the given
kinds, leaves activities when copying lessons); copy-periods; `401` without a token; schema
version change keeps a `.bak` and leaves the other databases alone.

## Acceptance criteria

- [ ] Endpoints above with token auth and the optional Picfort key.
- [ ] `school.db` at `SCHOOL_DB_PATH`; `/data/school.db` in the Docker image.
- [ ] Tests pass in CI; README (API table, backup line) and the `DATA_LOCATION` comment in
      `docker/example.env` updated.

## Out of scope

- A/B alternating weeks, terms, a holidays / no-school-days calendar, substitutions.
- Import from an e-register (Librus, Vulcan): v2 candidate; neither has an official open API.
- Notifications ("Ola finishes in 15 minutes").
- A server-side "today" summary: the phone computes it (ADR 0008: the server doesn't know the
  family's date).
