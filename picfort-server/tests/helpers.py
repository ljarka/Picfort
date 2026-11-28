import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


class FakeImmich:
    def __init__(self, album=None, library=None, by_day=None, non_photos=None, documents=None):
        self.album = album or {}
        self.library = library or []
        self.by_day = by_day or {}
        self.non_photos = set(non_photos or ())
        self.documents = set(documents or ())
        self.calls = []
        self.health = "ok"

    def random_images(self, size=50, album_id=None, filter_photos=False):
        self.calls.append(("random", album_id, filter_photos))
        items = list(self.album.get(album_id, []) if album_id else self.library)
        if filter_photos:
            items = [i for i in items if i not in self.non_photos]
        return items[:size]

    def images_taken_between(self, after, before, size=100, filter_photos=False):
        self.calls.append(("between", after[:10], filter_photos))
        items = list(self.by_day.get(after[:10], []))
        if filter_photos:
            items = [i for i in items if i not in self.non_photos]
        return items

    def document_ids(self):
        self.calls.append(("documents", None))
        return set(self.documents)

    def albums(self):
        return [{"id": k, "name": f"Album {k}"} for k in self.album]

    def check(self):
        self.calls.append(("check", None))
        return self.health


class Clock:
    def __init__(self, t=1_700_000_000.0):
        self.t = t

    def __call__(self):
        return self.t

    def advance(self, seconds):
        self.t += seconds
