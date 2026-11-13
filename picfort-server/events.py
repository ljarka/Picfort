"""Family events (trips, holidays, birthdays), optionally repeating, stored in SQLite
(T-022, T-035, ADR 0008, ADR 0010)."""

import calendar
import json
import re
from contextlib import closing
from datetime import date, timedelta

from store import SqliteStore, StoreError, utc_now

MAX_TITLE = 100
MAX_LOCATION = 200
MAX_NOTE = 2000
DEFAULT_LIMIT = 50
MAX_LIMIT = 100

DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
TIME_RE = re.compile(r"^([01]\d|2[0-3]):[0-5]\d$")
UUID_RE = re.compile(r"^[0-9a-fA-F-]{36}$")
MEMBER_ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
MAX_MEMBERS = 30

FIELDS = ("title", "start_date", "end_date", "start_time", "location", "note", "album_id", "repeat", "repeat_until")
REPEATS = ("weekly", "monthly", "yearly")

SCHEMA = """
CREATE TABLE events (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  title        TEXT NOT NULL,
  start_date   TEXT NOT NULL,
  end_date     TEXT,
  start_time   TEXT,
  location     TEXT,
  note         TEXT,
  album_id     TEXT,
  repeat       TEXT,
  repeat_until TEXT,
  kind         TEXT,
  member_id    TEXT,
  members      TEXT,
  created_by   TEXT NOT NULL,
  created      TEXT NOT NULL,
  updated      TEXT NOT NULL
);
CREATE INDEX events_start ON events(start_date);
CREATE UNIQUE INDEX events_member ON events(member_id) WHERE member_id IS NOT NULL;
"""


def _title(value) -> str:
    if not isinstance(value, str) or not value.strip():
        raise StoreError("Title must not be empty")
    value = value.strip()
    if len(value) > MAX_TITLE:
        raise StoreError(f"Title must be at most {MAX_TITLE} characters")
    return value


def _date(value, name: str) -> str:
    if not isinstance(value, str) or not DATE_RE.match(value):
        raise StoreError(f"{name} must be a YYYY-MM-DD date")
    try:
        date.fromisoformat(value)
    except ValueError:
        raise StoreError(f"{name} is not a valid calendar date")
    return value


def _optional_date(value, name: str):
    if value is None:
        return None
    return _date(value, name)


def _start_time(value):
    if value is None:
        return None
    if not isinstance(value, str) or not TIME_RE.match(value):
        raise StoreError("start_time must be HH:MM (24-hour)")
    return value


def _optional_text(value, name: str, max_len: int):
    if value is None:
        return None
    if not isinstance(value, str):
        raise StoreError(f"{name} must be a string")
    value = value.strip()
    if len(value) > max_len:
        raise StoreError(f"{name} must be at most {max_len} characters")
    return value or None


def _album_id(value):
    if value is None:
        return None
    if not isinstance(value, str) or not UUID_RE.match(value):
        raise StoreError("album_id must be a UUID")
    return value


def _repeat(value):
    if value is None:
        return None
    if value not in REPEATS:
        raise StoreError("repeat must be one of " + ", ".join(REPEATS) + " or null")
    return value


def _members(value) -> list:
    """Who the event is for (T-053): family member ids, in the order chosen, without repeats."""
    if value is None:
        return []
    if not isinstance(value, list):
        raise StoreError("members must be a list of member ids")
    if len(value) > MAX_MEMBERS:
        raise StoreError(f"members must have at most {MAX_MEMBERS} ids")
    result = []
    for member_id in value:
        if not isinstance(member_id, str) or not MEMBER_ID_RE.match(member_id):
            raise StoreError("members must be member ids")
        if member_id not in result:
            result.append(member_id)
    return result


def _stored_members(row) -> list:
    raw = row["members"]
    return json.loads(raw) if raw else []


def _add_months(first: date, months: int) -> date:
    """`first` moved by whole months; a day that doesn't exist (31st, 29 Feb) becomes the month's last."""
    index = first.year * 12 + first.month - 1 + months
    year, month = divmod(index, 12)
    month += 1
    return date(year, month, min(first.day, calendar.monthrange(year, month)[1]))


