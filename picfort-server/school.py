"""School timetables (T-028, ADR 0008): children, each with a bell schedule and a weekly template
of lessons and extracurricular activities. A child's photo is a file; each parent can link the
child to a person in their own Immich library (person ids are per library, T-030).

`school.db` only; no migrations yet: a schema change starts empty with a `.bak` (store.py).
"""

import os
import re
import uuid
from contextlib import closing
from io import BytesIO

from PIL import Image, UnidentifiedImageError

from store import SqliteStore, StoreError, utc_now

MAX_NAME = 50
MAX_TITLE = 100
MAX_SHORT = 100
MAX_NOTE = 500
MAX_PER_DAY = 20
MAX_PERIOD = 15
MAX_PHOTO_BYTES = 2 * 1024 * 1024
PHOTO_URL_PREFIX = "/school/children/photo/"
PHOTO_FORMATS = {"JPEG": "jpg", "PNG": "png"}
PHOTO_NAME_RE = re.compile(r"^[0-9a-f]{32}\.(jpg|png)$")
UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
TIME_RE = re.compile(r"^([01]\d|2[0-3]):[0-5]\d$")
# Immich's avatar colors (T-002), so a child's chip looks like a family member's ring.
COLORS = ("primary", "pink", "red", "yellow", "blue", "green", "purple", "orange", "gray", "amber")
KINDS = ("lesson", "activity")

SCHEMA = """
CREATE TABLE children (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  name            TEXT NOT NULL,
  color           TEXT NOT NULL,
  immich_user_id  TEXT,
  photo           TEXT,
  created_by      TEXT NOT NULL,
  created         TEXT NOT NULL,
  updated         TEXT NOT NULL
);
CREATE TABLE child_people (
  child_id        INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  immich_user_id  TEXT NOT NULL,
  person_id       TEXT NOT NULL,
  PRIMARY KEY (child_id, immich_user_id)
);
CREATE TABLE periods (
  child_id    INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  number      INTEGER NOT NULL,
  start_time  TEXT NOT NULL,
  end_time    TEXT NOT NULL,
  PRIMARY KEY (child_id, number)
);
CREATE TABLE entries (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  child_id    INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
  kind        TEXT NOT NULL,
  weekday     INTEGER NOT NULL,
  start_time  TEXT NOT NULL,
  end_time    TEXT NOT NULL,
  title       TEXT NOT NULL,
  period      INTEGER,
  place       TEXT,
  teacher     TEXT,
  note        TEXT,
  created_by  TEXT NOT NULL,
  updated     TEXT NOT NULL
);
CREATE INDEX entries_child_day ON entries(child_id, weekday);
"""

ENTRY_FIELDS = ("kind", "weekday", "start_time", "end_time", "title", "period", "place", "teacher", "note")


# --- validation ---

def _text(body: dict, key: str, limit: int, required: bool):
    value = body.get(key)
    if value is None:
        if required:
            raise StoreError(f"{key} is required")
        return None
    if not isinstance(value, str):
        raise StoreError(f"{key} must be text")
    value = value.strip()
    if not value:
        if required:
            raise StoreError(f"{key} must not be empty")
        return None
    if len(value) > limit:
        raise StoreError(f"{key} must be at most {limit} characters")
    return value


def _color(value) -> str:
    if value not in COLORS:
        raise StoreError(f"color must be one of: {', '.join(COLORS)}")
    return value


def _time(value, key: str) -> str:
    if not isinstance(value, str) or not TIME_RE.match(value):
        raise StoreError(f"{key} must be HH:MM")
    return value


def _times(start, end) -> tuple:
    start = _time(start, "start_time")
    end = _time(end, "end_time")
    if end <= start:
        raise StoreError("end_time must be after start_time")
    return start, end


def _int(value, key: str, low: int, high: int) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or not low <= value <= high:
        raise StoreError(f"{key} must be a number from {low} to {high}")
    return value


def _weekday(value, key: str = "weekday") -> int:
    return _int(value, key, 1, 7)


