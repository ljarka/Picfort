"""Family members: shared display name, photo, hidden flag and birth date per Immich user, plus manual
members who aren't Immich users (T-031, T-033, ADR 0008, ADR 0010)."""

import os
import re
import uuid
from contextlib import closing
from datetime import date, timedelta
from io import BytesIO

from PIL import Image, UnidentifiedImageError

from store import SqliteStore, StoreError, utc_now

MAX_NAME = 50
MAX_PHOTO_BYTES = 2 * 1024 * 1024
PHOTO_URL_PREFIX = "/family/photo/"
PHOTO_FORMATS = {"JPEG": "jpg", "PNG": "png"}
PHOTO_NAME_RE = re.compile(r"^[0-9a-f]{32}\.(jpg|png)$")
UUID_RE = re.compile(r"^[0-9a-fA-F-]{36}$")
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
BIRTH_DATE_RE = re.compile(r"^(\d{4})-(\d{2})-(\d{2})$")
BIRTH_DAY_RE = re.compile(r"^--(\d{2})-(\d{2})$")
MIN_BIRTH_YEAR = 1900

SCHEMA = """
CREATE TABLE profiles (
  user_id       TEXT PRIMARY KEY,
  display_name  TEXT,
  photo         TEXT,
  hidden        INTEGER,
  manual        INTEGER NOT NULL DEFAULT 0,
  birth_date    TEXT,
  no_photo      INTEGER,
  updated_by    TEXT NOT NULL,
  updated       TEXT NOT NULL
);
-- Who a member is in each viewer's Immich people (person ids are per Immich library), T-052.
CREATE TABLE member_people (
  member_id  TEXT NOT NULL,
  viewer_id  TEXT NOT NULL,
  person_id  TEXT NOT NULL,
  updated    TEXT NOT NULL,
  PRIMARY KEY (member_id, viewer_id)
);
"""


def _user_id(value) -> str:
    if not isinstance(value, str) or not UUID_RE.match(value):
        raise StoreError("user_id must be a UUID")
    return value


def _display_name(value):
    if value is None:
        return None
    if not isinstance(value, str) or not value.strip():
        raise StoreError("display_name must not be empty")
    value = value.strip()
    if len(value) > MAX_NAME:
        raise StoreError(f"display_name must be at most {MAX_NAME} characters")
    return value


def _no_photo(value):
    """True: show initials even when Immich has a profile picture. Stored as 1 or NULL."""
    if value is None or value is False:
        return None
    if value is True:
        return 1
    raise StoreError("no_photo must be true, false or null")


def _hidden(value):
    if value is None:
        return None
    if not isinstance(value, bool):
        raise StoreError("hidden must be true, false or null")
    return int(value)


def _birth_date(value, today: date):
    """`YYYY-MM-DD` (a real date, 1900 up to today) or `--MM-DD` (year unknown; 29 Feb is fine)."""
    if value is None:
        return None
    if not isinstance(value, str):
        raise StoreError("birth_date must be YYYY-MM-DD, --MM-DD or null")
    full = BIRTH_DATE_RE.match(value)
    if full:
        try:
            parsed = date(int(full.group(1)), int(full.group(2)), int(full.group(3)))
        except ValueError:
            raise StoreError("birth_date is not a real date")
        if parsed.year < MIN_BIRTH_YEAR:
            raise StoreError(f"birth_date must not be before {MIN_BIRTH_YEAR}")
        # A day of slack: the phone's date can be ahead of the server's (UTC).
        if parsed > today + timedelta(days=1):
            raise StoreError("birth_date must not be in the future")
        return value
    day_only = BIRTH_DAY_RE.match(value)
    if day_only:
        try:
            date(2000, int(day_only.group(1)), int(day_only.group(2)))  # 2000 is a leap year
        except ValueError:
            raise StoreError("birth_date is not a real date")
        return value
    raise StoreError("birth_date must be YYYY-MM-DD, --MM-DD or null")


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


def _parse_today(today_arg, body: dict = None) -> str | None:
    val = today_arg if today_arg is not None else (body.get("today") if body else None)
    if val is None:
        return None
    if not isinstance(val, str) or not DATE_RE.match(val):
        raise StoreError("today must be a YYYY-MM-DD date")
    try:
        date.fromisoformat(val)
    except ValueError:
        raise StoreError("today is not a valid calendar date")
    return val


