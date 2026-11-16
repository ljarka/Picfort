"""Shopping lists with checkable items, shared by the whole family (T-018, ADR 0008).

The `receipts` table is created here too, so receipts (T-020) don't change the schema
version, which would empty the lists.
"""

from contextlib import closing
from datetime import date as dt_date
import os
import re
import uuid

from photos import PHOTO_NAME_RE, inspect_photo
from store import SqliteStore, StoreError, utc_now
from wishlist_preview import fetch_link_preview, validate_http_url

MAX_TITLE = 100
MAX_TEXT = 200
MAX_ITEMS = 300
MAX_SHOP = 100
MAX_TOTAL_MINOR = 100_000_000
MAX_RECEIPTS_LIMIT = 200
MAX_SPENDING_MONTHS = 24
RECEIPT_PHOTO_PREFIX = "/receipts/photo/"
DATE_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")
MONTH_RE = re.compile(r"^\d{4}-\d{2}$")

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
CREATE TABLE wishlist_items (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  url              TEXT    NOT NULL,
  title            TEXT    NOT NULL,
  image_url        TEXT,
  price_minor      INTEGER,
  currency         TEXT    NOT NULL DEFAULT 'PLN',
  event_id         INTEGER,
  target_member_id TEXT,
  reserved_by      TEXT,
  bought           INTEGER NOT NULL DEFAULT 0,
  created_by       TEXT    NOT NULL,
  created          TEXT    NOT NULL,
  updated          TEXT    NOT NULL
);
CREATE INDEX wishlist_event ON wishlist_items(event_id);
"""

WISHLIST_V2_STATEMENTS = [
    """
    CREATE TABLE wishlist_items (
      id               INTEGER PRIMARY KEY AUTOINCREMENT,
      url              TEXT    NOT NULL,
      title            TEXT    NOT NULL,
      image_url        TEXT,
      price_minor      INTEGER,
      currency         TEXT    NOT NULL DEFAULT 'PLN',
      event_id         INTEGER,
      target_member_id TEXT,
      reserved_by      TEXT,
      bought           INTEGER NOT NULL DEFAULT 0,
      created_by       TEXT    NOT NULL,
      created          TEXT    NOT NULL,
      updated          TEXT    NOT NULL
    )
    """,
    "CREATE INDEX wishlist_event ON wishlist_items(event_id)",
]

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


def _receipt_date(value) -> str:
    if not isinstance(value, str) or not DATE_RE.match(value.strip()):
        raise StoreError("date must be YYYY-MM-DD")
    value = value.strip()
    try:
        dt_date.fromisoformat(value)
    except ValueError:
        raise StoreError("date must be a real YYYY-MM-DD date")
    return value


def _month_ym(value, field: str) -> tuple[int, int]:
    if not isinstance(value, str) or not MONTH_RE.match(value.strip()):
        raise StoreError(f"{field} must be YYYY-MM")
    year, month = (int(part) for part in value.strip().split("-"))
    if not 1 <= month <= 12:
        raise StoreError(f"{field} must be YYYY-MM")
    return year, month


def _total_minor(value):
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, int):
        raise StoreError("total_minor must be an integer")
    if not 1 <= value <= MAX_TOTAL_MINOR:
        raise StoreError(f"total_minor must be between 1 and {MAX_TOTAL_MINOR}")
    return value


def _shop(value):
    if value is None:
        return None
    if not isinstance(value, str):
        raise StoreError("shop must be text")
    value = value.strip()
    if len(value) > MAX_SHOP:
        raise StoreError(f"shop must be at most {MAX_SHOP} characters")
    return value or None


def _payer_id(value) -> str:
    if not isinstance(value, str) or not value.strip():
        raise StoreError("payer_id must not be empty")
    return value.strip()


def _optional_int(value, field: str):
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise StoreError(f"{field} must be a positive integer")
    return value


def _optional_id(value, field: str):
    if value is None:
        return None
    if not isinstance(value, str):
        raise StoreError(f"{field} must be text")
    value = value.strip()
    return value or None


class ShoppingStore(SqliteStore):
    SCHEMA_VERSION = 2
    SCHEMA = SCHEMA
    MIGRATIONS = {1: WISHLIST_V2_STATEMENTS}
    NAME = "Shopping"

    def __init__(
        self,
        db_path: str,
        clock=utc_now,
        receipts_dir: str = None,
        currency: str = "PLN",
        link_preview=fetch_link_preview,
    ):
        super().__init__(db_path, clock=clock)
        self.receipts_dir = receipts_dir or os.path.join(os.path.dirname(os.path.abspath(db_path)), "receipts")
        self.currency = (currency or "PLN").strip() or "PLN"
        self.link_preview = link_preview
        os.makedirs(self.receipts_dir, exist_ok=True)

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

    # Receipts (T-020)

    def receipts(self, month: str = None, list_id: int = None) -> list:
        clauses = []
        params = []
        if month is not None:
            year, mon = _month_ym(month, "month")
            clauses.append("date LIKE ?")
            params.append(f"{year:04d}-{mon:02d}-%")
        if list_id is not None:
            clauses.append("list_id = ?")
            params.append(list_id)
        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        limit = "" if month is not None else f"LIMIT {MAX_RECEIPTS_LIMIT}"
        with closing(self.connect()) as conn:
            rows = conn.execute(
                f"SELECT * FROM receipts {where} ORDER BY date DESC, id DESC {limit}",
                params,
            ).fetchall()
            return [self._receipt_json(r) for r in rows]

    def get_receipt(self, receipt_id: int) -> dict:
        with closing(self.connect()) as conn:
            return self._receipt_json(self._receipt_row(conn, receipt_id))

    def create_receipt(self, user_id: str, photo: bytes, date, list_id=None,
                       payer_id=None, shop=None, total_minor=None) -> dict:
        if not photo:
            raise StoreError("photo is required")
        date_str = _receipt_date(date)
        payer = _payer_id(user_id if payer_id is None else payer_id)
        shop_val = _shop(shop)
        total_val = _total_minor(total_minor)
        status = "confirmed" if total_val is not None else "manual"
        ext, _, _ = inspect_photo(photo, error_cls=StoreError)
        filename = f"{uuid.uuid4().hex}.{ext}"
        photo_path = os.path.join(self.receipts_dir, filename)
        with open(photo_path, "wb") as f:
            f.write(photo)
        now = self.clock()
        try:
            with closing(self.connect()) as conn, conn:
                if list_id is not None:
                    self._check_list_id(conn, list_id)
                receipt_id = conn.execute(
                    "INSERT INTO receipts (list_id, photo, shop, date, total_minor, payer_id,"
                    " status, read_error, created_by, created, updated)"
                    " VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?)",
                    (list_id, filename, shop_val, date_str, total_val, payer, status, user_id, now, now),
                ).lastrowid
                return self._receipt_json(self._receipt_row(conn, receipt_id))
        except Exception:
            try:
                os.remove(photo_path)
            except OSError:
                pass
            raise

    def update_receipt(self, receipt_id: int, body: dict) -> dict:
        changes = {}
        if "shop" in body:
            changes["shop"] = _shop(body["shop"])
        if "date" in body:
            changes["date"] = _receipt_date(body["date"])
        if "total_minor" in body:
            changes["total_minor"] = _total_minor(body["total_minor"])
        if "payer_id" in body:
            changes["payer_id"] = _payer_id(body["payer_id"])
        if "list_id" in body:
            val = body["list_id"]
            if val is not None and (isinstance(val, bool) or not isinstance(val, int)):
                raise StoreError("list_id must be an integer or null")
            changes["list_id"] = val
        with closing(self.connect()) as conn, conn:
            row = self._receipt_row(conn, receipt_id)
            if changes.get("list_id") is not None:
                self._check_list_id(conn, changes["list_id"])
            if "total_minor" in changes:
                if changes["total_minor"] is not None:
                    changes["status"] = "confirmed"
                    changes["read_error"] = None
                else:
                    changes["status"] = "manual"
            if changes:
                changes["updated"] = self.clock()
                sets = ", ".join(f"{col} = ?" for col in changes)
                conn.execute(f"UPDATE receipts SET {sets} WHERE id = ?", (*changes.values(), receipt_id))
            return self._receipt_json(self._receipt_row(conn, receipt_id))

    def delete_receipt(self, receipt_id: int, user_id: str):
        with closing(self.connect()) as conn, conn:
            row = self._receipt_row(conn, receipt_id)
            if row["created_by"] != user_id:
                raise StoreError("Only the person who added the receipt can delete it", 403)
            conn.execute("DELETE FROM receipts WHERE id = ?", (receipt_id,))
        if row["photo"]:
            try:
                os.remove(os.path.join(self.receipts_dir, row["photo"]))
            except FileNotFoundError:
                pass

    def receipt_photo_path(self, filename: str):
        if not PHOTO_NAME_RE.match(filename):
            return None
        path = os.path.join(self.receipts_dir, filename)
        return path if os.path.isfile(path) else None

    def spending(self, from_month: str, to_month: str) -> dict:
        sy, sm = _month_ym(from_month, "from")
        ey, em = _month_ym(to_month, "to")
        start_idx = sy * 12 + (sm - 1)
        end_idx = ey * 12 + (em - 1)
        if start_idx > end_idx:
            raise StoreError("from must not be after to")
        count = end_idx - start_idx + 1
        if count > MAX_SPENDING_MONTHS:
            raise StoreError(f"Range can cover at most {MAX_SPENDING_MONTHS} months")

        months_keys = [f"{idx // 12:04d}-{idx % 12 + 1:02d}" for idx in range(start_idx, end_idx + 1)]
        start_date = f"{months_keys[0]}-01"
        end_exclusive = f"{(end_idx + 1) // 12:04d}-{(end_idx + 1) % 12 + 1:02d}-01"

        with closing(self.connect()) as conn:
            summary_rows = conn.execute(
                """
                SELECT substr(date, 1, 7) AS m,
                       COALESCE(SUM(CASE WHEN total_minor IS NOT NULL THEN total_minor ELSE 0 END), 0) AS total_minor,
                       COUNT(*) AS receipt_count,
                       SUM(CASE WHEN total_minor IS NOT NULL AND status != 'confirmed' THEN 1 ELSE 0 END) AS unconfirmed_count,
                       SUM(CASE WHEN total_minor IS NULL THEN 1 ELSE 0 END) AS missing_total_count
                FROM receipts
                WHERE date >= ? AND date < ?
                GROUP BY m
                """,
                (start_date, end_exclusive),
            ).fetchall()
            summary_by_m = {r["m"]: r for r in summary_rows}

            shop_rows = conn.execute(
                """
                SELECT substr(date, 1, 7) AS m,
                       CASE WHEN shop IS NULL OR TRIM(shop) = '' THEN NULL ELSE LOWER(TRIM(shop)) END AS norm_shop,
                       CASE WHEN shop IS NULL OR TRIM(shop) = '' THEN NULL ELSE TRIM(shop) END AS raw_shop,
                       COUNT(*) AS cnt,
                       SUM(total_minor) AS total_minor
                FROM receipts
                WHERE date >= ? AND date < ? AND total_minor IS NOT NULL
                GROUP BY m, norm_shop, raw_shop
                """,
                (start_date, end_exclusive),
            ).fetchall()

            payer_rows = conn.execute(
                """
                SELECT substr(date, 1, 7) AS m,
                       payer_id,
                       SUM(total_minor) AS total_minor
                FROM receipts
                WHERE date >= ? AND date < ? AND total_minor IS NOT NULL
                GROUP BY m, payer_id
                ORDER BY total_minor DESC, payer_id ASC
                """,
                (start_date, end_exclusive),
            ).fetchall()

        # Aggregate shop spellings: pick the most frequent spelling per (month, norm_shop).
        shop_groups: dict[tuple[str, str | None], dict] = {}
        for r in shop_rows:
            key = (r["m"], r["norm_shop"])
            entry = shop_groups.setdefault(key, {"total": 0, "best_shop": r["raw_shop"], "best_cnt": -1})
            entry["total"] += r["total_minor"]
            if r["cnt"] > entry["best_cnt"]:
                entry["best_cnt"] = r["cnt"]
                entry["best_shop"] = r["raw_shop"]

        shops_by_m: dict[str, list] = {}
        for (m, _), entry in shop_groups.items():
            shops_by_m.setdefault(m, []).append({"shop": entry["best_shop"], "total_minor": entry["total"]})
        for lst in shops_by_m.values():
            lst.sort(key=lambda x: (-x["total_minor"], x["shop"] or ""))

        payers_by_m: dict[str, list] = {}
        for r in payer_rows:
            payers_by_m.setdefault(r["m"], []).append({"payer_id": r["payer_id"], "total_minor": r["total_minor"]})

        months = []
        for m in months_keys:
            sr = summary_by_m.get(m)
            months.append({
                "month": m,
                "total_minor": sr["total_minor"] if sr else 0,
                "receipt_count": sr["receipt_count"] if sr else 0,
                "unconfirmed_count": sr["unconfirmed_count"] if sr else 0,
                "missing_total_count": sr["missing_total_count"] if sr else 0,
                "by_shop": shops_by_m.get(m, []),
                "by_payer": payers_by_m.get(m, []),
            })
        return {"currency": self.currency, "months": months}

    @staticmethod
    def _check_list_id(conn, list_id):
        if isinstance(list_id, bool) or not isinstance(list_id, int):
            raise StoreError("list_id must be an integer")
        if conn.execute("SELECT 1 FROM lists WHERE id = ?", (list_id,)).fetchone() is None:
            raise StoreError("List not found")

    @staticmethod
    def _receipt_row(conn, receipt_id: int):
        row = conn.execute("SELECT * FROM receipts WHERE id = ?", (receipt_id,)).fetchone()
        if row is None:
            raise StoreError("Receipt not found", 404)
        return row

    def _receipt_json(self, r) -> dict:
        return {
            "id": r["id"],
            "list_id": r["list_id"],
            "photo_url": RECEIPT_PHOTO_PREFIX + r["photo"] if r["photo"] else None,
            "shop": r["shop"],
            "date": r["date"],
            "total_minor": r["total_minor"],
            "currency": self.currency,
            "payer_id": r["payer_id"],
            "status": r["status"],
            "read_error": r["read_error"],
            "created_by": r["created_by"],
            "created": r["created"],
            "updated": r["updated"],
        }

    # Wishlist (T-041)

    def preview_wishlist_url(self, url) -> dict:
        return self.link_preview(validate_http_url(url))

    def wishlist(self, event_id: int | None = None, bought: bool | None = None) -> list:
        clauses, params = [], []
        if event_id is not None:
            clauses.append("event_id = ?")
            params.append(event_id)
        if bought is not None:
            clauses.append("bought = ?")
            params.append(int(bought))
        where = f"WHERE {' AND '.join(clauses)} " if clauses else ""
        with closing(self.connect()) as conn:
            rows = conn.execute(
                f"SELECT * FROM wishlist_items {where}ORDER BY bought ASC, id DESC",
                params,
            ).fetchall()
            return [self._wishlist_json(r) for r in rows]

    def create_wishlist_item(self, user_id: str, body: dict) -> dict:
        url = validate_http_url(body.get("url"))
        raw_title = body.get("title")
        image_url = body.get("image_url")
        price_minor = _total_minor(body.get("price_minor")) if "price_minor" in body else None
        event_id = _optional_int(body.get("event_id"), "event_id")
        target_member_id = _optional_id(body.get("target_member_id"), "target_member_id")

        if raw_title is None or (isinstance(raw_title, str) and not raw_title.strip()):
            preview = {}
            try:
                preview = self.link_preview(url) or {}
            except StoreError:
                preview = {}
            title = (preview.get("title") or preview.get("shop_name") or url).strip()[:MAX_TEXT]
            if "image_url" not in body:
                image_url = preview.get("image_url")
            if "price_minor" not in body:
                price_minor = preview.get("price_minor")
        else:
            title = _text(raw_title, "title", MAX_TEXT)

        if image_url is not None:
            image_url = validate_http_url(image_url)

        now = self.clock()
        with closing(self.connect()) as conn, conn:
            cur = conn.execute(
                "INSERT INTO wishlist_items"
                " (url, title, image_url, price_minor, currency, event_id, target_member_id, created_by, created, updated)"
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (url, title, image_url, price_minor, self.currency, event_id, target_member_id, user_id, now, now),
            )
            row = self._wishlist_row(conn, cur.lastrowid)
        return self._wishlist_json(row)

    def update_wishlist_item(self, item_id: int, body: dict) -> dict:
        now = self.clock()
        with closing(self.connect()) as conn, conn:
            row = self._wishlist_row(conn, item_id)
            updates, params = [], []
            if "title" in body:
                updates.append("title = ?")
                params.append(_text(body["title"], "title", MAX_TEXT))
            if "image_url" in body:
                updates.append("image_url = ?")
                params.append(validate_http_url(body["image_url"]) if body["image_url"] is not None else None)
            if "price_minor" in body:
                updates.append("price_minor = ?")
                params.append(_total_minor(body["price_minor"]))
            if "event_id" in body:
                updates.append("event_id = ?")
                params.append(_optional_int(body["event_id"], "event_id"))
            if "target_member_id" in body:
                updates.append("target_member_id = ?")
                params.append(_optional_id(body["target_member_id"], "target_member_id"))
            if "reserved_by" in body:
                updates.append("reserved_by = ?")
                params.append(_optional_id(body["reserved_by"], "reserved_by"))
            if "bought" in body:
                updates.append("bought = ?")
                params.append(int(_bool(body["bought"], "bought")))
            if updates:
                updates.append("updated = ?")
                params.append(now)
                params.append(item_id)
                conn.execute(f"UPDATE wishlist_items SET {', '.join(updates)} WHERE id = ?", params)
                row = self._wishlist_row(conn, item_id)
        return self._wishlist_json(row)

    def delete_wishlist_item(self, item_id: int, user_id: str):
        with closing(self.connect()) as conn, conn:
            row = self._wishlist_row(conn, item_id)
            if row["created_by"] != user_id and not row["bought"]:
                raise StoreError("Only the creator can delete an unbought wishlist item", 403)
            conn.execute("DELETE FROM wishlist_items WHERE id = ?", (item_id,))

    @staticmethod
    def _wishlist_row(conn, item_id: int):
        row = conn.execute("SELECT * FROM wishlist_items WHERE id = ?", (item_id,)).fetchone()
        if row is None:
            raise StoreError("Wishlist item not found", 404)
        return row

    @staticmethod
    def _wishlist_json(r) -> dict:
        return {
            "id": r["id"],
            "url": r["url"],
            "title": r["title"],
            "image_url": r["image_url"],
            "price_minor": r["price_minor"],
            "currency": r["currency"],
            "event_id": r["event_id"],
            "target_member_id": r["target_member_id"],
            "reserved_by": r["reserved_by"],
            "bought": bool(r["bought"]),
            "created_by": r["created_by"],
            "created": r["created"],
            "updated": r["updated"],
        }
