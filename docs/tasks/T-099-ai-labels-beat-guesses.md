# T-099 — "Hide screenshots & documents": the photo AI's answer beats the guesses

**Status:** done (`f2eea60`)  
**Component:** Server (`slideshow.py`, `immich.py`, `photo_ai.py`), Android (`:feature:timeline`)  
**Depends on:** T-088 (guesses), T-089 (photo AI), T-102 (labels in the app)

## Summary

The filters guessed non-photos from "no camera make/model in EXIF", OCR hits ("VAT", "NIP",
"PARAGON", …) and phone folders. That also hid WhatsApp photos, scans and street signs. Once the
photo AI has seen a photo, its category decides; only photos not analyzed yet keep the guesses.

## Design

- AI non-photo categories: `screenshot`, `document`, `receipt`, `meme` (app:
  `AI_NON_PHOTO_CATEGORIES`; server: the complement of `AI_PHOTO_CATEGORIES` = photo, accidental,
  kids_art).
- App: `isHiddenNonPhoto(…, ai)`: analyzed → hidden iff in `ai.nonPhotos`; else the old guesses.
  `TimelineRepository` combines `remote.aiLabels()` into the filter.
- Frame (`Picker`): `random_images` / `images_taken_between` take `keep` (AI photos pass the
  no-EXIF guess); OCR document ids minus AI photos. The AI exclusions (`slideshow_excluded_ids`)
  and sensitive (T-093) stay.
- Local phone-only media keep the folder/file-name guess (the AI sees only Immich).

## Acceptance criteria

- [x] Server test: WhatsApp-like (no EXIF) and OCR-hit photos the AI saw as photos reach the
      frame in random and memories; unanalyzed guesses still hidden.
- [x] App test: same on the timeline; AI screenshot with camera EXIF hidden.
- [x] All checks green.
- [ ] Owner: after the first AI pass, the timeline filter keeps WhatsApp photos.