def occurrence_start(first: date, repeat, n: int) -> date:
    """Start of the n-th occurrence (n = 0 is the first). Always computed from `first`, never chained."""
    if repeat == "weekly":
        return first + timedelta(weeks=n)
    if repeat == "monthly":
        return _add_months(first, n)
    if repeat == "yearly":
        return _add_months(first, 12 * n)
    return first


def _periods_between(first: date, other: date, repeat) -> int:
    """Whole periods from `first` to `other`, rounded so that occurrence(n) is never after `other` + a period."""
    if repeat == "weekly":
        return (other - first).days // 7
    months = (other.year - first.year) * 12 + other.month - first.month
    return months if repeat == "monthly" else months // 12


def _series(row):
    first = date.fromisoformat(row["start_date"])
    length = (date.fromisoformat(row["end_date"]) - first) if row["end_date"] else timedelta(0)
    until = date.fromisoformat(row["repeat_until"]) if row["repeat_until"] else None
    return first, length, until


def next_occurrence(row, on: date):
    """The first occurrence that is ongoing or ahead on `on` (end >= on), or None when the series has ended."""
    first, length, until = _series(row)
    repeat = row["repeat"]
    if not repeat:
        return (first, first + length) if first + length >= on else None
    n = max(0, _periods_between(first, on - length, repeat) - 1)
    while occurrence_start(first, repeat, n) + length < on:
        n += 1
    start = occurrence_start(first, repeat, n)
    if until is not None and start > until:
        return None
    return start, start + length


def last_occurrence(row):
    """The final occurrence of a series that has an end date; for a single event, itself."""
    first, length, until = _series(row)
    repeat = row["repeat"]
    if not repeat or until is None:
        return first, first + length
    n = max(0, _periods_between(first, until, repeat))
    while n > 0 and occurrence_start(first, repeat, n) > until:
        n -= 1
    start = occurrence_start(first, repeat, n)
    return start, start + length


def _validate_event(data: dict) -> dict:
    title = _title(data.get("title"))
    start_date = _date(data.get("start_date"), "start_date")
    end_date = _optional_date(data.get("end_date"), "end_date")
    if end_date is not None:
        if end_date < start_date:
            raise StoreError("end_date must not be before start_date")
        if end_date == start_date:
            end_date = None
    repeat = _repeat(data.get("repeat"))
    repeat_until = _optional_date(data.get("repeat_until"), "repeat_until")
    if repeat_until is not None:
        if repeat is None:
            raise StoreError("repeat_until needs repeat")
        if repeat_until < start_date:
            raise StoreError("repeat_until must not be before start_date")
    return {
        "title": title,
        "start_date": start_date,
        "end_date": end_date,
        "start_time": _start_time(data.get("start_time")),
        "location": _optional_text(data.get("location"), "location", MAX_LOCATION),
        "note": _optional_text(data.get("note"), "note", MAX_NOTE),
        "album_id": _album_id(data.get("album_id")),
        "repeat": repeat,
        "repeat_until": repeat_until,
        "members": _members(data.get("members")),
    }


