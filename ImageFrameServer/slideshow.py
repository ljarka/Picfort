"""Slideshow: the frame picks its own photos on a schedule.

Modes:
  off       only shows what the app sends
  album     random photos from one album
  memories  "on this day": photos taken on today's date in earlier years
  random    random photos from the whole library
"""

import json
import logging
import os
import random
import tempfile
import threading
import time
from collections import deque
from dataclasses import asdict, dataclass, field
from datetime import date, datetime, timedelta

log = logging.getLogger("slideshow")

MODES = ("off", "album", "memories", "random")
INTERVALS = (15, 30, 60, 180, 360, 720, 1440)  # minutes; e-paper shouldn't refresh constantly


@dataclass
class SlideshowSettings:
    mode: str = "off"
    album_id: str | None = None
    interval_minutes: int = 60
    # Quiet hours (local time, 0-23): no changes from quiet_start until quiet_end. None = off.
    quiet_start: int | None = None
    quiet_end: int | None = None

    @staticmethod
    def from_dict(d: dict) -> "SlideshowSettings":
        s = SlideshowSettings(
            mode=d.get("mode", "off"),
            album_id=d.get("album_id") or None,
            interval_minutes=int(d.get("interval_minutes", 60)),
            quiet_start=d.get("quiet_start"),
            quiet_end=d.get("quiet_end"),
        )
        s.validate()
        return s

    def validate(self):
        if self.mode not in MODES:
            raise ValueError(f"mode must be one of {', '.join(MODES)}")
        if self.mode == "album" and not self.album_id:
            raise ValueError("album mode needs album_id")
        if self.interval_minutes not in INTERVALS:
            raise ValueError(f"interval_minutes must be one of {INTERVALS}")
        quiet = (self.quiet_start, self.quiet_end)
        if (quiet[0] is None) != (quiet[1] is None):
            raise ValueError("set both quiet_start and quiet_end, or neither")
        for h in quiet:
            if h is not None and not (isinstance(h, int) and 0 <= h <= 23):
                raise ValueError("quiet hours must be whole hours 0-23")


def in_quiet_hours(s: SlideshowSettings, now: datetime) -> bool:
    if s.quiet_start is None or s.quiet_start == s.quiet_end:
        return False
    h = now.hour
    if s.quiet_start < s.quiet_end:
        return s.quiet_start <= h < s.quiet_end
    return h >= s.quiet_start or h < s.quiet_end  # wraps midnight, e.g. 22 -> 7


def quiet_end_after(s: SlideshowSettings, now: datetime) -> datetime:
    """When the current quiet period ends."""
    end = now.replace(hour=s.quiet_end, minute=0, second=0, microsecond=0)
    return end if end > now else end + timedelta(days=1)


def on_this_day_ranges(today: date, years_back: int = 30):
    """(after, before) ISO ranges covering today's date in each earlier year."""
    for years in range(1, years_back + 1):
        year = today.year - years
        try:
            day = today.replace(year=year)
        except ValueError:  # 29 February in a non-leap year
            day = date(year, 2, 28)
        yield day.isoformat() + "T00:00:00.000Z", (day + timedelta(days=1)).isoformat() + "T00:00:00.000Z"


class SettingsStore:
    """Slideshow settings persisted as JSON, so they survive restarts."""

    def __init__(self, path: str):
        self.path = path

    def load(self) -> SlideshowSettings:
        try:
            with open(self.path) as f:
                return SlideshowSettings.from_dict(json.load(f))
        except FileNotFoundError:
            return SlideshowSettings()
        except (ValueError, OSError) as e:
            log.warning("Ignoring invalid %s: %s", self.path, e)
            return SlideshowSettings()

    def save(self, s: SlideshowSettings):
        directory = os.path.dirname(os.path.abspath(self.path))
        fd, tmp = tempfile.mkstemp(dir=directory, suffix=".tmp")
        with os.fdopen(fd, "w") as f:
            json.dump(asdict(s), f, indent=2)
        os.replace(tmp, self.path)  # atomic


