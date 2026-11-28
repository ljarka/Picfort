import os
import tempfile
import unittest
from datetime import datetime

from photo_ai import (
    ImmichTagger,
    PhotoAiConfig,
    PhotoAiStore,
    PhotoAiWorker,
    cluster_album_candidates,
)


def _photo(idx, day, city="", country="", make="Google", name="IMG.jpg"):
    return {
        "id": f"id-{idx}",
        "localDateTime": f"{day}T12:00:00.000Z",
        "originalFileName": name,
        "exifInfo": {"make": make, "city": city, "country": country},
    }


class FakeImmichUnassigned:
    def __init__(self, items):
        self.items = items

    def recent_images(self, page=1, size=100):
        return [], False

    def unassigned_images(self, page=1, size=100):
        return (self.items if page == 1 else []), False


class FakeAlbumAnalyzer:
    def __init__(self, title="Weekend in Zakopane", worth=True):
        self.title = title
        self.worth = worth
        self.calls = 0

    def name_album(self, cluster, scenes):
        self.calls += 1
        return self.title, self.worth


class AlbumSuggestionsTest(unittest.TestCase):
    def test_clusters_trip_by_city_and_skips_screenshots_and_small_groups(self):
        items = (
            [_photo(i, "2026-02-14", city="Zakopane", country="Poland") for i in range(7)]
            + [_photo(i + 10, "2026-02-16", city="Zakopane", country="Poland") for i in range(6)]
            + [_photo(90, "2026-02-15", city="Zakopane", name="Screenshot_1.png")]
            + [_photo(i + 50, "2026-03-01", city="Krakow") for i in range(3)]
        )
        clusters = cluster_album_candidates(items, min_photos=12)
        self.assertEqual(1, len(clusters))
        c = clusters[0]
        self.assertEqual("2026-02-14..2026-02-16|zakopane", c["cluster_key"])
        self.assertEqual(13, len(c["asset_ids"]))
        self.assertIn("13 photos", c["subtitle"])

    def test_worker_names_and_persists_suggestions_and_respects_dismiss(self):
        with tempfile.TemporaryDirectory() as d:
            store = PhotoAiStore(os.path.join(d, "photo_ai.db"))
            # Mark one photo as frame-worthy so it becomes the cover.
            store.save("id-5", "m", {"scene": "mountains snow", "category": "photo", "quality": 5, "frame_worthy": True})
            items = [_photo(i, "2026-02-14", city="Zakopane", country="Poland") for i in range(12)]
            immich = FakeImmichUnassigned(items)
            analyzer = FakeAlbumAnalyzer("Winter in Zakopane", worth=True)
            cfg = PhotoAiConfig(ollama_url="http://localhost:11434", ollama_model="gemma4:31b-mlx")
            worker = PhotoAiWorker(cfg, store, immich, analyzer, ImmichTagger(immich, enabled=False),
                                   now=lambda: datetime(2026, 2, 20, 23, 0))

            self.assertEqual(1, worker.refresh_album_suggestions())
            self.assertEqual(1, analyzer.calls)
            suggestions = store.list_album_suggestions()
            self.assertEqual(1, len(suggestions))
            self.assertEqual("Winter in Zakopane", suggestions[0]["title"])
            self.assertEqual("id-5", suggestions[0]["cover_asset_id"])
            self.assertEqual(12, len(suggestions[0]["asset_ids"]))

            # Dismissing hides it and prevents re-suggesting the same cluster.
            key = suggestions[0]["cluster_key"]
            self.assertTrue(store.set_album_suggestion_status(key, "dismissed"))
            self.assertEqual([], store.list_album_suggestions())
            self.assertEqual(0, worker.refresh_album_suggestions())
            self.assertEqual(1, analyzer.calls)


if __name__ == "__main__":
    unittest.main()