class EventStore(SqliteStore):
    SCHEMA_VERSION = 4
    SCHEMA = SCHEMA
    NAME = "Events"
    MIGRATIONS = {
        1: [
            "ALTER TABLE events ADD COLUMN repeat TEXT",
            "ALTER TABLE events ADD COLUMN repeat_until TEXT",
        ],
        2: [
            "ALTER TABLE events ADD COLUMN kind TEXT",
            "ALTER TABLE events ADD COLUMN member_id TEXT",
            "CREATE UNIQUE INDEX events_member ON events(member_id) WHERE member_id IS NOT NULL",
        ],
        3: ["ALTER TABLE events ADD COLUMN members TEXT"],
    }

    def __init__(self, db_path: str, clock=utc_now, family=None):
        self.family = family
        self._birth_dates = {}
        super().__init__(db_path, clock=clock)

    def upcoming(self, after: str) -> list:
        """One entry per event: the occurrence that is ongoing or next on `after`; ended series are left out."""
        day = date.fromisoformat(_date(after, "after"))
        with closing(self.connect()) as conn:
            rows = conn.execute("SELECT * FROM events").fetchall()
        entries = []
        for row in rows:
            occurrence = next_occurrence(row, day)
            if occurrence is not None:
                entries.append((row, occurrence))
        entries.sort(key=lambda e: (e[1][0], 0 if e[0]["start_time"] is None else 1, e[0]["start_time"] or "", e[0]["id"]))
        return [self._json(row, occurrence) for row, occurrence in entries]

    def past(self, before: str, limit: int = DEFAULT_LIMIT, offset: int = 0) -> list:
        """Events that ended before `before`; a series only once it has ended, with its last occurrence."""
        day = date.fromisoformat(_date(before, "before"))
        if isinstance(limit, bool) or not isinstance(limit, int) or not 1 <= limit <= MAX_LIMIT:
            raise StoreError(f"limit must be between 1 and {MAX_LIMIT}")
        if isinstance(offset, bool) or not isinstance(offset, int) or offset < 0:
            raise StoreError("offset must be >= 0")
        with closing(self.connect()) as conn:
            rows = conn.execute(
                "SELECT * FROM events WHERE repeat IS NOT NULL"
                " OR coalesce(end_date, start_date) < ?",
                (before,),
            ).fetchall()
        entries = []
        for row in rows:
            if row["repeat"] and not row["repeat_until"]:
                continue  # repeats forever: never past
            occurrence = last_occurrence(row)
            if occurrence[1] < day:
                entries.append((row, occurrence))
        entries.sort(key=lambda e: (e[1][1], e[1][0], e[0]["id"]), reverse=True)
        return [self._json(row, occurrence) for row, occurrence in entries[offset:offset + limit]]

    def get(self, event_id: int, on: str = None) -> dict:
        """The event; for a series, the occurrence on `on` (ongoing, next, or the last if it ended), else the first."""
        day = date.fromisoformat(_date(on, "on")) if on is not None else None
        with closing(self.connect()) as conn:
            row = self._row(conn, event_id)
        occurrence = None
        if day is not None:
            occurrence = next_occurrence(row, day) or last_occurrence(row)
        return self._json(row, occurrence)

    def create(self, user_id: str, body: dict) -> dict:
        if "kind" in body or "member_id" in body:
            raise StoreError("kind and member_id cannot be set directly", 400)
        fields = _validate_event(body)
        now = self.clock()
        with closing(self.connect()) as conn, conn:
            cur = conn.execute(
                "INSERT INTO events (title, start_date, end_date, start_time, location, note,"
                " album_id, repeat, repeat_until, kind, member_id, members, created_by, created, updated)"
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?, ?)",
                (
                    fields["title"],
                    fields["start_date"],
                    fields["end_date"],
                    fields["start_time"],
                    fields["location"],
                    fields["note"],
                    fields["album_id"],
                    fields["repeat"],
                    fields["repeat_until"],
                    json.dumps(fields["members"]) if fields["members"] else None,
                    user_id,
                    now,
                    now,
                ),
            )
            return self._json(self._row(conn, cur.lastrowid))

    def update(self, event_id: int, body: dict) -> dict:
        with closing(self.connect()) as conn, conn:
            row = self._row(conn, event_id)
            if row["kind"] == "birthday":
                forbidden = [f for f in ("start_date", "end_date", "repeat", "repeat_until", "title") if f in body]
                if forbidden:
                    raise StoreError("Edit the birthday in Family settings", 409)
            if not any(f in body for f in FIELDS + ("members",)):
                return self._json(row)
            merged = {f: body[f] if f in body else row[f] for f in FIELDS}
            merged["members"] = body["members"] if "members" in body else _stored_members(row)
            if "repeat" in body and body["repeat"] is None and "repeat_until" not in body:
                merged["repeat_until"] = None  # no repeat, no end of the repeat
            fields = _validate_event(merged)
            now = self.clock()
            conn.execute(
                "UPDATE events SET title = ?, start_date = ?, end_date = ?, start_time = ?,"
                " location = ?, note = ?, album_id = ?, repeat = ?, repeat_until = ?, members = ?,"
                " updated = ? WHERE id = ?",
                (
                    fields["title"],
                    fields["start_date"],
                    fields["end_date"],
                    fields["start_time"],
                    fields["location"],
                    fields["note"],
                    fields["album_id"],
                    fields["repeat"],
                    fields["repeat_until"],
                    json.dumps(fields["members"]) if fields["members"] else None,
                    now,
                    event_id,
                ),
            )
            return self._json(self._row(conn, event_id))

    def delete(self, event_id: int, user_id: str):
        with closing(self.connect()) as conn, conn:
            row = self._row(conn, event_id)
            if row["kind"] == "birthday":
                raise StoreError("Edit the birthday in Family settings", 409)
            if row["created_by"] != user_id:
                raise StoreError("Only the creator can delete this event", 403)
            conn.execute("DELETE FROM events WHERE id = ?", (event_id,))

    def sync_birthday(self, member_id: str, display_name: str | None, birth_date: str | None,
                      actor_id: str, today: str = None) -> dict | None:
        if birth_date is None:
            self.delete_birthday(member_id)
            return None
        self._birth_dates[member_id] = birth_date
        title = (display_name or "").strip() or "Birthday"
        if birth_date.startswith("--"):
            ref = date.fromisoformat(today) if today else date.fromisoformat(self.clock()[:10])
            month = int(birth_date[2:4])
            day = int(birth_date[5:7])
            max_day = calendar.monthrange(ref.year, month)[1]
            start_date = f"{ref.year:04d}-{month:02d}-{min(day, max_day):02d}"
        else:
            start_date = birth_date

        now = self.clock()
        with closing(self.connect()) as conn, conn:
            row = conn.execute("SELECT * FROM events WHERE member_id = ?", (member_id,)).fetchone()
            if row is None:
                cur = conn.execute(
                    "INSERT INTO events (title, start_date, end_date, start_time, location, note,"
                    " album_id, repeat, repeat_until, kind, member_id, created_by, created, updated)"
                    " VALUES (?, ?, NULL, NULL, NULL, NULL, NULL, 'yearly', NULL, 'birthday', ?, ?, ?, ?)",
                    (title, start_date, member_id, actor_id, now, now),
                )
                return self._json(self._row(conn, cur.lastrowid))
            else:
                conn.execute(
                    "UPDATE events SET title = ?, start_date = ?, updated = ? WHERE id = ?",
                    (title, start_date, now, row["id"]),
                )
                return self._json(self._row(conn, row["id"]))

    def delete_birthday(self, member_id: str):
        self._birth_dates.pop(member_id, None)
        with closing(self.connect()) as conn, conn:
            conn.execute("DELETE FROM events WHERE member_id = ?", (member_id,))

    @staticmethod
    def _row(conn, event_id: int):
        row = conn.execute("SELECT * FROM events WHERE id = ?", (event_id,)).fetchone()
        if row is None:
            raise StoreError("Event not found", 404)
        return row

    def _turns(self, row, occ_start: date):
        if row["kind"] != "birthday" or not row["member_id"]:
            return None
        birth_date = None
        if self.family is not None:
            birth_date = self.family.get_birth_date(row["member_id"])
        if birth_date is None:
            birth_date = self._birth_dates.get(row["member_id"])
        if birth_date and not birth_date.startswith("--"):
            try:
                birth_year = int(birth_date[:4])
                return occ_start.year - birth_year
            except ValueError:
                return None
        return None

    def _json(self, row, occurrence=None) -> dict:
        """`occurrence` is (start, end) as dates; default is the first occurrence (= start_date / end_date)."""
        if occurrence is None:
            occurrence = _series(row)[0], _series(row)[0] + _series(row)[1]
        occ_start, occ_end = occurrence
        return {
            "id": row["id"],
            "title": row["title"],
            "start_date": row["start_date"],
            "end_date": row["end_date"],
            "start_time": row["start_time"],
            "location": row["location"],
            "note": row["note"],
            "album_id": row["album_id"],
            "repeat": row["repeat"],
            "repeat_until": row["repeat_until"],
            "kind": row["kind"],
            "member_id": row["member_id"],
            "members": _stored_members(row),
            "occurrence_start": occ_start.isoformat(),
            "occurrence_end": occ_end.isoformat() if occ_end > occ_start else None,
            "turns": self._turns(row, occ_start),
            "created_by": row["created_by"],
            "created": row["created"],
            "updated": row["updated"],
        }