def _periods(body) -> list:
    if not isinstance(body, list):
        raise StoreError("The bell schedule must be a JSON list")
    periods, seen = [], set()
    for p in body:
        if not isinstance(p, dict):
            raise StoreError("Each period must be a JSON object")
        number = _int(p.get("number"), "number", 0, MAX_PERIOD)
        if number in seen:
            raise StoreError(f"Period {number} is listed twice")
        seen.add(number)
        start, end = _times(p.get("start_time"), p.get("end_time"))
        periods.append((number, start, end))
    return sorted(periods)


def _member_id(value):
    """The family member the child is (`immich_user_id`: an Immich user's id or a manual member's,
    both UUIDs from /family), or None."""
    if value is not None and (not isinstance(value, str) or not UUID_RE.match(value)):
        raise StoreError("immich_user_id must be a UUID or null")
    return value


def _inspect_photo(data: bytes) -> str:
    if not data:
        raise StoreError("A photo file is required")
    if len(data) > MAX_PHOTO_BYTES:
        raise StoreError("The photo is larger than 2 MB", 413)
    try:
        with Image.open(BytesIO(data)) as img:
            fmt = img.format
    except (UnidentifiedImageError, OSError):
        raise StoreError("The photo must be JPEG or PNG", 415)
    if fmt not in PHOTO_FORMATS:
        raise StoreError("The photo must be JPEG or PNG", 415)
    return PHOTO_FORMATS[fmt]


