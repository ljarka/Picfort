"""Shopping lists with checkable items, shared by the whole family (T-018, ADR 0008).

The `receipts` table is created here too, so receipts (T-020) don't change the schema
version, which would empty the lists.
"""

from contextlib import closing

from store import SqliteStore, StoreError

MAX_TITLE = 100
MAX_TEXT = 200
MAX_ITEMS = 300

SCHEMA = """
CREATE TABLE lists (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  title       TEXT    NOT NULL,
  archived    INTEGER NOT NULL DEFAULT 0,
  revision    INTEGER NOT NULL DEFAULT 1,
  created_by  TEXT    NOT NULL,
  created     TEXT    NOT NULL,
  updated     TEXT    NOT NULL
);
CREATE TABLE items (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  list_id     INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
  text        TEXT    NOT NULL,
  checked     INTEGER NOT NULL DEFAULT 0,
  position    REAL    NOT NULL,
  checked_by  TEXT,
  checked_at  TEXT,
  created_by  TEXT    NOT NULL
);
CREATE INDEX items_list ON items(list_id);
CREATE TABLE receipts (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  list_id     INTEGER REFERENCES lists(id) ON DELETE SET NULL,
  photo       TEXT,
  shop        TEXT,
  date        TEXT    NOT NULL,
  total_minor INTEGER,
  payer_id    TEXT    NOT NULL,
  status      TEXT    NOT NULL,
  read_error  TEXT,
  created_by  TEXT    NOT NULL,
  created     TEXT    NOT NULL,
  updated     TEXT    NOT NULL
);
CREATE INDEX receipts_date ON receipts(date);
"""

# Unchecked by position, then checked with the most recently checked first.
ITEM_ORDER = ("checked, CASE WHEN checked THEN 0 ELSE position END, "
              "CASE WHEN checked THEN checked_at END DESC, CASE WHEN checked THEN -id ELSE id END")


def _text(value, field: str, limit: int) -> str:
    if not isinstance(value, str) or not value.strip():
        raise StoreError(f"{field} must not be empty")
    value = value.strip()
    if len(value) > limit:
        raise StoreError(f"{field} must be at most {limit} characters")
    return value


def _bool(value, field: str) -> bool:
    if not isinstance(value, bool):
        raise StoreError(f"{field} must be true or false")
    return value


