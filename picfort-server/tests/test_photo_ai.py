import os
import tempfile
import unittest
from datetime import datetime

import requests

from photo_ai import (
    ALL_AI_TAGS,
    MAX_ATTEMPTS,
    ImmichTagger,
    PhotoAiConfig,
    PhotoAiStore,
    PhotoAiWorker,
    in_active_hours,
    parse_active_hours,
    tags_for_insight,
)


class FakeImmichForAi:
    def __init__(self, pages=None, forbid_tags=False, missing_previews=()):
        self.pages = pages or {}
        self.forbid_tags = forbid_tags
        self.missing_previews = set(missing_previews)
        self.tagged = []
        self.untagged = []
        self.pages_read = []
        self.tag_generation = 0
        self.deleted_generations = set()

    def recent_images(self, page=1, size=100):
        self.pages_read.append(page)
        items = self.pages.get(page, [])
        return items, (page + 1) in self.pages

    def preview_bytes(self, asset_id):
        if asset_id in self.missing_previews:
            raise http_error(404)
        return b"jpeg-" + asset_id.encode()

    def upsert_tags(self, names):
        if self.forbid_tags:
            raise http_error(403)
        self.tag_generation += 1
        return {name: f"tid{self.tag_generation}:{name}" for name in names}

    def tag_assets(self, tag_ids, asset_ids):
        if any(int(t.split(":")[0].removeprefix("tid")) in self.deleted_generations for t in tag_ids):
            raise http_error(400)
        self.tagged.append((list(tag_ids), list(asset_ids)))

    def untag_asset(self, tag_id, asset_id):
        self.untagged.append((tag_id, asset_id))


def http_error(status):
    resp = requests.Response()
    resp.status_code = status
    return requests.HTTPError(response=resp)


