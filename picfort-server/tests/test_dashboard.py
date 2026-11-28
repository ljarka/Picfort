import logging
import os
import tempfile
import unittest

from helpers import Clock, FakeImmich

import frame
from app import FrameController, create_app
from dashboard import RECENT_LOGS, RecentLogHandler
from events import EventStore
from photo_ai import PhotoAiConfig, PhotoAiStore, PhotoAiWorker
from slideshow import Picker, SettingsStore, Slideshow


class DashboardImmich(FakeImmich):
    def __init__(self, **kw):
        super().__init__(**kw)
        self.stats_fail = False

    def server_version(self):
        return "v3.3.5"

    def asset_statistics(self):
        if self.stats_fail:
            raise RuntimeError("403")
        return {"images": 200, "videos": 10, "total": 210}


class DashboardTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.events = EventStore(os.path.join(self.dir, "events.db"))
        self.ai = PhotoAiStore(os.path.join(self.dir, "photo_ai.db"))
        self.ai.save("a1", "m", {"category": "photo", "quality": 5, "frame_worthy": True, "scene": "Secret scene"})
        self.ai.save("a2", "m", {"category": "receipt", "quality": 3, "cleanup_candidate": True})
        self.ai.save("a3", "m", {"category": "photo", "quality": 4, "is_sensitive": True})
        for _ in range(3):
            self.ai.record_failure("bad", "boom")
        open(os.path.join(self.dir, "events.db.v1.bak"), "wb").close()
        self.immich = DashboardImmich()
        self.worker = PhotoAiWorker(
            PhotoAiConfig(ollama_url="http://ollama", ollama_model="gemma", active_hours=(23, 7)),
            self.ai, self.immich, analyzer=None, tagger=None,
        )
        self.worker.last_error = "Ollama timeout"

    def client(self, api_key="s3cret"):
        cfg = frame.Config(immich_url="http://immich", immich_api_key="k",
                           preview_path=os.path.join(self.dir, "p.png"))
        controller = FrameController(cfg, min_interval=0, display=lambda _: None, prepare=lambda a: a)
        slideshow = Slideshow(SettingsStore(os.path.join(self.dir, "s.json")), Picker(self.immich),
                              controller.try_show)
        app = create_app(controller, slideshow, self.immich, api_key=api_key, events=self.events,
                         photo_ai=self.ai, photo_ai_worker=self.worker, clock=Clock(), data_dir=self.dir)
        return app.test_client()

    def test_page_is_open_but_data_needs_the_key(self):
        c = self.client()
        page = c.get("/dashboard")
        self.assertEqual(200, page.status_code)
        self.assertIn(b"picfortFrameKey", page.data)  # one key for /frame and /dashboard
        self.assertEqual(401, c.get("/dashboard/data").status_code)
        self.assertEqual(401, c.get("/dashboard/data", headers={"X-Picfort-Key": "nope"}).status_code)
        ok = c.get("/dashboard/data", headers={"X-Picfort-Key": "s3cret"})
        self.assertEqual(200, ok.status_code)
        self.assertEqual("private, no-store", ok.headers["Cache-Control"])

    def test_without_server_key_data_is_open(self):
        self.assertEqual(200, self.client(api_key="").get("/dashboard/data").status_code)

    def test_sections_counts_and_coverage(self):
        d = self.client(api_key="").get("/dashboard/data").get_json()
        self.assertEqual({"server", "immich", "frame", "photo_ai", "stores", "backups", "recent_logs", "problems"},
                         set(d))
        self.assertEqual("dev", d["server"]["version"])

    def test_version_comes_from_the_build_not_the_compose_tag(self):
        old = dict(os.environ)
        try:
            # .env's PICFORT_VERSION picks the image tag and reaches the container via env_file.
            os.environ.update(PICFORT_VERSION="latest", PICFORT_BUILD_VERSION="v0.9.27", PICFORT_BUILD_REVISION="abc")
            d = self.client(api_key="").get("/dashboard/data").get_json()
            self.assertEqual(("v0.9.27", "abc"), (d["server"]["version"], d["server"]["revision"]))
        finally:
            os.environ.clear()
            os.environ.update(old)
        self.assertEqual({"state": "ok", "version": "v3.3.5"},
                         {k: d["immich"][k] for k in ("state", "version")})
        ai = d["photo_ai"]
        self.assertEqual((3, 1, 1, 1, 1), (ai["analyzed"], ai["cleanup"], ai["frame_worthy"], ai["sensitive"],
                                           ai["failed_skipped"]))
        self.assertEqual(0.015, ai["coverage"])
        self.assertEqual([23, 7], ai["active_hours"])
        self.assertEqual({"events", "photo_ai"}, set(d["stores"]))
        self.assertEqual(3, d["stores"]["photo_ai"]["tables"]["photo_insights"])
        self.assertEqual(PhotoAiStore.SCHEMA_VERSION, d["stores"]["photo_ai"]["schema_version"])
        self.assertEqual(["events.db.v1.bak"], [b["file"] for b in d["backups"]])
        self.assertIn({"level": "warning", "text": "Photo AI: Ollama timeout"}, d["problems"])

    def test_no_contents_leak(self):
        body = self.client(api_key="").get("/dashboard/data").get_data(as_text=True)
        for secret in ("Secret scene", "a1", "boom"):
            self.assertNotIn(secret, body)

    def test_immich_down_is_an_error_and_the_rest_still_renders(self):
        self.immich.health = "unreachable"
        d = self.client(api_key="").get("/dashboard/data").get_json()
        self.assertEqual("unreachable", d["immich"]["state"])
        self.assertIsNone(d["immich"]["library"])
        self.assertIsNone(d["photo_ai"]["coverage"])
        self.assertEqual({"level": "error", "text": "Immich: unreachable"}, d["problems"][0])
        self.assertIn("stores", d)

    def test_missing_statistics_permission_degrades(self):
        self.immich.stats_fail = True
        d = self.client(api_key="").get("/dashboard/data").get_json()
        self.assertIsNone(d["immich"]["library"])
        self.assertEqual("v3.3.5", d["immich"]["version"])

    def test_recent_logs_keep_the_last_warnings(self):
        handler = RecentLogHandler(capacity=3)
        logger = logging.getLogger("test-dashboard")
        logger.addHandler(handler)
        try:
            logger.info("ignored")
            for i in range(5):
                logger.warning("problem %s", i)
        finally:
            logger.removeHandler(handler)
        self.assertEqual(["problem 4", "problem 3", "problem 2"], [r["message"] for r in handler.recent()])

    def test_app_installs_the_recent_log_handler_once(self):
        self.client(api_key="")
        self.client(api_key="")
        self.assertEqual(1, logging.getLogger().handlers.count(RECENT_LOGS))


if __name__ == "__main__":
    unittest.main()
