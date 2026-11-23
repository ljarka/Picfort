"""Small Immich API client used by the slideshow."""

import requests

# What `ImmichClient.check` finds (T-086), reported in /status as "immich" and logged at start.
IMMICH_OK = "ok"
IMMICH_UNREACHABLE = "unreachable"  # nothing answers at IMMICH_URL
IMMICH_NOT_IMMICH = "not_immich"  # something answers, but not Immich
IMMICH_KEY_REJECTED = "key_rejected"  # Immich refuses IMMICH_API_KEY, or it lacks album.read


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
