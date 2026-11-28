import os
import tempfile
import unittest
from datetime import datetime

import requests

from photo_ai import (
    ALL_AI_TAGS,
    ImmichTagger,
    PhotoAiConfig,
    PhotoAiStore,
    PhotoAiWorker,
    in_active_hours,
    parse_active_hours,
    tags_for_insight,
)


class FakeImmichForAi:
    def __init__(self, pages=None, forbid_tags=False):
        self.pages = pages or {}
        self.forbid_tags = forbid_tags
        self.tagged = []
        self.untagged = []

    def recent_images(self, page=1, size=100):
        items = self.pages.get(page, [])
        return items, (page + 1) in self.pages

    def preview_bytes(self, asset_id):
        return b"jpeg-" + asset_id.encode()

    def upsert_tags(self, names):
        if self.forbid_tags:
            resp = requests.Response()
            resp.status_code = 403
            raise requests.HTTPError(response=resp)
        return {name: f"tid:{name}" for name in names}

    def tag_assets(self, tag_ids, asset_ids):
        self.tagged.append((list(tag_ids), list(asset_ids)))

    def untag_asset(self, tag_id, asset_id):
        self.untagged.append((tag_id, asset_id))


class FakeAnalyzer:
    def __init__(self, responses):
        self.responses = responses
        self.calls = 0

    def analyze(self, image_bytes):
        self.calls += 1
        key = image_bytes.decode().removeprefix("jpeg-")
        return self.responses[key]


class TagMappingTest(unittest.TestCase):
    def test_ordinary_photo_gets_no_tags(self):
        self.assertEqual(
            [],
            tags_for_insight({"category": "photo", "quality": 3, "issues": [], "is_nature": False, "frame_worthy": False}),
        )

    def test_cleanup_nature_and_frame_worthy_tags(self):
        self.assertEqual(
            ["ai/cleanup/low-quality", "ai/cleanup/blurry", "ai/cleanup/closed-eyes"],
            tags_for_insight({"category": "photo", "quality": 2, "issues": ["blurry", "closed_eyes"]}),
        )
        self.assertEqual(
            ["ai/type/nature", "ai/best/frame-worthy"],
            tags_for_insight({"category": "photo", "quality": 5, "is_nature": True, "frame_worthy": True}),
        )
        self.assertEqual(
            ["ai/type/kids-art"],
            tags_for_insight({"category": "kids_art", "quality": 4, "frame_worthy": True}),
        )
        self.assertEqual(
            ["ai/type/receipt"],
            tags_for_insight({"category": "receipt", "quality": 4}),
        )
        # Sensitive photos get ai/privacy/sensitive and NEVER ai/best/frame-worthy.
        self.assertEqual(
            ["ai/type/document", "ai/privacy/sensitive"],
            tags_for_insight({"category": "document", "quality": 5, "is_sensitive": True, "frame_worthy": True}),
        )
        self.assertEqual(
            ["ai/privacy/sensitive"],
            tags_for_insight({"category": "photo", "quality": 5, "is_sensitive": True, "frame_worthy": True}),
        )


class ActiveHoursTest(unittest.TestCase):
    def test_parses_and_checks_midnight_wrap(self):
        w = parse_active_hours("23-7")
        self.assertEqual((23, 7), w)
        self.assertTrue(in_active_hours(w, datetime(2026, 11, 28, 23, 30)))
        self.assertTrue(in_active_hours(w, datetime(2026, 11, 28, 6, 0)))
        self.assertFalse(in_active_hours(w, datetime(2026, 11, 28, 12, 0)))
        self.assertTrue(in_active_hours(None, datetime(2026, 11, 28, 12, 0)))


class PhotoAiWorkerAndStoreTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = PhotoAiStore(os.path.join(self.tmp.name, "photo_ai.db"))

    def tearDown(self):
        self.tmp.cleanup()

    def test_worker_analyzes_tags_and_supports_dismiss_and_delete(self):
        immich = FakeImmichForAi(pages={1: [{"id": "a1"}, {"id": "a2"}, {"id": "a3"}]})
        analyzer = FakeAnalyzer({
            "a1": {"scene": "blurry view", "category": "photo", "is_nature": False, "quality": 2,
                   "issues": ["blurry"], "frame_worthy": False, "cleanup_candidate": True, "cleanup_reason": "blurry"},
            "a2": {"scene": "store receipt", "category": "receipt", "is_nature": False, "quality": 4,
                   "issues": [], "frame_worthy": False, "cleanup_candidate": True, "cleanup_reason": "receipt"},
            "a3": {"scene": "mountain lake", "category": "photo", "is_nature": True, "quality": 5,
                   "issues": [], "frame_worthy": True, "cleanup_candidate": False, "cleanup_reason": ""},
        })
        tagger = ImmichTagger(immich, enabled=True)
        cfg = PhotoAiConfig(ollama_url="http://localhost:11434", ollama_model="gemma4:31b-mlx", batch_size=10)
        worker = PhotoAiWorker(cfg, self.store, immich, analyzer, tagger)

        self.assertEqual(3, worker.run_batch())
        # Second run skips already analyzed assets.
        self.assertEqual(0, worker.run_batch())
        self.assertEqual(3, analyzer.calls)

        self.assertEqual({"analyzed_count": 3, "cleanup_count": 2}, self.store.stats())
        self.assertEqual(["a1", "a2"], [r["asset_id"] for r in self.store.list_cleanup("all")])
        self.assertEqual(["a1"], [r["asset_id"] for r in self.store.list_cleanup("low_quality")])
        self.assertEqual(["a2"], [r["asset_id"] for r in self.store.list_cleanup("documents")])
        self.assertEqual({"a1", "a2"}, self.store.slideshow_excluded_ids())

        # Dismiss a1 removes cleanup tags in Immich and hides it from cleanup list.
        dismissed = self.store.dismiss("a1")
        tagger.remove_cleanup_tags("a1", dismissed["tags_applied"])
        self.assertIn(("tid:ai/cleanup/blurry", "a1"), immich.untagged)
        self.assertEqual(["a2"], [r["asset_id"] for r in self.store.list_cleanup("all")])

        # Deleting a2 removes it from the store.
        self.assertTrue(self.store.delete("a2"))
        self.assertEqual(0, self.store.stats()["cleanup_count"])

    def test_tagger_degrades_gracefully_on_403(self):
        immich = FakeImmichForAi(pages={1: [{"id": "a1"}]}, forbid_tags=True)
        analyzer = FakeAnalyzer({
            "a1": {"scene": "blurry shot", "category": "photo", "is_nature": False, "quality": 1,
                   "issues": ["blurry"], "frame_worthy": False, "cleanup_candidate": True, "cleanup_reason": "blurry"},
        })
        tagger = ImmichTagger(immich, enabled=True)
        cfg = PhotoAiConfig(ollama_url="http://localhost:11434", ollama_model="gemma4:e4b-mlx", batch_size=5)
        worker = PhotoAiWorker(cfg, self.store, immich, analyzer, tagger)

        self.assertEqual(1, worker.run_batch())
        self.assertEqual(1, self.store.stats()["cleanup_count"])
        self.assertEqual([], immich.tagged)

    def test_frame_worthy_ids_and_best_only_picker(self):
        from datetime import date
        from helpers import FakeImmich
        from slideshow import Picker, SlideshowSettings

        self.store.save("good1", "m", {"category": "photo", "quality": 5, "frame_worthy": True})
        self.store.save("meh", "m", {"category": "photo", "quality": 3, "frame_worthy": True})
        self.store.save("doc", "m", {"category": "document", "quality": 5, "frame_worthy": True})
        self.store.save("priv", "m", {"category": "photo", "quality": 5, "frame_worthy": True, "is_sensitive": True})
        self.assertEqual(["good1"], self.store.frame_worthy_ids())
        self.assertIn("priv", self.store.slideshow_excluded_ids())

        picker = Picker(FakeImmich(library=["other1", "other2"]), photo_ai=self.store)
        s = SlideshowSettings(mode="random", best_only=True)
        self.assertEqual("good1", picker.next(s, date(2026, 11, 28)))


if __name__ == "__main__":
    unittest.main()
