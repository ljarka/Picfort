# T-035 — Recurring events: Picfort server

**Status:** done  
**Component:** ImageFrameServer  
**Depends on:** T-022 (`events.db`), [ADR 0010](../decisions/0010-family-members-birthdays-recurring-events.md)

## Summary

An event can repeat every week, month or year, up to an optional end date.

## Data (`events.db`, `SCHEMA_VERSION` 1 → 2, additive)

```sql
ALTER TABLE events ADD COLUMN repeat       TEXT;   -- null | 'weekly' | 'monthly' | 'yearly'
ALTER TABLE events ADD COLUMN repeat_until TEXT;   -- YYYY-MM-DD, inclusive; null = forever
```

Migration as ADR 0010 (copy to `events.db.v1.bak`, `ALTER TABLE`, `user_version = 2`).
T-036 adds `kind` and `member_id`.

## Occurrences

For a series with first date `S`, optional `E` (end date, so length = `E − S`) and `repeat_until` `U`:
the n-th occurrence starts on `S + n` weeks / months / years (n = 0, 1, …); an occurrence exists
only if its start ≤ `U`. If the day doesn't exist in the month (31st, 29 February) use the last
day of that month, computed from `S` each time (not from the previous occurrence). The length is kept.

## API (extends T-022)

An event gains `repeat`, `repeat_until` and, in **list** responses, `occurrence_start` and
`occurrence_end` (`occurrence_end` null for one day). For non-repeating events they equal
`start_date` / `end_date`, so the app can always read them.

| Endpoint | Change |
|---|---|
| `POST` / `PATCH /events` | Accept `repeat` and `repeat_until`. `repeat_until` requires `repeat` and must be ≥ `start_date`; clearing `repeat` clears `repeat_until`. |
| `GET /events?after=D` | One entry per event: for a series the first occurrence with `occurrence_end ?? occurrence_start ≥ D` (ongoing or next); a series with none is omitted. Sorted by `occurrence_start`, then time, then `id`. |
| `GET /events?before=D…` | Non-repeating as before. A series appears only if it has ended (all occurrences before `D`), with its **last** occurrence, sorted by `occurrence_end`. |
| `GET /events/<id>` | The event; `occurrence_*` = the occurrence for the `?on=D` parameter if given, else the first one. |

- `start_date` / `end_date` always stay those of the first occurrence (what the form edits).
- Editing a series changes all occurrences; no exceptions or "this one only" (v2).
- Validation `400`: unknown `repeat`; `repeat_until` without `repeat` or before `start_date`.

## Tests

`tests/test_events.py`: weekly, monthly (31st into February, April), yearly (29 February in leap and
other years); multi-day length kept; `repeat_until` inclusive and after the last occurrence (series
omitted from upcoming, listed in past); ongoing occurrence of a repeating multi-day event;
ordering across series and single events; PATCH rules; v1 → v2 migration keeps events and leaves `.bak`.

## Acceptance criteria

- [ ] Endpoints above, existing clients still working for non-repeating events.
- [ ] Additive migration with `.bak`, tested.
- [ ] Tests pass in CI; README (API table, release notes line) updated.

## Out of scope

- Intervals other than 1, RRULE, exceptions per occurrence, reminders, iCal.
- Birthdays (T-036), the app (T-037).
