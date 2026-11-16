"""Family Wall: posts (text and/or a photo) stored in SQLite (`wall.db`), photos as files (ADR 0002).

Schema changes use `SqliteStore` migrations (ADR 0016) so wall posts survive updates.
"""

import os
import uuid
from contextlib import closing

from photos import MAX_PHOTO_BYTES, PHOTO_FORMATS, PHOTO_NAME_RE, inspect_photo
from store import SqliteStore, StoreError, utc_now

DEFAULT_LIMIT = 20
MAX_LIMIT = 100
PHOTO_URL_PREFIX = "/wall/photo/"

WallError = StoreError


class WallStore(SqliteStore):
    SCHEMA_VERSION = 1
    NAME = "wall"
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
    );
    """

    def __init__(self, db_path: str, photo_dir: str, clock=utc_now):
        self.photo_dir = photo_dir
        os.makedirs(photo_dir, exist_ok=True)
        super().__init__(db_path, clock=clock)

    # Reading

    def list(self, limit: int = DEFAULT_LIMIT) -> list:
        """Pinned posts first, then newest first."""
        with closing(self.connect()) as conn:
            rows = conn.execute(
                "SELECT * FROM posts ORDER BY pinned DESC, created DESC, id DESC LIMIT ?", (limit,)
            ).fetchall()
        return [self._json(r) for r in rows]

    def get(self, post_id: int):
        with closing(self.connect()) as conn:
            row = conn.execute("SELECT * FROM posts WHERE id = ?", (post_id,)).fetchone()
        return self._json(row) if row else None

    def photo_path(self, filename: str):
        """Path of a stored photo, or None for names that aren't ours or files that are gone."""
        self.require_ready()
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
            with closing(self.connect()) as conn, conn:
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
        with closing(self.connect()) as conn, conn:
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
        with closing(self.connect()) as conn, conn:
            cur = conn.execute("UPDATE posts SET pinned = 1 - pinned WHERE id = ?", (post_id,))
            if cur.rowcount == 0:
                return None
        return self.get(post_id)

    # Helpers

    @staticmethod
    def _inspect_photo(data: bytes):
        return inspect_photo(data, error_cls=WallError)

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
