# T-020 — Receipts and spending: Picfort server

**Status:** ready  
**Component:** ImageFrameServer  
**Depends on:** T-018 (`shopping.db` with the `receipts` table, `store.py`, auth prefixes),
ADR 0008

## Summary

Upload a receipt photo, optionally attached to a shopping list, with shop, date and total
typed by a person, and serve monthly spending summaries. No AI here: reading the photo with
Gemini is T-024 and plugs into the `status` field defined below.

## Data

The `receipts` table already exists (T-018). Photos are files in `RECEIPTS_PATH` (default
`receipts`; `/data/receipts` in the `Dockerfile` `ENV`), named `<uuid hex>.<ext>` like wall
photos. The currency is the server's `CURRENCY` (default `PLN`): one currency for
everything, amounts in minor units.

A receipt:

```json
{"id": 12, "list_id": 3, "photo_url": "/receipts/photo/<file>",
 "shop": "Lidl", "date": "2026-11-10", "total_minor": 8410, "currency": "PLN",
 "payer_id": "<immich user id>", "status": "confirmed", "read_error": null,
 "created_by": "<immich user id>", "created": "2026-11-11T17:03:00Z", "updated": "…"}
```

`status` is one of `pending`, `read`, `manual`, `confirmed` (ADR 0009). In this task a new
receipt is always `manual` with `read_error: "off"`; `pending` and `read` appear with T-024.

## API

| Endpoint | Result |
|---|---|
| `POST /receipts` multipart: `photo`, `date`, `list_id?`, `payer_id?` | `201` receipt. `photo` is JPEG, PNG or HEIC up to 10 MB, checked like wall photos (move `WallStore._inspect_photo` to a shared helper). `date` is the phone's today (`YYYY-MM-DD`): the default purchase date, since the server doesn't know the family's time zone. `payer_id` defaults to the caller. |
| `GET /receipts?month=2026-11&list_id=3` | Receipts by `date` desc, then `id` desc. Both filters optional; without `month` at most the 200 newest. |
| `GET /receipts/<id>` | One receipt. |
| `PATCH /receipts/<id>` `{shop?, date?, total_minor?, payer_id?, list_id?}` | Updated receipt. Anyone. If `total_minor` is set after the change, `status` becomes `confirmed` and `read_error` null; otherwise the status stays. `null` clears `shop`, `total_minor`, `list_id`. |
| `DELETE /receipts/<id>` | `204`; deletes the photo file. Creator only (`403`). |
| `GET /receipts/photo/<file>` | The photo, `Cache-Control: private` with a long max-age, as wall photos. |
| `GET /spending?from=2026-06&to=2026-11` | See below. |

Validation → `400`: `date` not a real `YYYY-MM-DD` date; `total_minor` not an integer in
1..100 000 000; `shop` longer than 100 chars; `list_id` that doesn't exist; empty `payer_id`;
missing photo; `month`/`from`/`to` not `YYYY-MM`, `from` after `to`, more than 24 months.
`413`/`415` for the photo as on the wall. `404` unknown receipt or photo.

### `GET /spending`

```json
{"currency": "PLN",
 "months": [{"month": "2026-11", "total_minor": 128450,
             "receipt_count": 14, "unconfirmed_count": 2, "missing_total_count": 1,
             "by_shop":  [{"shop": "Biedronka", "total_minor": 64000}],
             "by_payer": [{"payer_id": "<immich user id>", "total_minor": 90000}]}]}
```

- One entry per month from `from` to `to` inclusive, oldest first, zeros for months without
  receipts (the app draws bars without filling gaps). A receipt belongs to the month of its
  `date`.
- `total_minor`, `by_shop` and `by_payer` sum every receipt that has a total, whatever its
  status. `unconfirmed_count` counts those with status `read`; `missing_total_count` those
  without a total (not in the sums).
- `by_shop` groups by the trimmed, case-insensitive shop name and returns the spelling used
  most often; receipts without a shop are one entry with `"shop": null`. Both lists are
  sorted by amount, largest first.
- All sums are integer arithmetic in SQL.

### `/status`

Add `"receipts_ai": false` to `GET /status` (T-024 sets it from the configuration). The app
uses it to say whether receipts are read by Gemini.

## Tests

`tests/test_receipts.py`: upload (formats, too large, missing date), the photo is served and
needs a token, patch rules (`confirmed` only with a total, clearing fields), creator-only
delete removes the file, deleting a list keeps the receipt with `list_id` null, filters.
Spending: month boundaries (31 Oct vs 1 Nov), empty months, shop grouping (`"LIDL"`,
`"Lidl "`), receipts without total or shop, counts, range validation.

## Acceptance criteria

- [ ] Endpoints above with token auth and the optional Picfort key.
- [ ] A receipt uploaded with `curl`, patched with a total, shows up in `/spending`.
- [ ] Delete removes the photo file; deleting a list keeps its receipts.
- [ ] `RECEIPTS_PATH` and `CURRENCY` read from the environment; Docker keeps photos in `/data`.
- [ ] Tests pass in CI; README (API table, settings, backup line) and `docker/example.env`
      (`CURRENCY`, the `DATA_LOCATION` comment) updated.

## Out of scope

- Reading the photo (T-024).
- Line items and categories (owner decision 2026-11-11: total + shop + date only).
- Several currencies, expenses without a photo, duplicate detection.
- Budgets and limits, CSV export, "who owes whom" (v2 candidates).