class FakeAnalyzer:
    def __init__(self, responses):
        self.responses = responses
        self.calls = 0

    def analyze(self, image_bytes):
        self.calls += 1
        key = image_bytes.decode().removeprefix("jpeg-")
        response = self.responses[key]
        if isinstance(response, Exception):
            raise response
        return response


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
        self.assertIn(("tid1:ai/cleanup/blurry", "a1"), immich.untagged)
        self.assertEqual(["a2"], [r["asset_id"] for r in self.store.list_cleanup("all")])
        # A kept photo may go back on the frame; a kept receipt stays off it.
        self.assertEqual({"a2"}, self.store.slideshow_excluded_ids())

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

    def test_one_failing_photo_does_not_block_the_rest(self):
        good = {"category": "photo", "quality": 4, "issues": []}
        immich = FakeImmichForAi(pages={1: [{"id": "bad"}, {"id": "gone"}, {"id": "ok"}]}, missing_previews={"gone"})
        analyzer = FakeAnalyzer({"bad": ValueError("invalid JSON"), "ok": good})
        cfg = PhotoAiConfig(ollama_url="http://x", ollama_model="m", batch_size=10)
        worker = PhotoAiWorker(cfg, self.store, immich, analyzer, ImmichTagger(immich))

        self.assertEqual(1, worker.run_batch())
        self.assertIsNotNone(worker.last_error)
        for _ in range(MAX_ATTEMPTS - 1):
            self.assertEqual(0, worker.run_batch())
        calls = analyzer.calls
        # After MAX_ATTEMPTS both failing photos are skipped for good.
        self.assertEqual(0, worker.run_batch())
        self.assertEqual(calls, analyzer.calls)
        self.assertEqual({"bad", "gone", "ok"}, self.store.existing_ids(["bad", "gone", "ok"]))

    def test_ollama_down_stops_batch_without_blaming_photos(self):
        immich = FakeImmichForAi(pages={1: [{"id": "a1"}, {"id": "a2"}]})
        analyzer = FakeAnalyzer({"a1": requests.ConnectionError("refused"), "a2": {"category": "photo"}})
        cfg = PhotoAiConfig(ollama_url="http://x", ollama_model="m", batch_size=10)
        worker = PhotoAiWorker(cfg, self.store, immich, analyzer, ImmichTagger(immich))

        self.assertEqual(0, worker.run_batch())
        self.assertEqual(1, analyzer.calls)
        self.assertIn("refused", worker.last_error)
        self.assertEqual(set(), self.store.existing_ids(["a1", "a2"]))

    def test_deep_scan_reaches_photos_beyond_max_scan_pages(self):
        from photo_ai import MAX_SCAN_PAGES
        pages = {p: [{"id": f"p{p}-{i}"} for i in range(100)] for p in range(1, MAX_SCAN_PAGES + 6)}
        for p in range(1, MAX_SCAN_PAGES + 1):
            for item in pages[p]:
                self.store.save(item["id"], "m", {"category": "photo"})
        immich = FakeImmichForAi(pages=pages)
        analyzer = FakeAnalyzer({item["id"]: {"category": "photo"} for p in pages.values() for item in p})
        cfg = PhotoAiConfig(ollama_url="http://x", ollama_model="m", batch_size=5)
        worker = PhotoAiWorker(cfg, self.store, immich, analyzer, ImmichTagger(immich))

        self.assertEqual(0, worker.run_batch())  # first batch scans 1..MAX_SCAN_PAGES, all analyzed
        self.assertEqual(5, worker.run_batch())  # second resumes past them
        self.assertTrue(self.store.existing_ids([f"p{MAX_SCAN_PAGES + 1}-0"]))
        self.assertLessEqual(len(immich.pages_read), 2 * MAX_SCAN_PAGES)

    def test_tagger_recreates_tags_deleted_in_immich(self):
        immich = FakeImmichForAi()
        tagger = ImmichTagger(immich)
        self.assertEqual(["ai/type/nature"], tagger.apply("a1", ["ai/type/nature"]))
        immich.deleted_generations.add(1)  # someone deleted the ai/* tags in Immich
        self.assertEqual(["ai/type/nature"], tagger.apply("a2", ["ai/type/nature"]))
        self.assertEqual((["tid2:ai/type/nature"], ["a2"]), immich.tagged[-1])

    def test_invalid_env_values_fall_back(self):
        old = dict(os.environ)
        try:
            os.environ.update(OLLAMA_BATCH_SIZE="ten", OLLAMA_INTERVAL_MINUTES="", OLLAMA_ACTIVE_HOURS="23-7am")
            cfg = PhotoAiConfig.from_env()
            self.assertEqual((10, 30, None), (cfg.batch_size, cfg.interval_minutes, cfg.active_hours))
        finally:
            os.environ.clear()
            os.environ.update(old)

    def test_migrates_v2_database(self):
        import sqlite3
        path = os.path.join(self.tmp.name, "v2.db")
        conn = sqlite3.connect(path)
        conn.executescript("""
            CREATE TABLE photo_insights (asset_id TEXT PRIMARY KEY, model TEXT NOT NULL, scene TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT 'photo', is_nature INTEGER NOT NULL DEFAULT 0,
                is_sensitive INTEGER NOT NULL DEFAULT 0, quality INTEGER NOT NULL DEFAULT 3,
                issues TEXT NOT NULL DEFAULT '[]', frame_worthy INTEGER NOT NULL DEFAULT 0,
                cleanup_candidate INTEGER NOT NULL DEFAULT 0, cleanup_reason TEXT NOT NULL DEFAULT '',
                dismissed INTEGER NOT NULL DEFAULT 0, tags_applied TEXT NOT NULL DEFAULT '[]', analyzed_at TEXT NOT NULL);
            INSERT INTO photo_insights (asset_id, model, analyzed_at) VALUES ('old', 'm', '2026-11-28T00:00:00Z');
            PRAGMA user_version = 2;
        """)
        conn.close()
        store = PhotoAiStore(path)
        self.assertIsNone(store.migration_error)
        self.assertEqual({"old"}, store.existing_ids(["old"]))
        self.assertEqual(1, store.record_failure("x", "boom"))
        self.assertEqual(1, store.scan_page())

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

    def test_sensitive_photos_never_reach_the_frame(self):
        from datetime import date
        from helpers import FakeImmich
        from slideshow import Picker, SlideshowSettings

        self.store.save("priv", "m", {"category": "photo", "quality": 5, "is_sensitive": True})
        immich = FakeImmich(library=["priv", "ok"], album={"a": ["priv", "ok"]}, by_day={"2025-11-28": ["priv", "ok"]})
        picker = Picker(immich, photo_ai=self.store)
        today = date(2026, 11, 28)
        for s in (
            SlideshowSettings(mode="random", filter_photos=False),
            SlideshowSettings(mode="album", album_id="a"),
            SlideshowSettings(mode="memories", filter_photos=False),
        ):
            self.assertEqual({"ok"}, {picker.next(s, today) for _ in range(10)}, s.mode)

    def test_best_only_memories_keep_the_day(self):
        from datetime import date
        from helpers import FakeImmich
        from slideshow import Picker, SlideshowSettings

        self.store.save("best-other-day", "m", {"category": "photo", "quality": 5, "frame_worthy": True})
        picker = Picker(FakeImmich(by_day={"2025-11-28": ["today"]}), photo_ai=self.store)
        s = SlideshowSettings(mode="memories", best_only=True)
        self.assertEqual("today", picker.next(s, date(2026, 11, 28)))


if __name__ == "__main__":
    unittest.main()
