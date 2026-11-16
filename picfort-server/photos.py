"""Shared photo validation for Wall posts and Receipts."""

import re
from io import BytesIO

from PIL import Image, UnidentifiedImageError

try:  # Optional: lets Pillow open HEIC/HEIF photos from iPhones.
    from pillow_heif import register_heif_opener

    register_heif_opener()
except ImportError:
    pass

from store import StoreError

MAX_PHOTO_BYTES = 10 * 1024 * 1024
# Pillow format -> file extension. HEIF only opens when pillow-heif is installed.
PHOTO_FORMATS = {"JPEG": "jpg", "PNG": "png", "HEIF": "heic"}
PHOTO_NAME_RE = re.compile(r"^[0-9a-f]{32}\.(jpg|png|heic)$")
# EXIF orientations that rotate by 90°, so width and height swap when shown upright.
ROTATED_ORIENTATIONS = {5, 6, 7, 8}


def inspect_photo(data: bytes, error_cls=StoreError):
    """Return (extension, upright width, upright height); raise error_cls for invalid or oversized photos."""
    if len(data) > MAX_PHOTO_BYTES:
        raise error_cls("The photo is larger than 10 MB", 413)
    try:
        with Image.open(BytesIO(data)) as img:
            fmt = img.format
            width, height = img.size
            orientation = img.getexif().get(0x0112)
    except (UnidentifiedImageError, OSError):
        raise error_cls("The photo must be JPEG, PNG or HEIC", 415)
    if fmt not in PHOTO_FORMATS:
        raise error_cls("The photo must be JPEG, PNG or HEIC", 415)
    if orientation in ROTATED_ORIENTATIONS:
        width, height = height, width
    return PHOTO_FORMATS[fmt], width, height