class SchoolStore(SqliteStore):
    SCHEMA_VERSION = 1
    SCHEMA = SCHEMA
    NAME = "School"

    def __init__(self, db_path: str, photo_dir: str, clock=utc_now):
        self.photo_dir = photo_dir
        os.makedirs(photo_dir, exist_ok=True)
        super().__init__(db_path, clock=clock)

    # --- reading ---

    def children(self, viewer_id: str) -> list:
        with closing(self.connect()) as conn:
            ids = [r["id"] for r in conn.execute("SELECT id FROM children ORDER BY name COLLATE NOCASE, id")]
            return [self._child(conn, i, viewer_id) for i in ids]

    def photo_path(self, filename: str):
        if not PHOTO_NAME_RE.match(filename):
            return None
        path = os.path.join(self.photo_dir, filename)
        return path if os.path.isfile(path) else None

    # --- children ---

    def create_child(self, actor_id: str, body: dict) -> dict:
        name = _text(body, "name", MAX_NAME, required=True)
        color = _color(body.get("color"))
        member_id = _member_id(body.get("immich_user_id"))
        now = self.clock()
        with closing(self.connect()) as conn, conn:
            cur = conn.execute(
                "INSERT INTO children (name, color, immich_user_id, created_by, created, updated)"
                " VALUES (?, ?, ?, ?, ?, ?)",
                (name, color, member_id, actor_id, now, now),
            )
            return self._child(conn, cur.lastrowid, actor_id)

    def update_child(self, child_id: int, actor_id: str, body: dict) -> dict:
        changes = {}
        if "name" in body:
            changes["name"] = _text(body, "name", MAX_NAME, required=True)
        if "color" in body:
            changes["color"] = _color(body["color"])
        if "immich_user_id" in body:
            changes["immich_user_id"] = _member_id(body["immich_user_id"])
        with closing(self.connect()) as conn, conn:
            self._require_child(conn, child_id)
            if changes:
                sets = ", ".join(f"{k} = ?" for k in changes)
                conn.execute(f"UPDATE children SET {sets}, updated = ? WHERE id = ?",
                             (*changes.values(), self.clock(), child_id))
            return self._child(conn, child_id, actor_id)

    def delete_child(self, child_id: int):
        with closing(self.connect()) as conn, conn:
            row = self._require_child(conn, child_id)
            conn.execute("DELETE FROM children WHERE id = ?", (child_id,))
        if row["photo"]:
            self._remove_file(row["photo"])

    def set_photo(self, child_id: int, actor_id: str, data: bytes) -> dict:
        ext = _inspect_photo(data)
        with closing(self.connect()) as conn:
            self._require_child(conn, child_id)
        filename = f"{uuid.uuid4().hex}.{ext}"
        with open(os.path.join(self.photo_dir, filename), "wb") as f:
            f.write(data)
        try:
            with closing(self.connect()) as conn, conn:
                old = self._require_child(conn, child_id)["photo"]
                conn.execute("UPDATE children SET photo = ?, updated = ? WHERE id = ?",
                             (filename, self.clock(), child_id))
                child = self._child(conn, child_id, actor_id)
        except Exception:
            self._remove_file(filename)
            raise
        if old:
            self._remove_file(old)
        return child

    def delete_photo(self, child_id: int):
        with closing(self.connect()) as conn, conn:
            old = self._require_child(conn, child_id)["photo"]
            conn.execute("UPDATE children SET photo = NULL, updated = ? WHERE id = ?", (self.clock(), child_id))
        if old:
            self._remove_file(old)

    def link_person(self, child_id: int, viewer_id: str, body: dict) -> dict:
        person_id = body.get("person_id")
        if not isinstance(person_id, str) or not UUID_RE.match(person_id):
            raise StoreError("person_id must be a UUID")
        with closing(self.connect()) as conn, conn:
            self._require_child(conn, child_id)
            conn.execute(
                "INSERT INTO child_people (child_id, immich_user_id, person_id) VALUES (?, ?, ?)"
                " ON CONFLICT (child_id, immich_user_id) DO UPDATE SET person_id = excluded.person_id",
                (child_id, viewer_id, person_id),
            )
            return self._child(conn, child_id, viewer_id)

    def unlink_person(self, child_id: int, viewer_id: str):
        with closing(self.connect()) as conn, conn:
            self._require_child(conn, child_id)
            conn.execute("DELETE FROM child_people WHERE child_id = ? AND immich_user_id = ?", (child_id, viewer_id))

    # --- bell schedule ---

    def set_periods(self, child_id: int, actor_id: str, body) -> dict:
        periods = _periods(body)
        with closing(self.connect()) as conn, conn:
            self._require_child(conn, child_id)
            self._replace_periods(conn, child_id, periods)
            return self._child(conn, child_id, actor_id)

    def copy_periods(self, child_id: int, actor_id: str, body: dict) -> dict:
        source = body.get("from_child")
        if isinstance(source, bool) or not isinstance(source, int):
            raise StoreError("from_child must be a child id")
        if source == child_id:
            raise StoreError("from_child must be another child")
        with closing(self.connect()) as conn, conn:
            self._require_child(conn, child_id)
            self._require_child(conn, source)
            periods = [(r["number"], r["start_time"], r["end_time"]) for r in conn.execute(
                "SELECT * FROM periods WHERE child_id = ? ORDER BY number", (source,))]
            self._replace_periods(conn, child_id, periods)
            return self._child(conn, child_id, actor_id)

    def _replace_periods(self, conn, child_id: int, periods: list):
        """Lessons on a kept period take its new times; lessons on a removed one keep theirs."""
        conn.execute("DELETE FROM periods WHERE child_id = ?", (child_id,))
        conn.executemany("INSERT INTO periods (child_id, number, start_time, end_time) VALUES (?, ?, ?, ?)",
                         [(child_id, *p) for p in periods])
        numbers = [p[0] for p in periods]
        for number, start, end in periods:
            conn.execute("UPDATE entries SET start_time = ?, end_time = ? WHERE child_id = ? AND period = ?",
                         (start, end, child_id, number))
        marks = ",".join("?" * len(numbers))
        conn.execute(f"UPDATE entries SET period = NULL WHERE child_id = ? AND period IS NOT NULL"
                     f" AND period NOT IN ({marks})", (child_id, *numbers))
        conn.execute("UPDATE children SET updated = ? WHERE id = ?", (self.clock(), child_id))

    # --- entries ---

    def create_entry(self, child_id: int, actor_id: str, body: dict) -> dict:
        with closing(self.connect()) as conn, conn:
            self._require_child(conn, child_id)
            entry = self._valid_entry(conn, child_id, body, current=None)
            self._check_day_size(conn, child_id, entry["weekday"], exclude=None)
            cur = conn.execute(
                f"INSERT INTO entries (child_id, {', '.join(ENTRY_FIELDS)}, created_by, updated)"
                f" VALUES (?, {', '.join('?' * len(ENTRY_FIELDS))}, ?, ?)",
                (child_id, *(entry[k] for k in ENTRY_FIELDS), actor_id, self.clock()),
            )
            self._touch(conn, child_id)
            return self._entry_json(conn.execute("SELECT * FROM entries WHERE id = ?", (cur.lastrowid,)).fetchone())

    def update_entry(self, child_id: int, entry_id: int, actor_id: str, body: dict) -> dict:
        with closing(self.connect()) as conn, conn:
            self._require_child(conn, child_id)
            row = self._require_entry(conn, child_id, entry_id)
            current = {k: row[k] for k in ENTRY_FIELDS}
            entry = self._valid_entry(conn, child_id, body, current=current)
            if entry["weekday"] != row["weekday"]:
                self._check_day_size(conn, child_id, entry["weekday"], exclude=entry_id)
            conn.execute(
                f"UPDATE entries SET {', '.join(f'{k} = ?' for k in ENTRY_FIELDS)}, updated = ?"
                f" WHERE id = ?",
                (*(entry[k] for k in ENTRY_FIELDS), self.clock(), entry_id),
            )
            self._touch(conn, child_id)
            return self._entry_json(conn.execute("SELECT * FROM entries WHERE id = ?", (entry_id,)).fetchone())

    def delete_entry(self, child_id: int, entry_id: int):
        with closing(self.connect()) as conn, conn:
            self._require_child(conn, child_id)
            self._require_entry(conn, child_id, entry_id)
            conn.execute("DELETE FROM entries WHERE id = ?", (entry_id,))
            self._touch(conn, child_id)

    def copy_day(self, child_id: int, actor_id: str, body: dict) -> dict:
        source = _weekday(body.get("from"), "from")
        target = _weekday(body.get("to"), "to")
        if source == target:
            raise StoreError("from and to must be different days")
        kinds = body.get("kinds", ["lesson"])
        if not isinstance(kinds, list) or not kinds or any(k not in KINDS for k in kinds):
            raise StoreError("kinds must be a list of: lesson, activity")
        marks = ",".join("?" * len(kinds))
        with closing(self.connect()) as conn, conn:
            self._require_child(conn, child_id)
            conn.execute(f"DELETE FROM entries WHERE child_id = ? AND weekday = ? AND kind IN ({marks})",
                         (child_id, target, *kinds))
            rows = conn.execute(f"SELECT * FROM entries WHERE child_id = ? AND weekday = ? AND kind IN ({marks})",
                                (child_id, source, *kinds)).fetchall()
            self._check_day_size(conn, child_id, target, exclude=None, adding=len(rows))
            now = self.clock()
            for r in rows:
                values = {k: r[k] for k in ENTRY_FIELDS}
                values["weekday"] = target
                conn.execute(
                    f"INSERT INTO entries (child_id, {', '.join(ENTRY_FIELDS)}, created_by, updated)"
                    f" VALUES (?, {', '.join('?' * len(ENTRY_FIELDS))}, ?, ?)",
                    (child_id, *(values[k] for k in ENTRY_FIELDS), actor_id, now),
                )
            self._touch(conn, child_id)
            return self._child(conn, child_id, actor_id)

    def _valid_entry(self, conn, child_id: int, body: dict, current) -> dict:
        """The entry after applying `body` to `current` (None: a new one), checked as a whole."""
        merged = dict(current or {})
        for key in ENTRY_FIELDS:
            if key in body:
                merged[key] = body[key]
        kind = merged.get("kind")
        if kind not in KINDS:
            raise StoreError("kind must be lesson or activity")
        entry = {
            "kind": kind,
            "weekday": _weekday(merged.get("weekday")),
            "title": _text(merged, "title", MAX_TITLE, required=True),
            "place": _text(merged, "place", MAX_SHORT, required=False),
            "teacher": _text(merged, "teacher", MAX_SHORT, required=False),
            "note": _text(merged, "note", MAX_NOTE, required=False),
            "period": None,
        }
        period = merged.get("period")
        if period is not None:
            if kind != "lesson":
                raise StoreError("Only a lesson has a period")
            number = _int(period, "period", 0, MAX_PERIOD)
            row = conn.execute("SELECT * FROM periods WHERE child_id = ? AND number = ?",
                               (child_id, number)).fetchone()
            if row is None:
                raise StoreError(f"There is no period {number} in the bell schedule")
            entry["period"] = number
            if current is not None and "period" not in body and ("start_time" in body or "end_time" in body):
                raise StoreError("A lesson with a period takes its times from the bell schedule")
            entry["start_time"], entry["end_time"] = row["start_time"], row["end_time"]
        else:
            if current is None and ("start_time" not in body or "end_time" not in body):
                raise StoreError("start_time and end_time are required without a period")
            entry["start_time"], entry["end_time"] = _times(merged.get("start_time"), merged.get("end_time"))
        return entry

    def _check_day_size(self, conn, child_id: int, weekday: int, exclude, adding: int = 1):
        count = conn.execute("SELECT COUNT(*) FROM entries WHERE child_id = ? AND weekday = ? AND id IS NOT ?",
                             (child_id, weekday, exclude)).fetchone()[0]
        if count + adding > MAX_PER_DAY:
            raise StoreError(f"At most {MAX_PER_DAY} entries on one day")

    # --- helpers ---

    def _touch(self, conn, child_id: int):
        conn.execute("UPDATE children SET updated = ? WHERE id = ?", (self.clock(), child_id))

    @staticmethod
    def _require_child(conn, child_id: int):
        row = conn.execute("SELECT * FROM children WHERE id = ?", (child_id,)).fetchone()
        if row is None:
            raise StoreError("Child not found", 404)
        return row

    @staticmethod
    def _require_entry(conn, child_id: int, entry_id: int):
        row = conn.execute("SELECT * FROM entries WHERE id = ? AND child_id = ?", (entry_id, child_id)).fetchone()
        if row is None:
            raise StoreError("Entry not found", 404)
        return row

    def _remove_file(self, filename: str):
        try:
            os.remove(os.path.join(self.photo_dir, filename))
        except FileNotFoundError:
            pass

    def _child(self, conn, child_id: int, viewer_id: str) -> dict:
        row = self._require_child(conn, child_id)
        link = conn.execute("SELECT person_id FROM child_people WHERE child_id = ? AND immich_user_id = ?",
                            (child_id, viewer_id)).fetchone()
        periods = conn.execute("SELECT * FROM periods WHERE child_id = ? ORDER BY number", (child_id,)).fetchall()
        entries = conn.execute("SELECT * FROM entries WHERE child_id = ? ORDER BY weekday, start_time, id",
                               (child_id,)).fetchall()
        return {
            "id": row["id"],
            "name": row["name"],
            "color": row["color"],
            "immich_user_id": row["immich_user_id"],
            "photo_url": PHOTO_URL_PREFIX + row["photo"] if row["photo"] else None,
            "my_person_id": link["person_id"] if link else None,
            "created_by": row["created_by"],
            "updated": row["updated"],
            "periods": [{"number": p["number"], "start_time": p["start_time"], "end_time": p["end_time"]}
                        for p in periods],
            "entries": [self._entry_json(e) for e in entries],
        }

    @staticmethod
    def _entry_json(row) -> dict:
        return {
            "id": row["id"],
            "kind": row["kind"],
            "weekday": row["weekday"],
            "start_time": row["start_time"],
            "end_time": row["end_time"],
            "title": row["title"],
            "period": row["period"],
            "place": row["place"],
            "teacher": row["teacher"],
            "note": row["note"],
        }
