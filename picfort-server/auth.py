"""Who is calling: the app sends its Immich token, and Immich says which user it belongs to (ADR 0006).

Tokens are never stored: the cache is in memory, keyed by a hash of the token.
"""

import hashlib
import threading
import time
from dataclasses import dataclass

import requests

CACHE_SECONDS = 5 * 60
MAX_CACHED = 256


@dataclass(frozen=True)
class User:
    id: str
    name: str


class AuthError(Exception):
    """401: no token, or Immich rejected it."""


class AuthUnavailable(Exception):
    """502: Immich couldn't be asked. Not the caller's fault, so not a 401 (the app must not log out)."""


def immich_lookup(base_url: str, timeout: float = 10.0):
    """Asks Immich who owns a token. Returns a User, or None if Immich rejects the token."""
    url = base_url.rstrip("/") + "/api/users/me"

    def lookup(token: str):
        try:
            r = requests.get(url, headers={"Authorization": f"Bearer {token}"}, timeout=timeout)
        except requests.RequestException as e:
            raise AuthUnavailable(f"Could not reach Immich: {e}") from e
        if r.status_code in (401, 403):
            return None
        if not r.ok:
            raise AuthUnavailable(f"Immich answered {r.status_code}")
        body = r.json()
        return User(id=body["id"], name=body.get("name") or body.get("email") or body["id"])

    return lookup


class Authenticator:
    def __init__(self, lookup, clock=time.monotonic, ttl=CACHE_SECONDS):
        self.lookup = lookup
        self.clock = clock
        self.ttl = ttl
        self._cache = {}  # sha256(token) -> (User, expires)
        self._lock = threading.Lock()

    def user(self, authorization_header) -> User:
        """The user behind an `Authorization: Bearer <token>` header; raises AuthError / AuthUnavailable."""
        scheme, _, token = (authorization_header or "").partition(" ")
        token = token.strip()
        if scheme.lower() != "bearer" or not token:
            raise AuthError("Log in to Immich in the app (missing Authorization: Bearer token)")
        key = hashlib.sha256(token.encode()).hexdigest()
        now = self.clock()
        with self._lock:
            hit = self._cache.get(key)
            if hit and hit[1] > now:
                return hit[0]
        user = self.lookup(token)  # outside the lock: it's a network call
        if user is None:
            with self._lock:
                self._cache.pop(key, None)
            raise AuthError("Immich rejected the token")
        with self._lock:
            if len(self._cache) >= MAX_CACHED:
                self._cache = {k: v for k, v in self._cache.items() if v[1] > now}
                if len(self._cache) >= MAX_CACHED:
                    self._cache.clear()
            self._cache[key] = (user, now + self.ttl)
        return user


def immich_partners(base_url: str, timeout: float = 10.0, ttl: float = 600.0, clock=time.monotonic):
    """Ids of users who share their library with the token's owner (Immich partners), cached for
    [ttl] seconds per token hash (T-104, T-108: photo AI labels a member may see)."""
    url = base_url.rstrip("/") + "/api/partners"
    cache: dict[str, tuple[float, set[str]]] = {}  # sha256(token) -> (expires, partner_ids)
    lock = threading.Lock()

    def lookup(authorization: str) -> set[str]:
        scheme, _, token = (authorization or "").partition(" ")
        token = token.strip()
        if scheme.lower() != "bearer" or not token:
            return set()
        key = hashlib.sha256(token.encode()).hexdigest()
        now = clock()
        with lock:
            hit = cache.get(key)
            if hit and hit[0] > now:
                return hit[1]
        r = requests.get(url, params={"direction": "shared-with"},
                         headers={"Authorization": f"Bearer {token}"}, timeout=timeout)
        r.raise_for_status()
        ids = {p["id"] for p in r.json() if isinstance(p, dict) and p.get("id")}
        with lock:
            if len(cache) >= MAX_CACHED:
                for k in [k for k, v in cache.items() if v[0] <= now]:
                    cache.pop(k, None)
                if len(cache) >= MAX_CACHED:
                    cache.clear()
            cache[key] = (now + ttl, ids)
        return ids

    lookup._cache = cache  # exposed for tests
    return lookup
