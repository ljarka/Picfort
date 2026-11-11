"""Family events (trips, holidays, birthdays) stored in SQLite (T-022, ADR 0008)."""

import re
from contextlib import closing
from datetime import date

from store import SqliteStore, StoreError, utc_now

MAX_TITLE = 100
MAX_LOCATION = 200
MAX_NOTE = 2000
DEFAULT_LIMIT = 50
MAX_LIMIT = 100

DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
TIME_RE = re.compile(r"^([01]\d|2[0-3]):[0-5]\d$")
UUID_RE = re.compile(r"^[0-9a-fA-F-]{36}$")

FIELDS = ("title", "start_date", "end_date", "start_time", "location", "note", "album_id")

SCHEMA = """
CREATE TABLE events (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  title       TEXT NOT NULL,
  start_date  TEXT NOT NULL,
  end_date    TEXT,
  start_time  TEXT,
  location    TEXT,
  note        TEXT,
  album_id    TEXT,
  created_by  TEXT NOT NULL,
  created     TEXT NOT NULL,
  updated     TEXT NOT NULL
);
CREATE INDEX events_start ON events(start_date);
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


def _validate_event(data: dict) -> dict:
    title = _title(data.get("title"))
    start_date = _date(data.get("start_date"), "start_date")
    end_date = _optional_date(data.get("end_date"), "end_date")
    if end_date is not None:
        if end_date < start_date:
            raise StoreError("end_date must not be before start_date")
        if end_date == start_date:
            end_date = None
    return {
        "title": title,
        "start_date": start_date,
        "end_date": end_date,
        "start_time": _start_time(data.get("start_time")),
        "location": _optional_text(data.get("location"), "location", MAX_LOCATION),
        "note": _optional_text(data.get("note"), "note", MAX_NOTE),
        "album_id": _album_id(data.get("album_id")),
    }


class EventStore(SqliteStore):
    SCHEMA_VERSION = 1
    SCHEMA = SCHEMA
    NAME = "Events"

    def upcoming(self, after: str) -> list:
        after = _date(after, "after")
        with closing(self.connect()) as conn:
            rows = conn.execute(
                "SELECT * FROM events WHERE coalesce(end_date, start_date) >= ?"
                " ORDER BY start_date ASC, CASE WHEN start_time IS NULL THEN 0 ELSE 1 END ASC,"
                " start_time ASC, id ASC",
                (after,),
            ).fetchall()
        return [self._json(r) for r in rows]

    def past(self, before: str, limit: int = DEFAULT_LIMIT, offset: int = 0) -> list:
        before = _date(before, "before")
        if isinstance(limit, bool) or not isinstance(limit, int) or not 1 <= limit <= MAX_LIMIT:
            raise StoreError(f"limit must be between 1 and {MAX_LIMIT}")
        if isinstance(offset, bool) or not isinstance(offset, int) or offset < 0:
            raise StoreError("offset must be >= 0")
        with closing(self.connect()) as conn:
            rows = conn.execute(
                "SELECT * FROM events WHERE coalesce(end_date, start_date) < ?"
                " ORDER BY coalesce(end_date, start_date) DESC, start_date DESC, id DESC"
                " LIMIT ? OFFSET ?",
                (before, limit, offset),
            ).fetchall()
        return [self._json(r) for r in rows]

    def get(self, event_id: int) -> dict:
        with closing(self.connect()) as conn:
            return self._json(self._row(conn, event_id))

    def create(self, user_id: str, body: dict) -> dict:
        fields = _validate_event(body)
        now = self.clock()
        with closing(self.connect()) as conn, conn:
            cur = conn.execute(
                "INSERT INTO events (title, start_date, end_date, start_time, location, note,"
                " album_id, created_by, created, updated) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (
                    fields["title"],
                    fields["start_date"],
                    fields["end_date"],
                    fields["start_time"],
                    fields["location"],
                    fields["note"],
                    fields["album_id"],
                    user_id,
                    now,
                    now,
                ),
            )
            return self._json(self._row(conn, cur.lastrowid))

    def update(self, event_id: int, body: dict) -> dict:
        with closing(self.connect()) as conn, conn:
            row = self._row(conn, event_id)
            if not any(f in body for f in FIELDS):
                return self._json(row)
            merged = {f: body[f] if f in body else row[f] for f in FIELDS}
            fields = _validate_event(merged)
            now = self.clock()
            conn.execute(
                "UPDATE events SET title = ?, start_date = ?, end_date = ?, start_time = ?,"
                " location = ?, note = ?, album_id = ?, updated = ? WHERE id = ?",
                (
                    fields["title"],
                    fields["start_date"],
                    fields["end_date"],
                    fields["start_time"],
                    fields["location"],
                    fields["note"],
                    fields["album_id"],
                    now,
                    event_id,
                ),
            )
            return self._json(self._row(conn, event_id))

    def delete(self, event_id: int, user_id: str):
        with closing(self.connect()) as conn, conn:
            row = self._row(conn, event_id)
            if row["created_by"] != user_id:
                raise StoreError("Only the creator can delete this event", 403)
            conn.execute("DELETE FROM events WHERE id = ?", (event_id,))

    @staticmethod
    def _row(conn, event_id: int):
        row = conn.execute("SELECT * FROM events WHERE id = ?", (event_id,)).fetchone()
        if row is None:
            raise StoreError("Event not found", 404)
        return row

    @staticmethod
    def _json(row) -> dict:
        return {
            "id": row["id"],
            "title": row["title"],
            "start_date": row["start_date"],
            "end_date": row["end_date"],
            "start_time": row["start_time"],
            "location": row["location"],
            "note": row["note"],
            "album_id": row["album_id"],
            "created_by": row["created_by"],
            "created": row["created"],
            "updated": row["updated"],
        }
