# T-024 — Receipts: read shop, date and total with Gemini (server)

**Status:** ready  
**Component:** ImageFrameServer  
**Depends on:** T-020 (receipts API), [ADR 0009](../decisions/0009-receipts-gemini.md)

## Summary

When `GEMINI_API_KEY` is set, the server asks Gemini for the shop, the purchase date and the
total of each uploaded receipt and fills them in as a suggestion for the user to confirm.
Without the key, nothing changes from T-020.

## Configuration

| Variable | Meaning |
|---|---|
| `GEMINI_API_KEY` | Empty (default) = receipts are never sent anywhere. |
| `GEMINI_MODEL` | Model id. Required when the key is set; if it is missing the server logs an error at start and keeps reading off. `example.env` carries the free-tier vision model current at implementation time (check Google's model list then; don't copy a name from this task). |

`docker/example.env` gets both with a privacy note: "Optional. With a key, receipt photos are
sent to Google Gemini to read the shop, date and total. On the free tier Google may use them
to improve its products. Leave empty to type totals by hand." `GET /status` reports
`receipts_ai: true` when both are set.

## Reading

- `receipts.py` holds the prompt, the response schema, the HTTP call and the parsing of the
  answer, behind `read_receipt(jpeg_bytes) -> ReceiptReading | ReadError`. The store and the
  routes never talk to Gemini directly, so tests pass a fake reader.
- The call: HTTPS with `requests` to the Generative Language API's `generateContent` for
  `GEMINI_MODEL`, the image inline, JSON output constrained by a response schema, the key in
  a request header (not in the URL). Timeout 30 s. Check the exact endpoint, header and
  schema syntax in the current Gemini API docs before coding. Never log the key, the request
  URL with a key, or the photo.
- Image sent: decoded with Pillow, turned upright (`ImageOps.exif_transpose`), longest edge
  1600 px, JPEG quality 85, no EXIF. The stored original is not changed.
- Asked for: `shop` (the store's everyday name, e.g. "Biedronka", not the legal entity
  "Jeronimo Martins Polska S.A."), `date` (`YYYY-MM-DD`), `total` (the amount paid as a string
  with a dot, e.g. `"45.20"`; on Polish fiscal receipts the line `SUMA PLN`). Each may be
  null when not legible. The prompt says the photo is usually a Polish fiscal receipt and
  that nothing may be guessed.
- Validation of the answer (the model is not trusted):
  - `total`: spaces removed, a comma accepted as the decimal separator, then `Decimal` →
    minor units; anything else, or a value outside 1..100 000 000, counts as null.
  - `date` must parse and lie between one year before the upload and one day after it, else
    the upload date stays.
  - `shop` trimmed, at most 100 chars.
- Result: a total was read → `status = "read"`, fields filled. No total → `status = "manual"`,
  `read_error = "no_total"`, shop and date kept if valid.
- `read_error` values (also used by the app): `off` (no key), `no_total`, `rate_limited`
  (`429`), `failed` (network error, timeout, other HTTP errors, unparsable answer).

## Flow

- `POST /receipts` with reading on: the receipt is created `pending` and queued; the response
  returns at once. One worker thread reads receipts one at a time.
- The worker writes its result only if the receipt is still `pending` (a user may have typed
  the total meanwhile, or deleted the receipt).
- On start, receipts left `pending` by a restart are queued again.
- `POST /receipts/<id>/read`: re-queues a `manual` or `read` receipt as `pending` ("Read
  again"); `409` when reading is off or the receipt is `confirmed` or already `pending`.
- No automatic retries: a `429` or failure becomes `manual` right away and the user decides.

## Tests

`tests/test_receipt_reading.py` with a fake reader and recorded Gemini answers as fixtures
(success, null total, garbage JSON, HTTP 429, timeout): status transitions; total parsing
(`"45.20"`, `"1 284,50"`, `"45.2 zł"` → null, negative, huge); date window; user edit
during `pending` is not overwritten; pending receipts re-queued at start; key never appears
in logs (`assertLogs`); the request carries only the image and the prompt. No test calls
Google.

## Acceptance criteria

- [ ] Without `GEMINI_API_KEY`: no outbound request, new receipts are `manual`/`off`,
      `receipts_ai` is false.
- [ ] With the key, on the owner's server: five real Polish receipts (supermarket, petrol
      station, pharmacy, a long one, a crumpled one) uploaded with `curl` leave `pending`
      within 15 s; note in the task how many totals, dates and shops were right.
- [ ] A wrong key or no internet → `manual` with `read_error: "failed"`; "read again" works
      after fixing it.
- [ ] The user's own values are never overwritten by a late reading.
- [ ] Tests pass in CI; README (settings, a "Receipts and Google Gemini" privacy paragraph),
      `example.env`, and the one-line exception in STATUS.md's vision and ARCHITECTURE.md.

## Out of scope

- Line items, categories, VAT, payment method.
- Other providers, a local model, on-device OCR (the reader interface leaves room for them).
- Re-reading old receipts in bulk.
