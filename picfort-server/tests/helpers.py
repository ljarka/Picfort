import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


class FakeImmich:
    def __init__(self, album=None, library=None, by_day=None):
        self.album = album or {}
        self.library = library or []
        self.by_day = by_day or {}
        self.calls = []

    def random_images(self, size=50, album_id=None):
        self.calls.append(("random", album_id))
        return list(self.album.get(album_id, []) if album_id else self.library)[:size]

    def images_taken_between(self, after, before, size=100):
        self.calls.append(("between", after[:10]))
        return list(self.by_day.get(after[:10], []))

    def albums(self):
        return [{"id": k, "name": f"Album {k}"} for k in self.album]


class Clock:
    def __init__(self, t=1_700_000_000.0):
        self.t = t

    def __call__(self):
        return self.t

    def advance(self, seconds):
        self.t += seconds
