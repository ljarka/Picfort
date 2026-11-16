# ADR 0009 — Receipts are read on-device with Gemini Nano (when supported)

**Status:** accepted  
**Date:** 2026-11-16 (revised from 2026-11-11 proposal)

## Context

The shopping feature (T-020, T-021, T-024) should read a receipt photo and fill in the shop,
date and total, then sum the spending. The earlier 2026-11-11 proposal considered calling
the cloud Gemini API from the Picfort server as an explicit exception to Picfort's "no cloud
service" vision.

The owner revised this decision (2026-11-16): receipt photos should **stay local** and use
the phone's on-device assistant/model (**Gemini Nano** via **AICore** / ML Kit GenAI Prompt
API). If a phone does not have on-device Gemini Nano support, the auto-read feature is simply
not supported on that device and the user types the fields by hand.

Read from a receipt: **shop, date and total only**, no line items or categories.

## Decision

- **On-device only (Android):** Receipt reading runs locally in the Android app (`:feature:shopping`)
  using **ML Kit GenAI Prompt API** (`com.google.mlkit:genai-prompt`) backed by **Gemini Nano**
  (Android AICore). No receipt photo is ever sent to a cloud AI service, and the Picfort server
  has no Gemini integration or API keys.
- **Device capability check:** Before offering or running receipt reading, the app checks
  whether Gemini Nano / AICore is available on the device. On unsupported devices (or when
  AICore is unavailable), the feature is simply not supported: no "Read again" / AI UI is
  shown and no error is surfaced; the user enters shop, date and total manually (T-021).
- **Pre-fill as a suggestion:** The model's answer is treated as an untrusted suggestion.
  The app validates it (plausible date, positive decimal total, shop length) and pre-fills
  only fields the user hasn't touched in the receipt sheet.
- **Manual fallback always works:** Receipts and spending work fully without on-device AI
  (T-020 + T-021). The user can always edit shop, date and total; saving marks the receipt
  `confirmed`.

Receipt `status` values, used by server and app alike:

| Status | Meaning |
|---|---|
| `manual` | Uploaded without a confirmed total (not read or not yet saved by a person). |
| `confirmed` | A person saved the fields. |

## Consequences

- **Zero cloud exception:** Picfort remains 100% free of cloud AI services; receipt photos
  never leave the phone and the home server.
- **Hardware-dependent convenience:** Devices with multimodal Gemini Nano (AICore) get
  automatic shop/date/total pre-fill; older or unsupported devices work identically in manual
  mode without degraded UX.
- **Simpler server:** `T-020` needs no `GEMINI_API_KEY`, no background worker queue, no
  `pending`/`read_error` states, and no `/receipts/<id>/read` endpoint.
