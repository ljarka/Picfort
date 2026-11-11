# T-036 — Birthdays as events: Picfort server

**Status:** ready  
**Component:** ImageFrameServer  
**Depends on:** T-033 (`birth_date` on members), T-035 (recurring events), [ADR 0010](../decisions/0010-family-members-birthdays-recurring-events.md)

## Summary

Setting a member's birth date creates a yearly **birthday event**; changing or clearing it updates or
removes the event; deleting a manual member removes it.

## Data (`events.db`, `SCHEMA_VERSION` 2 → 3, additive)

```sql
ALTER TABLE events ADD COLUMN kind      TEXT;   -- null | 'birthday'
ALTER TABLE events ADD COLUMN member_id TEXT;   -- family.db user_id
CREATE UNIQUE INDEX events_member ON events(member_id) WHERE member_id IS NOT NULL;
```

## Behaviour

A change to `family.db` (T-033) runs in the same request as the change:
- `birth_date` set (no event yet) → create `{kind: 'birthday', member_id, title: <display_name or
  "Birthday">, start_date: the birth date, repeat: 'yearly', created_by: the caller}`. For
  `--MM-DD` the first date is that day in the **current year** (the app passes `today`, see below).
- `birth_date` changed → update `start_date` (and `title`).
- `display_name` changed → update `title`.
- `birth_date` cleared, or the manual member deleted → delete the event.
- Event JSON gains `turns`: the age on the occurrence, when the birth year is known (else null).
  `occurrence_*` are the usual (T-035).
- Without a year the series starts in the current year, so the "past" never lists a birthday that
  was never going to happen.

`PATCH /family/<id>` and `POST /family/members` take an optional `today` (`YYYY-MM-DD`, the phone's
date, ADR 0008) used only for the year of a `--MM-DD` birthday; default: the server's date.

## Birthday events in the events API

- `PATCH /events/<id>` on a birthday event: `start_date`, `end_date`, `repeat`, `repeat_until`, `title`
  → `409` "Edit the birthday in Family settings". `start_time`, `location`, `note`, `album_id` allowed.
- `DELETE /events/<id>` on a birthday event → `409`.
- `POST /events` with `kind` or `member_id` → `400` (only the family code creates them).

## Tests

`tests/test_events.py` and `tests/test_family.py`: create, change, clear, rename, delete a manual member;
a birthday without a year; Feb 29; `turns` on the right occurrence; the `409` rules; the album can be set;
one event per member (unique index); existing events survive the v2 → v3 migration.

## Acceptance criteria

- [ ] Birthday events follow the member as above, in one transaction with the member change.
- [ ] `turns` returned for birth dates with a year.
- [ ] Tests pass in CI; README (API table, release notes line) updated.