@dataclass
class Picker:
    """Chooses the next photo, avoiding recent repeats."""

    immich: object
    history: deque = field(default_factory=lambda: deque(maxlen=100))
    _memories: tuple = (None, [])  # (date, ids) cache for the day

    def next(self, s: SlideshowSettings, today: date) -> str | None:
        if s.mode == "album":
            candidates = self.immich.random_images(album_id=s.album_id)
        elif s.mode == "memories":
            candidates = self._on_this_day(today) or self.immich.random_images()
        elif s.mode == "random":
            candidates = self.immich.random_images()
        else:
            return None
        if not candidates:
            return None
        fresh = [c for c in candidates if c not in self.history]
        choice = random.choice(fresh or candidates)
        self.history.append(choice)
        return choice

    def _on_this_day(self, today: date) -> list[str]:
        cached_day, ids = self._memories
        if cached_day != today:
            ids = []
            for after, before in on_this_day_ranges(today):
                ids += self.immich.images_taken_between(after, before)
            self._memories = (today, ids)
        return ids


class Slideshow:
    """Background thread that changes the photo every interval (outside quiet hours)."""

    def __init__(self, store: SettingsStore, picker: Picker, show, clock=time.time, now=None):
        self.store = store
        self.picker = picker
        self.show = show  # show(asset_id) -> True if it was displayed or accepted
        self.clock = clock
        self.now = now or (lambda: datetime.fromtimestamp(self.clock()))
        self.settings = store.load()
        self.last_change = clock()
        self.last_error = None
        self._wake = threading.Event()
        self._lock = threading.Lock()

    # -- called from HTTP handlers -------------------------------------------------

    def update(self, s: SlideshowSettings):
        with self._lock:
            changed = (s.mode, s.album_id) != (self.settings.mode, self.settings.album_id)
            self.settings = s
            self.store.save(s)
            if changed and s.mode != "off":
                self.last_change = 0  # show something from the new source right away
        self._wake.set()

    def photo_changed(self):
        """Something was shown (e.g. sent from the app): give it a full interval."""
        with self._lock:
            self.last_change = self.clock()

    def skip(self):
        """Show the next photo now."""
        with self._lock:
            self.last_change = 0
        self._wake.set()

    def next_change_at(self) -> float | None:
        """Unix time of the next automatic change, or None when the slideshow is off."""
        with self._lock:
            s = self.settings
            if s.mode == "off":
                return None
            due = max(self.last_change + s.interval_minutes * 60, self.clock())
        due_dt = datetime.fromtimestamp(due)
        if in_quiet_hours(s, due_dt):
            return quiet_end_after(s, due_dt).timestamp()
        return due

    # -- background thread ---------------------------------------------------------

    def tick(self) -> float:
        """Advance if due. Returns how many seconds to sleep before the next check."""
        with self._lock:
            s = self.settings
            if s.mode == "off":
                return 3600
            due = self.last_change + s.interval_minutes * 60
            wait = due - self.clock()
            if wait > 0:
                return min(wait, 300)
            if in_quiet_hours(s, self.now()):
                return 300
        try:
            asset_id = self.picker.next(s, self.now().date())
            if asset_id is None:
                self.last_error = "No photos found for this slideshow"
                return 600
            if self.show(asset_id):
                self.photo_changed()
                self.last_error = None
                return 1
            return 30  # frame busy or too soon: try again shortly
        except Exception as e:  # Immich down, network, …: keep running
            log.warning("Slideshow step failed: %s", e)
            self.last_error = str(e)
            return 300

    def run_forever(self):
        while True:
            delay = self.tick()
            self._wake.wait(timeout=delay)
            self._wake.clear()

    def start(self):
        threading.Thread(target=self.run_forever, daemon=True, name="slideshow").start()
