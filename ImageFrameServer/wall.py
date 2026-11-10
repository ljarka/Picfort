"""Family Wall: posts (text and/or a photo) stored in SQLite, photos as files (ADR 0002).

No migrations: when SCHEMA_VERSION changes, the wall is recreated empty.
"""

import logging
import os
import re
import sqlite3
import uuid
from contextlib import closing
from datetime import datetime, timezone
from io import BytesIO

from PIL import Image, UnidentifiedImageError

try:  # Optional: lets Pillow open HEIC/HEIF photos from iPhones.
    from pillow_heif import register_heif_opener

    register_heif_opener()
except ImportError:
    pass

log = logging.getLogger("wall")

SCHEMA_VERSION = 1
MAX_PHOTO_BYTES = 10 * 1024 * 1024
DEFAULT_LIMIT = 20
MAX_LIMIT = 100
PHOTO_URL_PREFIX = "/wall/photo/"
# Pillow format -> file extension. HEIF only opens when pillow-heif is installed.
PHOTO_FORMATS = {"JPEG": "jpg", "PNG": "png", "HEIF": "heic"}
PHOTO_NAME_RE = re.compile(r"^[0-9a-f]{32}\.(jpg|png|heic)$")
# EXIF orientations that rotate by 90°, so width and height swap when shown upright.
ROTATED_ORIENTATIONS = {5, 6, 7, 8}

SCHEMA = """
CREATE TABLE posts (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  author_id    TEXT    NOT NULL,
  author       TEXT    NOT NULL,
  text         TEXT,
  photo        TEXT,
  photo_width  INTEGER,
  photo_height INTEGER,
  pinned       INTEGER NOT NULL DEFAULT 0,
  created      TEXT    NOT NULL
)
"""


class WallError(Exception):
    def __init__(self, message: str, status: int):
        super().__init__(message)
        self.status = status


def _utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class WallStore:
    def __init__(self, db_path: str, photo_dir: str, clock=_utc_now):
        self.db_path = db_path
        self.photo_dir = photo_dir
        self.clock = clock
        os.makedirs(photo_dir, exist_ok=True)
        self._init_schema()

    def _connect(self) -> sqlite3.Connection:
        # One connection per call: Flask serves requests on several threads.
        conn = sqlite3.connect(self.db_path, timeout=10)
        conn.row_factory = sqlite3.Row
        return conn

    def _init_schema(self):
        with closing(self._connect()) as conn, conn:
            version = conn.execute("PRAGMA user_version").fetchone()[0]
            if version == SCHEMA_VERSION:
                return
            if version != 0 or conn.execute(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'posts'"
            ).fetchone():
                log.warning("Wall schema changed (%s -> %s): starting with an empty wall", version, SCHEMA_VERSION)
                conn.execute("DROP TABLE IF EXISTS posts")
                self._remove_all_photos()
            conn.execute(SCHEMA)
            conn.execute(f"PRAGMA user_version = {SCHEMA_VERSION}")

    def _remove_all_photos(self):
        for name in os.listdir(self.photo_dir):
            if PHOTO_NAME_RE.match(name):
                os.remove(os.path.join(self.photo_dir, name))

    # Reading

    def list(self, limit: int = DEFAULT_LIMIT) -> list:
        """Pinned posts first, then newest first."""
        with closing(self._connect()) as conn:
            rows = conn.execute(
                "SELECT * FROM posts ORDER BY pinned DESC, created DESC, id DESC LIMIT ?", (limit,)
            ).fetchall()
        return [self._json(r) for r in rows]

    def get(self, post_id: int):
        with closing(self._connect()) as conn:
            row = conn.execute("SELECT * FROM posts WHERE id = ?", (post_id,)).fetchone()
        return self._json(row) if row else None

    def photo_path(self, filename: str):
        """Path of a stored photo, or None for names that aren't ours or files that are gone."""
        if not PHOTO_NAME_RE.match(filename):
            return None
        path = os.path.join(self.photo_dir, filename)
        return path if os.path.isfile(path) else None

    # Writing

    def create(self, author_id: str, author: str, text, photo: bytes = None) -> dict:
        author_id = (author_id or "").strip()
        author = (author or "").strip()
        text = (text or "").strip() or None
        if not author_id or not author:
            raise WallError("author_id and author are required", 400)
        if text is None and not photo:
            raise WallError("A post needs text or a photo", 400)

        filename = width = height = None
        if photo:
            ext, width, height = self._inspect_photo(photo)
            filename = f"{uuid.uuid4().hex}.{ext}"
            with open(os.path.join(self.photo_dir, filename), "wb") as f:
                f.write(photo)
        try:
            with closing(self._connect()) as conn, conn:
                cur = conn.execute(
                    "INSERT INTO posts (author_id, author, text, photo, photo_width, photo_height, created)"
                    " VALUES (?, ?, ?, ?, ?, ?, ?)",
                    (author_id, author, text, filename, width, height, self.clock()),
                )
                post_id = cur.lastrowid
        except Exception:
            if filename:
                os.remove(os.path.join(self.photo_dir, filename))
            raise
        return self.get(post_id)

    def delete(self, post_id: int) -> bool:
        with closing(self._connect()) as conn, conn:
            row = conn.execute("SELECT photo FROM posts WHERE id = ?", (post_id,)).fetchone()
            if row is None:
                return False
            conn.execute("DELETE FROM posts WHERE id = ?", (post_id,))
        if row["photo"]:
            try:
                os.remove(os.path.join(self.photo_dir, row["photo"]))
            except FileNotFoundError:
                pass
        return True

    def toggle_pin(self, post_id: int):
        with closing(self._connect()) as conn, conn:
            cur = conn.execute("UPDATE posts SET pinned = 1 - pinned WHERE id = ?", (post_id,))
            if cur.rowcount == 0:
                return None
        return self.get(post_id)

    # Helpers

    @staticmethod
    def _inspect_photo(data: bytes):
        """(extension, upright width, upright height); raises WallError for anything that isn't a photo we keep."""
        if len(data) > MAX_PHOTO_BYTES:
            raise WallError("The photo is larger than 10 MB", 413)
        try:
            with Image.open(BytesIO(data)) as img:
                fmt = img.format
                width, height = img.size
                orientation = img.getexif().get(0x0112)
        except (UnidentifiedImageError, OSError):
            raise WallError("The photo must be JPEG, PNG or HEIC", 415)
        if fmt not in PHOTO_FORMATS:
            raise WallError("The photo must be JPEG, PNG or HEIC", 415)
        if orientation in ROTATED_ORIENTATIONS:
            width, height = height, width
        return PHOTO_FORMATS[fmt], width, height

    @staticmethod
    def _json(row) -> dict:
        return {
            "id": row["id"],
            "author_id": row["author_id"],
            "author": row["author"],
            "text": row["text"],
            "photo_url": PHOTO_URL_PREFIX + row["photo"] if row["photo"] else None,
            "photo_width": row["photo_width"],
            "photo_height": row["photo_height"],
            "pinned": bool(row["pinned"]),
            "created": row["created"],
        }
