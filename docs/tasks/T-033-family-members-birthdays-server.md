# T-033 — Family members without Immich, birth dates: Picfort server

**Status:** in-progress (server code and tests done; released with the next server version)  
**Component:** ImageFrameServer  
**Depends on:** T-031 (`family.db`), [ADR 0010](../decisions/0010-family-members-birthdays-recurring-events.md) (additive migration)

## Summary

`family.db` also holds **manual members** (people who aren't Immich users) and a **birth date**
for any member. The birthday events themselves are T-036.

## Data (`family.db`, `SCHEMA_VERSION` 1 → 2, additive)

```sql
ALTER TABLE profiles ADD COLUMN manual     INTEGER NOT NULL DEFAULT 0;  -- 1 = not an Immich user
ALTER TABLE profiles ADD COLUMN birth_date TEXT;                        -- YYYY-MM-DD or --MM-DD
```

- Migration: copy `family.db` to `family.db.v1.bak`, then `ALTER TABLE`, then `user_version = 2`.
  Existing rows keep every value. Test that.
- A manual member: `user_id` = a UUID4 made by the server, `display_name` required (1..50).
- "A row with every field null is deleted" (T-031) now means: `manual = 0` and every other field null.
  A manual member is never deleted that way.

## API (extends T-031)

| Endpoint | Change |
|---|---|
| `GET /family` | Profiles gain `manual` (bool) and `birth_date` (string or null). |
| `POST /family/members` `{display_name, birth_date?}` | `201` profile with a new `user_id`, `manual: true`. |
| `PATCH /family/<id>` | Also accepts `birth_date` (`null` clears). For a manual member `display_name` can't be cleared (`400`). |
| `PUT` / `DELETE /family/<id>/photo` | Work for manual members too (for them "back to the Immich picture" means no photo). |
| `DELETE /family/<id>` | `204` for a manual member, its photo file included. `409` for an Immich user's row (clear its fields instead). `404` unknown. |

- `birth_date`: a real date, not in the future, year ≥ 1900; or `--MM-DD` with a month/day that
  exists (`--02-29` is allowed). Anything else `400`.
- `hidden` works for manual members as for Immich users (the app decides what it hides).
- Deleting a manual member will also delete its birthday event (T-036); until then nothing links.

## Tests

`tests/test_family.py`: v1 → v2 migration keeps data and leaves a `.bak`; create, rename, delete a
manual member; `display_name` required; birth date accepted (`1990-05-17`, `--02-29`) and
rejected (future, `--02-30`, `1850-01-01`, text); photo upload and delete for a manual member;
`DELETE` an Immich user's row → `409`; `401` without a token.

## Acceptance criteria

- [x] Endpoints above with token auth and the optional Picfort key.
- [x] Additive migration with `.bak`, tested on a v1 file with data.
- [x] Tests pass in CI; README (API table, release notes line) updated.

## Out of scope

- Birthday events (T-036), the app (T-034).
- Manual members posting on the wall.

## Implementation notes

- `store.py` gained a generic `SqliteStore.MIGRATIONS` ({old version: [SQL]}): an additive migration copies the
  file to `<name>.v<old>.bak`, runs the statements and bumps `user_version`. A version with no migration still
  keeps a `.bak` and starts empty (ADR 0008). T-035 and T-036 reuse it for `events.db`.
- `family.py`: `add_member`, `delete_member`, `birth_date` in `update`; birth dates may be at most one day ahead
  of the server's date (the phone can be ahead of UTC).
- Release note for the server version: **`family.db` is migrated from v1 to v2 in place; a copy stays as
  `family.db.v1.bak`.**
