"""Small Immich API client used by the slideshow."""

import requests


class ImmichClient:
    def __init__(self, base_url: str, api_key: str, timeout: float = 30.0):
        self.base_url = base_url.rstrip("/")
        self.headers = {"x-api-key": api_key}
        self.timeout = timeout

    def _post(self, path: str, body: dict):
        r = requests.post(f"{self.base_url}/api{path}", json=body, headers=self.headers, timeout=self.timeout)
        r.raise_for_status()
        return r.json()

    def random_images(self, size: int = 50, album_id: str | None = None) -> list[str]:
        """Random photo ids, optionally from one album."""
        body = {"size": size, "type": "IMAGE"}
        if album_id:
            body["albumIds"] = [album_id]
        return [a["id"] for a in self._post("/search/random", body)]

    def images_taken_between(self, after: str, before: str, size: int = 100) -> list[str]:
        """Photo ids taken in [after, before) (ISO timestamps)."""
        body = {"type": "IMAGE", "takenAfter": after, "takenBefore": before, "size": size}
        return [a["id"] for a in self._post("/search/metadata", body).get("assets", {}).get("items", [])]

    def albums(self) -> list[dict]:
        r = requests.get(f"{self.base_url}/api/albums", headers=self.headers, timeout=self.timeout)
        r.raise_for_status()
        return [{"id": a["id"], "name": a.get("albumName", "")} for a in r.json()]
