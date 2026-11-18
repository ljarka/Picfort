# T-083 — Wishlist: take a photo or choose one from the phone

**Status:** done (30105f8)  
**Component:** Picfort server (`shopping.py`, `app.py`), Android (`:feature:shopping`, `:app` file paths and auth paths)  
**Depends on:** T-041, T-042. Owner request 2026-11-18: adding a wishlist item only offered an image URL.

## Summary

The add / edit wishlist sheet gets **Take photo** and **Choose photo** next to the image. A photo
from the phone wins over the image URL and becomes the item's image for the whole family, also for
things that have no shop link with a picture (a toy seen in a shop, a book on a shelf).

## Design

### Server
- `PUT /wishlist/<id>/photo` (multipart `photo`, JPEG / PNG / HEIC ≤ 10 MB, `inspect_photo`) → the item,
  `image_url` = `/wishlist/photo/<random>.<ext>`. Anyone can set it (like every wishlist field).
- `GET /wishlist/photo/<file>` → the file, private cache for a year (same as receipts and wall photos),
  behind the Immich token like every `/wishlist` path.
- Files in `WISHLIST_PHOTOS_PATH` (Docker: `/data/wishlist_photos`; default next to `shopping.db`).
- The old own photo is deleted when a new photo is uploaded, when `image_url` is changed or cleared
  by `PATCH`, and when the item is deleted. Shop image URLs are never touched.

### Android
- Sheet: under the image preview a row **Take photo** (camera into `cacheDir/wishlist/`, FileProvider
  path `wishlist`) · **Choose photo** (system photo picker, images only). With a photo picked: preview
  shows it, the image URL field and "Load photo in browser" hide, **Remove photo** goes back to the URL.
- Save: create / update the item first, then compress (longest edge 1600 px, JPEG 85) and
  `PUT …/photo`. Editing without touching the image keeps it (`keepImage`): the phone never sends the
  server's own photo URL back as an `image_url`.
- `WishlistItemDto.toDomain(base)` resolves `/wishlist/photo/…` against the server address.
- `PICFORT_FAMILY_PATHS` (`:app`) gets `wishlist`, so Coil sends the Immich token for these photos
  (it was missing, the server already required it); `AuthScopeTest` keeps it equal to `AUTH_PREFIXES`.

## Tests

- Server (`test_wishlist.WishlistPhotoTest`): upload → served privately, 401 without token; a new
  photo / URL / delete removes the old file; empty, non-image, unknown item, bad file name.
- Android: `WishlistRepositoryTest` (upload replaces the image, `keepImage`, relative URL resolved),
  `AuthScopeTest`.
- Device (test server): Choose photo → Save → the card shows the photo (checked 2026-11-18).

## Done when

- A wishlist item can get its image from the camera or the gallery, and every family phone sees it.
