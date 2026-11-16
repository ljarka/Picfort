# T-024 — Receipts: read shop, date and total on-device with Gemini Nano (Android)

**Status:** done  
**Component:** Android app (`:feature:shopping`)  
**Depends on:** T-021 (receipts sheet & upload), [ADR 0009](../decisions/0009-receipts-gemini.md)

## Summary

When the phone supports **Gemini Nano** via **Android AICore** (ML Kit GenAI Prompt API),
the Android app reads the shop, purchase date and total from the receipt photo locally on
the device and pre-fills them in the receipt sheet for the user to confirm. If the phone
does not support on-device Gemini Nano, the feature is simply not supported and the receipt
sheet works in manual mode without any AI UI or error.

## Device capability & privacy

- Uses `com.google.mlkit:genai-prompt` (ML Kit GenAI Prompt API) with `ModelPreference.FULL`
  and multimodal input (`ImagePart` + `TextPart`), plus structured output (`@Generable` /
  JSON schema) if supported by the library version.
- **No cloud fallback:** Receipts are never sent to Google's cloud API or any external server.
- Before showing AI UI or starting inference, check feature/model availability via ML Kit
  GenAI (`checkFeatureStatus`).
  - If `UNAVAILABLE` / unsupported on this device: do not show "Read with AI" / "Read again",
    do not show a "not supported" banner, and keep the receipt sheet purely manual.
  - If `DOWNLOADABLE`: optionally trigger/await model download only when the user taps
    "Read from photo" (or check if already `AVAILABLE` for automatic reading).

## Reading & validation

- Behind a `ReceiptReader` interface in `:feature:shopping` so unit tests and Paparazzi
  screenshots run with a fake reader without AICore.
- Image input: upright bitmap downscaled to longest edge 1600 px (keeps small fiscal print
  legible without exceeding on-device context/memory limits).
- Prompt asks for:
  - `shop`: everyday store name (e.g. `"Biedronka"`, not `"Jeronimo Martins Polska S.A."`)
  - `date`: `YYYY-MM-DD`
  - `total`: amount paid as a decimal string (on Polish fiscal receipts, the `SUMA PLN` line)
  - Any field may be `null` when not legible; nothing may be guessed.
- Validation (model output is never trusted):
  - `total`: spaces removed, `,` accepted as decimal separator, parsed with `BigDecimal` to
    minor units; invalid or outside `1..100_000_000` → `null`.
  - `date`: must parse as a real date between 1 year before today and 1 day after today;
    otherwise ignored (keeps default/user date).
  - `shop`: trimmed, max 100 chars.

## Flow in the receipt sheet (T-021)

- When a receipt is added (camera or picker) on a supported device with Gemini Nano ready:
  - The receipt sheet opens immediately with all fields editable and shows a subtle inline
    indicator `"Reading receipt on device…"` while inference runs in a coroutine.
  - When inference finishes, only fields the user has **not** touched are filled in.
  - Hint after read: `"Read on device. Check the total and save."` (or `"Couldn't find the
    total. Type it."` if `total` is `null`).
- **Read again** button in the receipt sheet: shown **only** when the device supports
  Gemini Nano; re-runs `ReceiptReader` on the local photo (or downloaded receipt photo) and
  updates untouched fields.
- If the user closes the sheet without saving, nothing is lost: the photo is already on the
  Picfort server (`manual`), and tapping **Save** sends `PATCH /receipts/<id>` (`confirmed`).

## Tests

- Unit tests in `:feature:shopping` with a fake `ReceiptReader`:
  - Total parsing (`"45.20"`, `"1 284,50"`, `"45.2 zł"` → null, negative, huge) and
    `BigDecimal` conversion.
  - Date window validation and `shop` trimming/length cap.
  - Untouched-field preservation when user edits during inference.
  - Unsupported device (`Available = false`) hides AI indicator and "Read again" button.
- Paparazzi snapshots (light + dark): receipt sheet in `reading` state and `read` state.

## Acceptance criteria

- [ ] On a phone without Gemini Nano / AICore: no AI UI, no crash, no error; manual receipt
      entry works identically to T-021.
- [ ] On a supported phone (e.g. Pixel with AICore): taking/picking a receipt photo fills
      `shop`, `date`, `total` locally without internet/cloud AI; user-edited fields are not
      overwritten; "Read again" works.
- [ ] Zero outbound requests to external AI services; no `GEMINI_API_KEY` anywhere.
- [ ] Unit tests + Paparazzi + `./scripts/check-modular-boundaries.sh` + `./gradlew ktlintCheck`
      green.

## Out of scope

- Line items, categories, VAT, payment method.
- Cloud Gemini API fallback (explicitly rejected in ADR 0009).
- Bulk re-reading of old receipts.
