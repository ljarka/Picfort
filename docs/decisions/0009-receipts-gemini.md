# ADR 0009 — Receipts are read by Gemini (free tier), on the server

**Status:** proposed  
**Date:** 2026-11-11  
**Exception to:** the "no cloud service" vision in STATUS.md, for receipt photos only

## Context

The shopping feature (T-020, T-021, T-024) should read a receipt photo and fill in the shop,
date and total, then sum the spending. The options were:

1. On-device OCR (ML Kit) + rules: free and private, but reading "the total" from noisy OCR
   text needs hand-written rules per receipt layout.
2. **Gemini API, free tier:** understands receipts well and returns structured JSON. The photo
   leaves the house; the free-tier models, limits and data-use terms change over time.
3. A local vision LLM (e.g. Ollama) next to Immich: private, but heavy for the home server.

The owner chose option 2 and accepted the privacy trade-off (2026-11-11). Read from a
receipt: **shop, date and total only**, no line items or categories.

## Decision

- The **server** calls Gemini, not the phone: one API key (`GEMINI_API_KEY` in `.env`), never
  on phones; the receipt photo is uploaded to the Picfort server anyway.
- **Opt-in.** Without `GEMINI_API_KEY` nothing is ever sent to Google and receipts are typed
  in by hand. Receipts and spending work fully without Gemini (T-020 ships before T-024).
- The model is configuration, not code: `GEMINI_MODEL`. `example.env` carries the free-tier
  vision model that is current when T-024 is implemented; no model name is hard-coded as a
  fallback in the docs or tests.
- The call is plain HTTPS with `requests` (no SDK dependency), asks for JSON constrained by a
  response schema, and sends the key in a header, never in the URL (URLs end up in logs and
  exception messages). Endpoint, header and schema syntax are checked against the current
  Gemini API docs when implementing.
- Only the receipt photo is sent, upright and downscaled (longest edge 1600 px, JPEG), with
  EXIF removed (no GPS position). No names, no list contents, no Immich data.
- The model's answer is **a suggestion**. The server validates it (date plausible, total
  positive), stores it as `status = "read"`, and the app asks the user to confirm. No
  self-reported "confidence" is used: models are confidently wrong about it.
- When Gemini is off, fails, times out, hits a rate limit or returns nothing usable, the
  receipt becomes `status = "manual"` with a `read_error` code and the user types the total.
  "Read again" retries. Reading a receipt is a convenience, never a requirement.
- The user can always correct shop, date and total; a saved receipt is `"confirmed"`.

Receipt `status` values, used by server and app alike:

| Status | Meaning |
|---|---|
| `pending` | Uploaded, waiting for Gemini (only when Gemini is on). |
| `read` | Gemini filled in the fields; not confirmed by a person yet. |
| `manual` | Not read (see `read_error`); a person must type the total. |
| `confirmed` | A person saved the fields. |

## Consequences

- Receipt photos (shop, items, sometimes a card's last digits) are processed by Google. On
  the free tier Google may use them to improve its products, depending on the terms for the
  owner's country: read the current Gemini API terms for Poland/EEA when creating the key.
  The README, `example.env` and Settings → Picfort server state that receipts are read by
  Google Gemini when it is on.
- STATUS.md's vision line and ARCHITECTURE.md ("everything runs at home") get a one-line
  exception when T-024 lands.
- The server needs outbound HTTPS to Google's Generative Language API.
- A family's receipts are far below any free-tier limit; a `429` still falls back to manual.
- If the free tier disappears or the model is retired, reading stops with `manual` receipts
  and a `read_error`; nothing else breaks.
