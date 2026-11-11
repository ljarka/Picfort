"""Family profiles: shared display name, photo and hidden flag per Immich user (T-031, ADR 0008)."""

import os
import re
import uuid
from contextlib import closing
from io import BytesIO

from PIL import Image, UnidentifiedImageError

from store import SqliteStore, StoreError, utc_now

MAX_NAME = 50
MAX_PHOTO_BYTES = 2 * 1024 * 1024
PHOTO_URL_PREFIX = "/family/photo/"
PHOTO_FORMATS = {"JPEG": "jpg", "PNG": "png"}
PHOTO_NAME_RE = re.compile(r"^[0-9a-f]{32}\.(jpg|png)$")
UUID_RE = re.compile(r"^[0-9a-fA-F-]{36}$")

SCHEMA = """
CREATE TABLE profiles (
  user_id       TEXT PRIMARY KEY,
  display_name  TEXT,
  photo         TEXT,
  hidden        INTEGER,
  updated_by    TEXT NOT NULL,
  updated       TEXT NOT NULL
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


def _hidden(value):
    if value is None:
        return None
    if not isinstance(value, bool):
        raise StoreError("hidden must be true, false or null")
    return int(value)


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


class FamilyStore(SqliteStore):
    SCHEMA_VERSION = 1
    SCHEMA = SCHEMA
    NAME = "Family"

    def __init__(self, db_path: str, photo_dir: str, clock=utc_now):
        self.photo_dir = photo_dir
        os.makedirs(photo_dir, exist_ok=True)
        super().__init__(db_path, clock=clock)

    def profiles(self) -> list:
        with closing(self.connect()) as conn:
            rows = conn.execute("SELECT * FROM profiles ORDER BY user_id").fetchall()
        return [self._json(r) for r in rows]

    def update(self, user_id: str, actor_id: str, body: dict) -> dict:
        user_id = _user_id(user_id)
        changes = {}
        if "display_name" in body:
            changes["display_name"] = _display_name(body["display_name"])
        if "hidden" in body:
            changes["hidden"] = _hidden(body["hidden"])
        now = self.clock()
        with closing(self.connect()) as conn, conn:
            row = conn.execute("SELECT * FROM profiles WHERE user_id = ?", (user_id,)).fetchone()
            display_name = changes.get("display_name", row["display_name"] if row else None)
            photo = row["photo"] if row else None
            hidden = changes.get("hidden", row["hidden"] if row else None)
            if display_name is None and photo is None and hidden is None:
                if row is not None:
                    conn.execute("DELETE FROM profiles WHERE user_id = ?", (user_id,))
                return {
                    "user_id": user_id,
                    "display_name": None,
                    "photo_url": None,
                    "hidden": None,
                    "updated_by": actor_id,
                    "updated": now,
                }
            if row is None:
                conn.execute(
                    "INSERT INTO profiles (user_id, display_name, photo, hidden, updated_by, updated)"
                    " VALUES (?, ?, ?, ?, ?, ?)",
                    (user_id, display_name, photo, hidden, actor_id, now),
                )
            elif changes:
                conn.execute(
                    "UPDATE profiles SET display_name = ?, hidden = ?, updated_by = ?, updated = ?"
                    " WHERE user_id = ?",
                    (display_name, hidden, actor_id, now, user_id),
                )
            return self._json(conn.execute("SELECT * FROM profiles WHERE user_id = ?", (user_id,)).fetchone())

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
                        "UPDATE profiles SET photo = ?, updated_by = ?, updated = ? WHERE user_id = ?",
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
            if row["display_name"] is None and row["hidden"] is None:
                conn.execute("DELETE FROM profiles WHERE user_id = ?", (user_id,))
            else:
                conn.execute(
                    "UPDATE profiles SET photo = NULL, updated_by = ?, updated = ? WHERE user_id = ?",
                    (actor_id, now, user_id),
                )
        if old_photo:
            self._remove_file(old_photo)

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
            "updated_by": row["updated_by"],
            "updated": row["updated"],
        }
