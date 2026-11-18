"""Family Wall: posts (text and/or a photo) stored in SQLite (`wall.db`), photos as files (ADR 0002).

Schema changes use `SqliteStore` migrations (ADR 0016) so wall posts survive updates.

Reactions (T-084): anyone reacts to any post with one of `REACTIONS`, once per emoji. The first
reaction by someone other than the author is remembered on the post (`first_reaction_*`, never
reset) so the author's phone can notify about it once (`first_reactions`).
"""

import os
import uuid
from contextlib import closing

from photos import MAX_PHOTO_BYTES, PHOTO_FORMATS, PHOTO_NAME_RE, inspect_photo
from store import SqliteStore, StoreError, utc_now

DEFAULT_LIMIT = 20
MAX_LIMIT = 100
PHOTO_URL_PREFIX = "/wall/photo/"
# Slack-like quick reactions; the app shows them in this order.
REACTIONS = ("\u2764\ufe0f", "\U0001f44d", "\U0001f602", "\U0001f62e", "\U0001f622", "\U0001f389")
FIRST_REACTIONS_LIMIT = 50

REACTIONS_TABLE = """
CREATE TABLE reactions (
  post_id   INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  user_id   TEXT    NOT NULL,
  user_name TEXT    NOT NULL,
  emoji     TEXT    NOT NULL,
  created   TEXT    NOT NULL,
  PRIMARY KEY (post_id, user_id, emoji)
)
"""

WallError = StoreError


class WallStore(SqliteStore):
    SCHEMA_VERSION = 2
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
      created      TEXT    NOT NULL,
      first_reaction_at    TEXT,
      first_reaction_by    TEXT,
      first_reaction_emoji TEXT
    );
    """ + REACTIONS_TABLE + ";"
    MIGRATIONS = {
        1: [
            "ALTER TABLE posts ADD COLUMN first_reaction_at TEXT",
            "ALTER TABLE posts ADD COLUMN first_reaction_by TEXT",
            "ALTER TABLE posts ADD COLUMN first_reaction_emoji TEXT",
            REACTIONS_TABLE,
        ],
    }

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
            reactions = self._reactions(conn, [r["id"] for r in rows])
        return [self._json(r, reactions.get(r["id"], [])) for r in rows]

    def get(self, post_id: int):
        with closing(self.connect()) as conn:
            row = conn.execute("SELECT * FROM posts WHERE id = ?", (post_id,)).fetchone()
            if row is None:
                return None
            reactions = self._reactions(conn, [post_id])
        return self._json(row, reactions.get(post_id, []))

    def first_reactions(self, author_id: str, since: str) -> list:
        """The caller's posts whose first reaction (by someone else) came after `since`, oldest first."""
        if not isinstance(since, str) or not since.strip():
            raise WallError("since is required (ISO time, e.g. 2026-11-18T12:00:00Z)", 400)
        with closing(self.connect()) as conn:
            rows = conn.execute(
                "SELECT id, text, photo, first_reaction_at, first_reaction_by, first_reaction_emoji FROM posts"
                " WHERE author_id = ? AND first_reaction_at > ? ORDER BY first_reaction_at, id LIMIT ?",
                (author_id, since.strip(), FIRST_REACTIONS_LIMIT),
            ).fetchall()
        return [
            {
                "post_id": r["id"],
                "text": r["text"],
                "has_photo": r["photo"] is not None,
                "at": r["first_reaction_at"],
                "by": r["first_reaction_by"],
                "emoji": r["first_reaction_emoji"],
            }
            for r in rows
        ]

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

    def react(self, post_id: int, user_id: str, user_name: str, emoji) -> dict | None:
        """Adds the caller's [emoji] (idempotent); None when the post doesn't exist."""
        emoji = self._emoji(emoji)
        now = self.clock()
        with closing(self.connect()) as conn, conn:
            post = conn.execute("SELECT author_id, first_reaction_at FROM posts WHERE id = ?", (post_id,)).fetchone()
            if post is None:
                return None
            conn.execute(
                "INSERT OR IGNORE INTO reactions (post_id, user_id, user_name, emoji, created) VALUES (?, ?, ?, ?, ?)",
                (post_id, user_id, user_name, emoji, now),
            )
            if post["first_reaction_at"] is None and post["author_id"] != user_id:
                conn.execute(
                    "UPDATE posts SET first_reaction_at = ?, first_reaction_by = ?, first_reaction_emoji = ?"
                    " WHERE id = ?",
                    (now, user_name, emoji, post_id),
                )
        return self.get(post_id)

    def unreact(self, post_id: int, user_id: str, emoji) -> dict | None:
        """Removes the caller's [emoji]; the first-reaction record stays (one notification per post)."""
        emoji = self._emoji(emoji)
        with closing(self.connect()) as conn, conn:
            if conn.execute("SELECT 1 FROM posts WHERE id = ?", (post_id,)).fetchone() is None:
                return None
            conn.execute(
                "DELETE FROM reactions WHERE post_id = ? AND user_id = ? AND emoji = ?", (post_id, user_id, emoji)
            )
        return self.get(post_id)

    def toggle_pin(self, post_id: int):
        with closing(self.connect()) as conn, conn:
            cur = conn.execute("UPDATE posts SET pinned = 1 - pinned WHERE id = ?", (post_id,))
            if cur.rowcount == 0:
                return None
        return self.get(post_id)

    # Helpers

    @staticmethod
    def _emoji(value) -> str:
        if value not in REACTIONS:
            raise WallError("emoji must be one of " + " ".join(REACTIONS), 400)
        return value

    @staticmethod
    def _reactions(conn, post_ids: list) -> dict:
        """{post id: [{emoji, count, user_ids, names}]}, emojis in `REACTIONS` order, people by time."""
        if not post_ids:
            return {}
        marks = ",".join("?" * len(post_ids))
        rows = conn.execute(
            f"SELECT post_id, user_id, user_name, emoji FROM reactions WHERE post_id IN ({marks})"
            " ORDER BY created, user_id",
            post_ids,
        ).fetchall()
        grouped: dict = {}
        for r in rows:
            entry = grouped.setdefault(r["post_id"], {}).setdefault(
                r["emoji"], {"emoji": r["emoji"], "count": 0, "user_ids": [], "names": []}
            )
            entry["count"] += 1
            entry["user_ids"].append(r["user_id"])
            entry["names"].append(r["user_name"])
        return {
            post_id: [by_emoji[e] for e in REACTIONS if e in by_emoji]
            for post_id, by_emoji in grouped.items()
        }

    @staticmethod
    def _inspect_photo(data: bytes):
        return inspect_photo(data, error_cls=WallError)

    @staticmethod
    def _json(row, reactions=()) -> dict:
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
            "reactions": list(reactions),
        }
