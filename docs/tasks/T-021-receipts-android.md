# T-021 — Receipts: Android (add, check, correct)

**Status:** done  
**Component:** Android app  
**Depends on:** T-019 (list detail, `PicfortServerClient`), T-020 (API). Works with and
without T-024 (reading).

## Summary

Add a receipt to a shopping list with the camera or the photo picker, type shop, date and
total (or have them pre-filled on-device in T-024 when the phone supports Gemini Nano) and
save. A list shows its receipts and their sum. The monthly summary is T-025.

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

- All fields are editable at once. (T-024 adds on-device Gemini Nano pre-fill and "Read again" on supported phones.)
- **Save** → `PATCH /receipts/<id>` (→ `confirmed`). Enabled when the total is valid.
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

- [ ] Camera and picker both add a receipt to the open list; the sheet opens.
- [ ] The sheet asks for shop, date and total; Save works; the list's sum updates.
- [ ] `12,50`, `12.5` and `12` are accepted; `0`, `12,555` and text are not.
- [ ] Paid by and List can be changed; the receipt moves to the other list.
- [ ] Delete is offered only on own receipts and removes it from the list's sum.
- [ ] Upload failure offers Retry without taking the photo again.
- [ ] TalkBack: fields have labels; ⚠ is read as "not confirmed".
- [ ] Paparazzi: sheet (manual), list with receipts, dark.
- [ ] Unit tests: total parsing/formatting.
- [ ] README Features; CI passes.

## Out of scope

- Monthly summary and receipts without a list (T-025).
- Line items, categories, splitting a receipt between people.
- Cropping or enhancing the photo; several photos per receipt; PDF e-receipts.