class FamilyStore(SqliteStore):
    SCHEMA_VERSION = 4
    SCHEMA = SCHEMA
    MIGRATIONS = {
        1: [
            "ALTER TABLE profiles ADD COLUMN manual INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE profiles ADD COLUMN birth_date TEXT",
        ],
        2: [
            "ALTER TABLE profiles ADD COLUMN no_photo INTEGER",
        ],
        3: [
            "CREATE TABLE member_people (member_id TEXT NOT NULL, viewer_id TEXT NOT NULL,"
            " person_id TEXT NOT NULL, updated TEXT NOT NULL, PRIMARY KEY (member_id, viewer_id))",
        ],
    }
    NAME = "Family"

    def __init__(self, db_path: str, photo_dir: str, clock=utc_now, events=None):
        self.photo_dir = photo_dir
        self.events = events
        os.makedirs(photo_dir, exist_ok=True)
        super().__init__(db_path, clock=clock)

    def get_birth_date(self, user_id: str) -> str | None:
        with closing(self.connect()) as conn:
            row = conn.execute("SELECT birth_date FROM profiles WHERE user_id = ?", (user_id,)).fetchone()
            return row["birth_date"] if row else None

    def profiles(self) -> list:
        with closing(self.connect()) as conn:
            rows = conn.execute("SELECT * FROM profiles ORDER BY user_id").fetchall()
        return [self._json(r) for r in rows]

    def _today(self) -> date:
        return date.fromisoformat(self.clock()[:10])

    def add_member(self, actor_id: str, body: dict, today: str = None) -> dict:
        """A member who isn't an Immich user: the server makes the id."""
        today_str = _parse_today(today, body)
        now = self.clock()
        ref_today = date.fromisoformat(today_str) if today_str else date.fromisoformat(now[:10])
        display_name = _display_name(body.get("display_name"))
        if display_name is None:
            raise StoreError("display_name is required")
        birth_date = _birth_date(body.get("birth_date"), ref_today)
        user_id = str(uuid.uuid4())
        with closing(self.connect()) as conn, conn:
            conn.execute(
                "INSERT INTO profiles (user_id, display_name, manual, birth_date, updated_by, updated)"
                " VALUES (?, ?, 1, ?, ?, ?)",
                (user_id, display_name, birth_date, actor_id, now),
            )
            created = self._json(conn.execute("SELECT * FROM profiles WHERE user_id = ?", (user_id,)).fetchone())
        if self.events is not None and birth_date is not None:
            self.events.sync_birthday(user_id, display_name, birth_date, actor_id, today=today_str)
        return created

    def update(self, user_id: str, actor_id: str, body: dict, today: str = None) -> dict:
        user_id = _user_id(user_id)
        today_str = _parse_today(today, body)
        now = self.clock()
        changes = {}
        if "display_name" in body:
            changes["display_name"] = _display_name(body["display_name"])
        if "hidden" in body:
            changes["hidden"] = _hidden(body["hidden"])
        if "birth_date" in body:
            ref_today = date.fromisoformat(today_str) if today_str else date.fromisoformat(now[:10])
            changes["birth_date"] = _birth_date(body["birth_date"], ref_today)
        if "no_photo" in body:
            changes["no_photo"] = _no_photo(body["no_photo"])
        deleted = False
        with closing(self.connect()) as conn, conn:
            row = conn.execute("SELECT * FROM profiles WHERE user_id = ?", (user_id,)).fetchone()
            manual = bool(row["manual"]) if row else False
            current = {k: (row[k] if row else None) for k in ("display_name", "hidden", "birth_date", "no_photo")}
            current.update(changes)
            photo = row["photo"] if row else None
            if manual and current["display_name"] is None:
                raise StoreError("display_name is required for a member who isn't an Immich user")
            if not manual and photo is None and all(v is None for v in current.values()):
                if row is not None:
                    conn.execute("DELETE FROM profiles WHERE user_id = ?", (user_id,))
                deleted = True
                res = {
                    "user_id": user_id,
                    "display_name": None,
                    "photo_url": None,
                    "hidden": None,
                    "manual": False,
                    "birth_date": None,
                    "no_photo": False,
                    "updated_by": actor_id,
                    "updated": now,
                }
            elif row is None:
                conn.execute(
                    "INSERT INTO profiles (user_id, display_name, photo, hidden, birth_date, no_photo, updated_by,"
                    " updated) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    (user_id, current["display_name"], photo, current["hidden"], current["birth_date"],
                     current["no_photo"], actor_id, now),
                )
                res = self._json(conn.execute("SELECT * FROM profiles WHERE user_id = ?", (user_id,)).fetchone())
            else:
                if changes:
                    conn.execute(
                        "UPDATE profiles SET display_name = ?, hidden = ?, birth_date = ?, no_photo = ?, updated_by = ?,"
                        " updated = ? WHERE user_id = ?",
                        (current["display_name"], current["hidden"], current["birth_date"], current["no_photo"],
                         actor_id, now, user_id),
                    )
                res = self._json(conn.execute("SELECT * FROM profiles WHERE user_id = ?", (user_id,)).fetchone())

        if self.events is not None:
            if deleted or current["birth_date"] is None:
                self.events.delete_birthday(user_id)
            elif "birth_date" in body or "display_name" in body:
                self.events.sync_birthday(user_id, current["display_name"], current["birth_date"], actor_id, today=today_str)
        return res

    def delete_member(self, user_id: str):
        """Only manual members are deleted (with their photo); an Immich user's row is cleared via PATCH."""
        user_id = _user_id(user_id)
        with closing(self.connect()) as conn, conn:
            row = conn.execute("SELECT * FROM profiles WHERE user_id = ?", (user_id,)).fetchone()
            if row is None:
                raise StoreError("Member not found", 404)
            if not row["manual"]:
                raise StoreError("Only members who aren't Immich users can be deleted; clear the fields instead", 409)
            conn.execute("DELETE FROM profiles WHERE user_id = ?", (user_id,))
            conn.execute("DELETE FROM member_people WHERE member_id = ?", (user_id,))
        if row["photo"]:
            self._remove_file(row["photo"])
        if self.events is not None:
            self.events.delete_birthday(user_id)

    def set_photo(self, user_id: str, actor_id: str, data: bytes) -> dict:
        user_id = _user_id(user_id)
        ext = _inspect_photo(data)
        filename = f"{uuid.uuid4().hex}.{ext}"
        new_path = os.path.join(self.photo_dir, filename)
        with open(new_path, "wb") as f:
            f.write(data)
        now = self.clock()
        old_photo = None
        try:
            with closing(self.connect()) as conn, conn:
                row = conn.execute("SELECT * FROM profiles WHERE user_id = ?", (user_id,)).fetchone()
                if row is None:
                    conn.execute(
                        "INSERT INTO profiles (user_id, photo, updated_by, updated) VALUES (?, ?, ?, ?)",
                        (user_id, filename, actor_id, now),
                    )
                else:
                    old_photo = row["photo"]
                    conn.execute(
                        "UPDATE profiles SET photo = ?, no_photo = NULL, updated_by = ?, updated = ? WHERE user_id = ?",
                        (filename, actor_id, now, user_id),
                    )
                saved = self._json(conn.execute("SELECT * FROM profiles WHERE user_id = ?", (user_id,)).fetchone())
        except Exception:
            self._remove_file(filename)
            raise
        if old_photo:
            self._remove_file(old_photo)
        return saved

    def delete_photo(self, user_id: str, actor_id: str):
        user_id = _user_id(user_id)
        now = self.clock()
        old_photo = None
        with closing(self.connect()) as conn, conn:
            row = conn.execute("SELECT * FROM profiles WHERE user_id = ?", (user_id,)).fetchone()
            if row is None or row["photo"] is None:
                return
            old_photo = row["photo"]
            if (not row["manual"] and row["display_name"] is None and row["hidden"] is None
                    and row["birth_date"] is None and row["no_photo"] is None):
                conn.execute("DELETE FROM profiles WHERE user_id = ?", (user_id,))
            else:
                conn.execute(
                    "UPDATE profiles SET photo = NULL, updated_by = ?, updated = ? WHERE user_id = ?",
                    (actor_id, now, user_id),
                )
        if old_photo:
            self._remove_file(old_photo)

    # --- T-052: each viewer's link from a member to a person in their own Immich library ---

    def my_people(self, viewer_id: str) -> dict:
        """{member_id: person_id} for this viewer only (person ids mean nothing in other libraries)."""
        with closing(self.connect()) as conn:
            rows = conn.execute(
                "SELECT member_id, person_id FROM member_people WHERE viewer_id = ? ORDER BY member_id", (viewer_id,)
            ).fetchall()
        return {r["member_id"]: r["person_id"] for r in rows}

    def link_person(self, member_id: str, viewer_id: str, body: dict) -> dict:
        member_id = _user_id(member_id)
        person_id = body.get("person_id")
        if not isinstance(person_id, str) or not UUID_RE.match(person_id):
            raise StoreError("person_id must be a UUID")
        with closing(self.connect()) as conn, conn:
            conn.execute(
                "INSERT INTO member_people (member_id, viewer_id, person_id, updated) VALUES (?, ?, ?, ?)"
                " ON CONFLICT (member_id, viewer_id) DO UPDATE SET person_id = excluded.person_id,"
                " updated = excluded.updated",
                (member_id, viewer_id, person_id, self.clock()),
            )
        return {"member_id": member_id, "person_id": person_id}

    def unlink_person(self, member_id: str, viewer_id: str):
        member_id = _user_id(member_id)
        with closing(self.connect()) as conn, conn:
            conn.execute("DELETE FROM member_people WHERE member_id = ? AND viewer_id = ?", (member_id, viewer_id))

    def photo_path(self, filename: str):
        if not PHOTO_NAME_RE.match(filename):
            return None
        path = os.path.join(self.photo_dir, filename)
        return path if os.path.isfile(path) else None

    def _remove_file(self, filename: str):
        try:
            os.remove(os.path.join(self.photo_dir, filename))
        except FileNotFoundError:
            pass

    @staticmethod
    def _json(row) -> dict:
        return {
            "user_id": row["user_id"],
            "display_name": row["display_name"],
            "photo_url": PHOTO_URL_PREFIX + row["photo"] if row["photo"] else None,
            "hidden": None if row["hidden"] is None else bool(row["hidden"]),
            "manual": bool(row["manual"]),
            "birth_date": row["birth_date"],
            "no_photo": bool(row["no_photo"]),
            "updated_by": row["updated_by"],
            "updated": row["updated"],
        }
