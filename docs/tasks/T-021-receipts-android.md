# T-021 — Receipts: Android (add, check, correct)

**Status:** ready (first step: mockup for the owner to approve)  
**Component:** Android app  
**Depends on:** T-019 (list detail, `PicfortServerClient`), T-020 (API). Works with and
without T-024 (reading).

## Summary

Add a receipt to a shopping list with the camera or the photo picker, see what was read (when
the server reads receipts), correct it and save. A list shows its receipts and their sum.
The monthly summary is T-025.

## Mockup first

`docs/wireframes/receipts-v1.png`: the receipt sheet (reading, read, manual) and the list's
receipts section. Owner approval before coding.

## Add a receipt

- Entry point: 🧾 "Add receipt" in the list detail top bar (also on archived lists). T-025 adds
  a second one without a list.
- A small menu: **Take photo** (`TakePicture` with the existing `FileProvider`, as the wall's
  compose sheet) or **Choose photo** (`PickVisualMedia`, images only). No camera permission
  is needed for either.
- Compress before upload: longest edge 2400 px, JPEG 90%, upright (receipts need readable
  small print, so larger than wall photos; reuse the wall's `compress` with parameters).
- `POST /receipts` with `photo`, `list_id` and `date` = today on the phone. While uploading:
  a progress row in the list; on failure a snackbar with **Retry** (the compressed photo is
  kept until it is uploaded or dismissed).
- After the upload the receipt sheet opens.

## Receipt sheet (modal bottom sheet, full height)

```
┌─────────────────────────────┐
│ [photo]  Reading the receipt…│  only while `pending`
│ Shop    [ Lidl            ] │
│ Date    [ 10 Nov 2026     ] │  M3 DatePicker
│ Total   [ 84,10        zł ] │  decimal keyboard
│ Paid by [ (◯) Mama      ▾ ] │  family members, default: me
│ List    [ Biedronka     ▾ ] │  active lists + "No list"
│ [Read again]  [Delete] [Save]│
└─────────────────────────────┘
```

- `pending`: fields are editable at once; poll `GET /receipts/<id>` every 2 s for at most
  60 s. When it becomes `read`, fill only the fields the user hasn't touched. After 60 s stop
  polling and show "Couldn't read the receipt. Type the total."
- `read`: hint "Read from the photo. Check the total and save."
- `manual`: hint by `read_error`: `off` → none; `no_total` → "Couldn't find the total. Type
  it."; `rate_limited` / `failed` → "Couldn't read the receipt. Type the total or try again."
- **Save** → `PATCH` (→ `confirmed`). Enabled when the total is valid.
- **Read again** → `POST /receipts/<id>/read`; shown only when `/status` has `receipts_ai:
  true` and the receipt is `read` or `manual`.
- **Delete** (own receipts only) asks for confirmation.
- Total input: accepts `,` and `.` as the decimal separator, at most two decimals, greater
  than zero; converted to minor units with `BigDecimal`, never through `Double`. Shown with
  `NumberFormat.getCurrencyInstance` for the device locale and the server's `currency`.
- Tapping the photo opens it full screen with zoom (the wall's `PhotoViewer` /
  `PinchToZoom`). Photos load through Coil with the Immich token (`PicfortAuthInterceptor`,
  T-019).
- Closing the sheet without Save keeps the receipt as it is (`read` or `manual`); it is
  marked ⚠ in lists until someone confirms it.

## List detail

Below the items, a section "Receipts · 129,30 zł" (sum of the list's receipts with a total)
with one row per receipt: date, shop (or "Receipt"), total (or "No total"), ⚠ when not
`confirmed`. Tap opens the sheet. Data: `GET /receipts?list_id=<id>`, loaded with the list and
after every change; not part of the 10 s poll. Hidden when the list has no receipts.

## Settings

Settings → Picfort server: a line "Receipts are read by Google Gemini" when `/status` has
`receipts_ai: true` (add the field to `FrameResponse`; missing = false).

## Acceptance criteria

- [ ] Camera and picker both add a receipt to the open list; the sheet opens.
- [ ] Server without reading: the sheet asks for the total; Save works; the list's sum
      updates.
- [ ] Server with reading (T-024): fields fill in within about 15 s; values typed meanwhile
      are kept; Save confirms; "Read again" works.
- [ ] `12,50`, `12.5` and `12` are accepted; `0`, `12,555` and text are not.
- [ ] Paid by and List can be changed; the receipt moves to the other list.
- [ ] Delete is offered only on own receipts and removes it from the list's sum.
- [ ] Upload failure offers Retry without taking the photo again.
- [ ] TalkBack: fields have labels; ⚠ is read as "not confirmed".
- [ ] Paparazzi: sheet (pending, read, manual with each hint), list with receipts, dark.
- [ ] Unit tests: total parsing/formatting, "fill only untouched fields", polling stop.
- [ ] README Features; CI passes.

## Out of scope

- Monthly summary and receipts without a list (T-025).
- Line items, categories, splitting a receipt between people.
- Cropping or enhancing the photo; several photos per receipt; PDF e-receipts.
