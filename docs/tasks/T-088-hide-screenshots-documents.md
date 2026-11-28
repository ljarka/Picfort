# T-088 — Hide screenshots & documents (timeline + frame)

**Status:** done (`b6a883d`)  
**Component:** Picfort server (`immich.py`, `slideshow.py`, `app.py`), Android (`:core:database`, `:feature:timeline`, `:feature:frame`)  
**Depends on:** —

## Summary

Allow hiding screenshots, saved images without camera EXIF, and OCR-detected receipts/documents both on the Photos timeline and in the picture frame's automatic slideshow (`random` and `memories` modes).

## Design

### Signals
1. **Screenshots & non-camera images:** photos lacking camera `make`/`model` in EXIF (unless faces are recognized on the frame), or with `Screenshot` in `originalFileName`, or local phone photos in `Screenshots` / `WhatsApp Images` / `Download` buckets. Videos are never hidden.
2. **Documents & receipts (Immich OCR):** `POST /api/search/metadata` with `ocr` for Polish document keywords (`PARAGON`, `NIP`, `FAKTURA`, `SUMA PLN`, `VAT`, `UMOWA`, `PESEL`), refreshed at most once per day and gracefully ignored on Immich servers without OCR.

### Frame (`picfort-server` + `:feature:frame`)
- `SlideshowSettings.filter_photos` (default `True`), exposed in `/status` and `/slideshow`.
- `ImmichClient.random_images` and `images_taken_between` pass `withExif: True, withPeople: True` when filtering and skip `is_likely_non_photo` assets; `Picker` caches `document_ids()` per day.
- `album` mode never filters.
- Android `FrameScreen` shows a **"Hide screenshots & documents"** switch under Quiet hours in `random` and `memories` modes.

### Timeline (`:core:database` + `:feature:timeline`)
- `ImagesDatabase` v24 adds cache tables `remote_asset_exif` (`AssetExifV1` / `PartnerAssetExifV1` from `/api/sync/stream`) and `remote_documents` (OCR ids from `POST /api/search/metadata`).
- `TimelineRepository` persists `hide_non_photos` in `SharedPreferences` (default `false`) and filters `buildTimelineIndex` without affecting backup counts.
- `TimelineScreen` shows a **"Hide screenshots & documents"** `FilterChip` at the top of the timeline.

## Verification

- `picfort-server`: 183 unit tests pass (`unittest discover -s tests`), including `NonPhotoHeuristicTest`, `PickerTest`, and `ApiContractTest`.
- Android: `./scripts/check-modular-boundaries.sh`, `./gradlew ktlintCheck`, `./gradlew testDebugUnitTest`, `:feature:timeline:verifyPaparazziDebug`, `:feature:frame:verifyPaparazziDebug` all green.
