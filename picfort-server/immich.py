"""Small Immich API client used by the slideshow."""

import logging

import requests

log = logging.getLogger("immich")

# What `ImmichClient.check` finds (T-086), reported in /status as "immich" and logged at start.
IMMICH_OK = "ok"
IMMICH_UNREACHABLE = "unreachable"  # nothing answers at IMMICH_URL
IMMICH_NOT_IMMICH = "not_immich"  # something answers, but not Immich
IMMICH_KEY_REJECTED = "key_rejected"  # Immich refuses IMMICH_API_KEY, or it lacks album.read

# OCR keywords used to identify receipts and documents in Immich (T-088).
DOCUMENT_OCR_TERMS = ("PARAGON", "NIP", "FAKTURA", "SUMA PLN", "VAT", "UMOWA", "PESEL")


def is_likely_non_photo(asset: dict) -> bool:
    """True for screenshots and saved images without camera EXIF (unless faces are recognized)."""
    if asset.get("people"):
        return False
    name = (asset.get("originalFileName") or "").lower()
    if "screenshot" in name:
        return True
    exif = asset.get("exifInfo") or {}
    return not (exif.get("make") or exif.get("model"))


class ImmichClient:
    def __init__(self, base_url: str, api_key: str, timeout: float = 30.0):
        self.base_url = base_url.rstrip("/")
        self.headers = {"x-api-key": api_key}
        self.timeout = timeout

    def _post(self, path: str, body: dict):
        r = requests.post(f"{self.base_url}/api{path}", json=body, headers=self.headers, timeout=self.timeout)
        r.raise_for_status()
        return r.json()

    def random_images(self, size: int = 50, album_id: str | None = None, filter_photos: bool = False,
                      keep: set[str] | frozenset = frozenset()) -> list[str]:
        """Random photo ids, optionally from one album and filtered to camera photos.

        `keep`: ids the photo AI saw as real photos; they pass the filter's guesses (T-099).
        """
        body: dict = {"size": size, "type": "IMAGE"}
        if album_id:
            body["albumIds"] = [album_id]
        if filter_photos:
            body["withExif"] = True
            body["withPeople"] = True
        items = self._post("/search/random", body)
        if filter_photos:
            items = [a for a in items if a.get("id") in keep or not is_likely_non_photo(a)]
        return [a["id"] for a in items]

    def images_taken_between(self, after: str, before: str, size: int = 100, filter_photos: bool = False,
                             keep: set[str] | frozenset = frozenset()) -> list[str]:
        """Photo ids taken in [after, before) (ISO timestamps)."""
        body: dict = {"type": "IMAGE", "takenAfter": after, "takenBefore": before, "size": size}
        if filter_photos:
            body["withExif"] = True
            body["withPeople"] = True
        items = self._post("/search/metadata", body).get("assets", {}).get("items", [])
        if filter_photos:
            items = [a for a in items if a.get("id") in keep or not is_likely_non_photo(a)]
        return [a["id"] for a in items]

    def document_ids(self, terms: tuple[str, ...] = DOCUMENT_OCR_TERMS, size: int = 1000) -> set[str]:
        """Photo ids matching document/receipt OCR terms via Immich metadata search (T-088)."""
        ids: set[str] = set()
        for term in terms:
            try:
                body = {"ocr": term, "type": "IMAGE", "size": size}
                items = self._post("/search/metadata", body).get("assets", {}).get("items", [])
                ids.update(a["id"] for a in items if "id" in a)
            except requests.RequestException as e:
                log.warning("OCR document search for %r failed: %s", term, e)
                break
        return ids

    def albums(self) -> list[dict]:
        r = requests.get(f"{self.base_url}/api/albums", headers=self.headers, timeout=self.timeout)
        r.raise_for_status()
        return [{"id": a["id"], "name": a.get("albumName", "")} for a in r.json()]

    def recent_images(self, page: int = 1, size: int = 100) -> tuple[list[dict], bool]:
        """Newest images from Immich metadata search, with whether another page exists (T-089)."""
        body = {"type": "IMAGE", "page": page, "size": size, "order": "desc"}
        assets = self._post("/search/metadata", body).get("assets", {})
        return assets.get("items", []), bool(assets.get("nextPage"))

    def unassigned_images(self, page: int = 1, size: int = 100) -> tuple[list[dict], bool]:
        """Newest images not in any album, with EXIF location metadata for album suggestions (T-098)."""
        body = {"type": "IMAGE", "isNotInAlbum": True, "withExif": True, "page": page, "size": size, "order": "desc"}
        assets = self._post("/search/metadata", body).get("assets", {})
        return assets.get("items", []), bool(assets.get("nextPage"))

    def preview_bytes(self, asset_id: str) -> bytes:
        """Preview JPEG bytes for an asset (smaller than original, ideal for Vision LLM, T-089)."""
        r = requests.get(
            f"{self.base_url}/api/assets/{asset_id}/thumbnail",
            params={"size": "preview"},
            headers=self.headers,
            timeout=self.timeout,
        )
        r.raise_for_status()
        return r.content

    def upsert_tags(self, names: list[str]) -> dict[str, str]:
        """Create/fetch hierarchical tags in Immich; returns {tag_value: tag_id} (T-089)."""
        r = requests.put(f"{self.base_url}/api/tags", json={"tags": names}, headers=self.headers, timeout=self.timeout)
        r.raise_for_status()
        return {t["value"]: t["id"] for t in r.json() if "value" in t and "id" in t}

    def tag_assets(self, tag_ids: list[str], asset_ids: list[str]) -> None:
        """Assign tags to assets in Immich (requires tag.asset permission, T-089)."""
        if not tag_ids or not asset_ids:
            return
        r = requests.put(
            f"{self.base_url}/api/tags/assets",
            json={"tagIds": tag_ids, "assetIds": asset_ids},
            headers=self.headers,
            timeout=self.timeout,
        )
        r.raise_for_status()

    def untag_asset(self, tag_id: str, asset_id: str) -> None:
        """Remove a tag from an asset in Immich (T-089)."""
        r = requests.delete(
            f"{self.base_url}/api/tags/{tag_id}/assets",
            json={"ids": [asset_id]},
            headers=self.headers,
            timeout=self.timeout,
        )
        r.raise_for_status()

    def server_version(self, timeout: float = 3.0) -> str:
        """Immich's version, e.g. "v3.3.5" (public endpoint, T-095)."""
        r = requests.get(f"{self.base_url}/api/server/version", headers=self.headers, timeout=timeout)
        r.raise_for_status()
        v = r.json()
        return f"v{v.get('major')}.{v.get('minor')}.{v.get('patch')}"

    def asset_statistics(self, timeout: float = 5.0) -> dict:
        """Library size {"images", "videos", "total"} (needs asset.statistics on the key, T-095)."""
        r = requests.get(f"{self.base_url}/api/assets/statistics", headers=self.headers, timeout=timeout)
        r.raise_for_status()
        s = r.json()
        return {"images": s.get("images"), "videos": s.get("videos"), "total": s.get("total")}

    def check(self, timeout: float = 3.0) -> str:
        """Whether IMMICH_URL is Immich and accepts IMMICH_API_KEY; one of the IMMICH_* values."""
        try:
            ping = requests.get(f"{self.base_url}/api/server/ping", timeout=timeout)
        except requests.RequestException:
            return IMMICH_UNREACHABLE
        if ping.status_code != 200 or "pong" not in ping.text:
            return IMMICH_NOT_IMMICH
        try:
            albums = requests.get(f"{self.base_url}/api/albums", headers=self.headers, timeout=timeout)
        except requests.RequestException:
            return IMMICH_UNREACHABLE
        if albums.status_code in (401, 403):
            return IMMICH_KEY_REJECTED
        return IMMICH_OK


# What the server log says at start for each result: the problem and the fix.
IMMICH_CHECK_LOG = {
    IMMICH_OK: "Immich at %s: connected, the API key works.",
    IMMICH_UNREACHABLE: "Can't reach Immich at %s. Check IMMICH_URL in .env: inside Docker, "
                        "localhost is the container itself; use the machine's address (INSTALL.md, step 3).",
    IMMICH_NOT_IMMICH: "%s answers, but it isn't Immich. Check the address and port in IMMICH_URL "
                       "(Immich's default port is 2283).",
    IMMICH_KEY_REJECTED: "Immich at %s rejects IMMICH_API_KEY, or the key lacks album.read. "
                         "Create a new key with asset.read, asset.view, asset.download and album.read "
                         "(INSTALL.md, step 2).",
}
