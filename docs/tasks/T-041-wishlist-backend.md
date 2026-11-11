# T-041 — Wishlist & link metadata preview: Picfort server

**Status:** ready  
**Component:** ImageFrameServer  
**Depends on:** T-018 (`shopping.db`, `store.py`), ADR 0008, ADR 0010

## Summary

Store a family-wide wishlist in `shopping.db` with link preview extraction (OpenGraph tags: title,
image, price from online stores such as Allegro or Amazon). Items can be linked to a family
event (birthdays, holidays) and assigned to a recipient member.

## Data (`shopping.db`, `SCHEMA_VERSION` 1 → 2, additive)

```sql
CREATE TABLE wishlist_items (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  url              TEXT NOT NULL,
  title            TEXT NOT NULL,          -- 1..200 chars
  image_url        TEXT,                   -- product image from OpenGraph / store
  price_minor      INTEGER,                -- optional price in grosze
  currency         TEXT NOT NULL DEFAULT 'PLN',
  event_id         INTEGER,                -- optional link to events.db id
  target_member_id TEXT,                   -- optional recipient (family.db user_id)
  reserved_by      TEXT,                   -- Immich user id who plans to buy it (secret gift)
  bought           INTEGER NOT NULL DEFAULT 0, -- 1 = purchased
  created_by       TEXT NOT NULL,          -- Immich user id
  created          TEXT NOT NULL,          -- ISO 8601 UTC
  updated          TEXT NOT NULL
);
CREATE INDEX wishlist_event ON wishlist_items(event_id);
```

Migration: additive `ALTER TABLE` / `CREATE TABLE` with `.v1.bak` backup per ADR 0010.

## API

| Endpoint | Result |
|---|---|
| `POST /wishlist/preview` `{"url": "https://..."}` | Fetches webpage HTML (max 1 MB), extracts OpenGraph metadata (`og:title`, `og:image`, `og:price:amount`, `<title>`). Returns `{"title", "image_url", "price_minor", "shop_name"}`. |
| `GET /wishlist` | Returns `{"items": [wishlist_item, …]}`, optional query params `?event_id=<id>` and `?bought=0/1`. Sorted by `bought ASC, id DESC`. |
| `POST /wishlist` | `{"url", "title"?, "image_url"?, "price_minor"?, "event_id"?, "target_member_id"?}`. If `title` is omitted, server fetches metadata automatically. `201` with created item. |
| `PATCH /wishlist/<id>` | Update title, price, event_id, target_member_id, `reserved_by` or `bought`. Anyone in family. |
| `DELETE /wishlist/<id>` | `204`. Creator only or anyone once bought. |

- URL validation: must be a valid `http://` or `https://` URL.
- OpenGraph parser: uses Python standard library `html.parser` (no heavy third-party scraper dependencies), with a standard mobile browser User-Agent so online shops return OG metadata.
- Safe timeout: link preview call times out after 4 seconds to never hang server workers.

## Tests

`tests/test_wishlist.py`:
- Metadata extractor with fake HTML response (`og:title`, `og:image`, fallback `<title>`).
- CRUD operations for wishlist items.
- Filtering by `event_id` and `bought`.
- Marking item as bought / reserved.
- Additive schema migration with `.bak` verification.

## Acceptance criteria

- [ ] `POST /wishlist/preview` reliably returns product title and image for standard shop URLs.
- [ ] Endpoints above with token authentication and Picfort key.
- [ ] Items linked to an event are returned when filtered by `event_id`.
- [ ] Tests pass in CI; README API table updated.