def _number(value, field: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise StoreError(f"{field} must be a number")
    return float(value)


class ShoppingStore(SqliteStore):
    SCHEMA_VERSION = 1
    SCHEMA = SCHEMA
    NAME = "Shopping"

    # Reading

    def lists(self, archived: bool = False) -> list:
        with closing(self.connect()) as conn:
            rows = conn.execute(
                "SELECT * FROM lists WHERE archived = ? ORDER BY created DESC, id DESC", (int(archived),)
            ).fetchall()
            return [self._list_json(conn, r) for r in rows]

    def get(self, list_id: int) -> dict:
        with closing(self.connect()) as conn:
            return self._list_json(conn, self._row(conn, list_id))

    # Lists

    def create(self, user_id: str, title, items=None) -> dict:
        title = _text(title, "title", MAX_TITLE)
        if items is None:
            items = []
        if not isinstance(items, list):
            raise StoreError("items must be a list of texts")
        texts = [_text(t, "item text", MAX_TEXT) for t in items]
        if len(texts) > MAX_ITEMS:
            raise StoreError(f"A list can have at most {MAX_ITEMS} items")
        now = self.clock()
        with closing(self.connect()) as conn, conn:
            list_id = conn.execute(
                "INSERT INTO lists (title, created_by, created, updated) VALUES (?, ?, ?, ?)",
                (title, user_id, now, now),
            ).lastrowid
            conn.executemany(
                "INSERT INTO items (list_id, text, position, created_by) VALUES (?, ?, ?, ?)",
                [(list_id, text, float(i + 1), user_id) for i, text in enumerate(texts)],
            )
        return self.get(list_id)

    def update(self, list_id: int, body: dict) -> dict:
        changes = {}
        if "title" in body:
            changes["title"] = _text(body["title"], "title", MAX_TITLE)
        if "archived" in body:
            changes["archived"] = int(_bool(body["archived"], "archived"))
        with closing(self.connect()) as conn, conn:
            self._row(conn, list_id)
            for column, value in changes.items():
                conn.execute(f"UPDATE lists SET {column} = ? WHERE id = ?", (value, list_id))
            if changes:
                self._touch(conn, list_id)
        return self.get(list_id)

    def delete(self, list_id: int, user_id: str):
        with closing(self.connect()) as conn, conn:
            row = self._row(conn, list_id)
            if row["created_by"] != user_id:
                raise StoreError("Only the person who created the list can delete it", 403)
            # Items go with it (ON DELETE CASCADE); receipts stay with list_id NULL.
            conn.execute("DELETE FROM lists WHERE id = ?", (list_id,))

    def uncheck_all(self, list_id: int) -> dict:
        with closing(self.connect()) as conn, conn:
            self._row(conn, list_id)
            conn.execute(
                "UPDATE items SET checked = 0, checked_by = NULL, checked_at = NULL WHERE list_id = ? AND checked",
                (list_id,),
            )
            self._touch(conn, list_id)
        return self.get(list_id)

    def delete_checked(self, list_id: int) -> dict:
        with closing(self.connect()) as conn, conn:
            self._row(conn, list_id)
            conn.execute("DELETE FROM items WHERE list_id = ? AND checked", (list_id,))
            self._touch(conn, list_id)
        return self.get(list_id)

    # Items

    def add_item(self, list_id: int, user_id: str, body: dict) -> dict:
        text = _text(body.get("text"), "text", MAX_TEXT)
        position = _number(body["position"], "position") if body.get("position") is not None else None
        with closing(self.connect()) as conn, conn:
            self._row(conn, list_id)
            count, last = conn.execute(
                "SELECT COUNT(*), MAX(position) FROM items WHERE list_id = ?", (list_id,)
            ).fetchone()
            if count >= MAX_ITEMS:
                raise StoreError(f"A list can have at most {MAX_ITEMS} items")
            if position is None:
                position = (last or 0.0) + 1.0
            item_id = conn.execute(
                "INSERT INTO items (list_id, text, position, created_by) VALUES (?, ?, ?, ?)",
                (list_id, text, position, user_id),
            ).lastrowid
            self._touch(conn, list_id)
            return self._item_json(self._item(conn, list_id, item_id))

    def update_item(self, list_id: int, item_id: int, user_id: str, body: dict) -> dict:
        changes = {}
        if "text" in body:
            changes["text"] = _text(body["text"], "text", MAX_TEXT)
        if "position" in body:
            changes["position"] = _number(body["position"], "position")
        if "checked" in body:
            checked = _bool(body["checked"], "checked")
            changes["checked"] = int(checked)
            changes["checked_by"] = user_id if checked else None
            changes["checked_at"] = self.clock() if checked else None
        with closing(self.connect()) as conn, conn:
            self._row(conn, list_id)
            self._item(conn, list_id, item_id)
            for column, value in changes.items():
                conn.execute(f"UPDATE items SET {column} = ? WHERE id = ?", (value, item_id))
            if changes:
                self._touch(conn, list_id)
            return self._item_json(self._item(conn, list_id, item_id))

    def delete_item(self, list_id: int, item_id: int):
        with closing(self.connect()) as conn, conn:
            self._row(conn, list_id)
            self._item(conn, list_id, item_id)
            conn.execute("DELETE FROM items WHERE id = ?", (item_id,))
            self._touch(conn, list_id)

    # Helpers

    def _touch(self, conn, list_id: int):
        """Every change to a list or its items: new revision (for the ETag) and time."""
        conn.execute("UPDATE lists SET revision = revision + 1, updated = ? WHERE id = ?", (self.clock(), list_id))

    @staticmethod
    def _row(conn, list_id: int):
        row = conn.execute("SELECT * FROM lists WHERE id = ?", (list_id,)).fetchone()
        if row is None:
            raise StoreError("List not found", 404)
        return row

    @staticmethod
    def _item(conn, list_id: int, item_id: int):
        row = conn.execute("SELECT * FROM items WHERE id = ? AND list_id = ?", (item_id, list_id)).fetchone()
        if row is None:
            raise StoreError("Item not found", 404)
        return row

    @staticmethod
    def _item_json(r) -> dict:
        return {
            "id": r["id"],
            "text": r["text"],
            "checked": bool(r["checked"]),
            "position": r["position"],
            "checked_by": r["checked_by"],
            "checked_at": r["checked_at"],
            "created_by": r["created_by"],
        }

    def _list_json(self, conn, r) -> dict:
        items = [self._item_json(i) for i in conn.execute(
            f"SELECT * FROM items WHERE list_id = ? ORDER BY {ITEM_ORDER}", (r["id"],)
        ).fetchall()]
        return {
            "id": r["id"],
            "title": r["title"],
            "archived": bool(r["archived"]),
            "revision": r["revision"],
            "created_by": r["created_by"],
            "created": r["created"],
            "updated": r["updated"],
            "item_count": len(items),
            "checked_count": sum(1 for i in items if i["checked"]),
            "items": items,
        }
