# T-031 — Family profiles: Picfort server

**Status:** in-progress (server code and tests done; released with the next server version)  
**Component:** ImageFrameServer  
**Depends on:** T-018 (`store.py`, `register_auth` prefixes), [ADR 0008](../decisions/0008-family-hub-storage.md)

## Summary

A shared, family-wide profile for each Immich user: a display name, a photo (usually a face
from Immich, chosen in T-032) and whether the account is hidden from the family features.
Immich can't do this for us: a user can change only their own profile picture, and only an
admin can rename users.

## Data (`family.db`, `SCHEMA_VERSION = 1`)

```sql
CREATE TABLE profiles (
  user_id       TEXT PRIMARY KEY,       -- Immich user id (UUID)
  display_name  TEXT,                   -- 1..50 chars, trimmed; null = Immich name
  photo         TEXT,                   -- file name in PROFILE_PHOTOS_PATH; null = Immich picture
  hidden        INTEGER,                -- 1/0; null = not set (the app's default applies)
  updated_by    TEXT NOT NULL,          -- Immich user id (from the token)
  updated       TEXT NOT NULL           -- ISO 8601 UTC
);
```

`FAMILY_DB_PATH` (default `family.db`) and `PROFILE_PHOTOS_PATH` (default `profile_photos`);
add `/data/family.db` and `/data/profile_photos` to the `Dockerfile` `ENV` block. Add
`/family` to `AUTH_PREFIXES`.

## API

A profile: `{"user_id", "display_name", "photo_url", "hidden", "updated_by", "updated"}`
(unset fields `null`).

| Endpoint | Result |
|---|---|
| `GET /family` | `{"profiles": [profile, …]}`: only users that have a row. |
| `PATCH /family/<user_id>` `{display_name?, hidden?}` | Creates the row if missing; `null` clears a field. Returns the profile. Anyone in the family, for any user (owner decision: it's family config). |
| `PUT /family/<user_id>/photo` multipart `photo` | JPEG or PNG up to 2 MB, checked like wall photos; replaces and deletes the old file. Returns the profile. |
| `DELETE /family/<user_id>/photo` | `204`; back to the Immich picture. |
| `GET /family/photo/<file>` | The photo, `Cache-Control: private`, long max-age (a new file name on every change). |

- Validation → `400`: `user_id` not a UUID, `display_name` empty or too long, `hidden` not
  boolean/null, body not a JSON object.
- The server doesn't check that `user_id` is an Immich user: the app picks it from its synced
  user list. A row for a user deleted from Immich is harmless.
- A row with every field null is deleted.

## Tests

`tests/test_family.py`: create by `PATCH`, clear with `null`, delete when empty; photo
upload / replace / delete removes files; validation; `401` without a token; schema version
change keeps a `.bak` and leaves the other databases alone.

## Acceptance criteria

- [x] Endpoints above with token auth and the optional Picfort key.
- [x] `family.db` and photos under `/data` in Docker.
- [x] Tests pass in CI; README (API table, backup line) updated.

## Out of scope

- Per-user private nicknames (a profile is the same for the whole family).
- Writing names or pictures back to Immich.
