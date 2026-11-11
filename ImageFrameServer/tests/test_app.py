import os
import tempfile
import time
import unittest

from helpers import Clock, FakeImmich

import frame
from app import FrameController, create_app
from slideshow import Picker, SettingsStore, Slideshow

ASSET = "aaaaaaaa-0000-0000-0000-000000000000"


class AppTest(unittest.TestCase):
    def setUp(self):
        d = tempfile.mkdtemp()
        cfg = frame.Config(immich_url="http://immich", immich_api_key="k", preview_path=os.path.join(d, "p.png"))
        self.displayed = []
        self.clock = Clock()

        def prepare(asset_id):
            if asset_id.startswith("b"):
                raise frame.FrameError("Asset not found on Immich", 404)
            return asset_id

        self.controller = FrameController(cfg, min_interval=30, clock=self.clock,
                                          display=self.displayed.append, prepare=prepare)
        self.slideshow = Slideshow(SettingsStore(os.path.join(d, "s.json")),
                                   Picker(FakeImmich(album={"a": [ASSET]})), self.controller.try_show,
                                   clock=self.clock)
        self.client = create_app(self.controller, self.slideshow, FakeImmich(album={"a": []})).test_client()

    def wait_idle(self):
        for _ in range(100):
            if not self.controller.busy:
                return
            time.sleep(0.01)

    def test_run_and_rate_limit(self):
        self.assertEqual(400, self.client.get("/run?assetId=../etc").status_code)
        self.assertEqual(404, self.client.get("/run?assetId=" + "b" * 8 + ASSET[8:]).status_code)
        self.assertEqual(202, self.client.get("/run?assetId=" + ASSET).status_code)
        self.wait_idle()
        self.assertEqual([ASSET], self.displayed)
        r = self.client.get("/run?assetId=" + ASSET)
        self.assertEqual(429, r.status_code)
        self.assertEqual("30", r.headers["Retry-After"])
        self.assertEqual(ASSET, self.client.get("/status").get_json()["currentAsset"])

    def test_slideshow_settings(self):
        self.assertEqual("off", self.client.get("/slideshow").get_json()["mode"])
        self.assertEqual(409, self.client.post("/next").status_code)
        self.assertEqual(400, self.client.put("/slideshow", json={"mode": "album"}).status_code)
        r = self.client.put("/slideshow", json={"mode": "album", "album_id": "a", "interval_minutes": 30,
                                                "quiet_start": 22, "quiet_end": 7})
        self.assertEqual(200, r.status_code, r.get_json())
        body = r.get_json()
        self.assertEqual(("album", 30, 22), (body["mode"], body["interval_minutes"], body["quiet_start"]))
        # Partial update keeps the rest; settings survive a restart.
        self.client.put("/slideshow", json={"quiet_start": None, "quiet_end": None})
        reloaded = SettingsStore(self.slideshow.store.path).load()
        self.assertEqual(("album", "a", None), (reloaded.mode, reloaded.album_id, reloaded.quiet_start))

    def test_slideshow_shows_through_controller(self):
        self.client.put("/slideshow", json={"mode": "album", "album_id": "a"})
        self.slideshow.tick()
        self.wait_idle()
        self.assertEqual([ASSET], self.displayed)
        self.assertEqual(202, self.client.post("/next").status_code)

    def test_albums(self):
        self.assertEqual([{"id": "a", "name": "Album a"}], self.client.get("/albums").get_json())

    def test_run_local(self):
        from io import BytesIO
        from PIL import Image
        buf = BytesIO()
        Image.new("RGB", (100, 100), color=(128, 128, 128)).save(buf, format="JPEG")
        jpeg = buf.getvalue()

        r = self.client.post("/run-local", data={"photo": (BytesIO(jpeg), "photo.jpg")},
                             content_type="multipart/form-data")
        self.assertEqual(202, r.status_code)
        self.wait_idle()
        self.assertEqual(1, len(self.displayed))
        self.assertEqual("local", self.client.get("/status").get_json()["currentAsset"])

    def test_run_local_missing_field(self):
        r = self.client.post("/run-local", data={}, content_type="multipart/form-data")
        self.assertEqual(400, r.status_code)

    def test_run_local_busy(self):
        from io import BytesIO
        from PIL import Image
        buf = BytesIO()
        Image.new("RGB", (10, 10)).save(buf, format="JPEG")
        jpeg = buf.getvalue()

        self.controller.busy = True
        r = self.client.post("/run-local", data={"photo": (BytesIO(jpeg), "photo.jpg")},
                             content_type="multipart/form-data")
        self.assertEqual(409, r.status_code)
        self.controller.busy = False


class ApiKeyTest(unittest.TestCase):
    """PICFORT_API_KEY: every endpoint needs X-Picfort-Key; unset, nothing changes (ADR 0004)."""

    def make_client(self, api_key):
        d = tempfile.mkdtemp()
        cfg = frame.Config(immich_url="http://immich", immich_api_key="k", preview_path=os.path.join(d, "p.png"))
        controller = FrameController(cfg, min_interval=30, display=lambda _: None, prepare=lambda a: a)
        slideshow = Slideshow(SettingsStore(os.path.join(d, "s.json")), Picker(FakeImmich(album={})),
                              controller.try_show)
        return create_app(controller, slideshow, FakeImmich(album={}), api_key=api_key).test_client()

    def test_key_required(self):
        client = self.make_client("s3cret")
        for path in ["/status", "/albums", "/current", "/wall"]:
            for headers in [{}, {"X-Picfort-Key": "wrong"}, {"X-Picfort-Key": ""}]:
                r = client.get(path, headers=headers)
                self.assertEqual(401, r.status_code, (path, headers))
                self.assertEqual("Picfort-Key", r.headers["WWW-Authenticate"])
        self.assertEqual(200, client.get("/status", headers={"X-Picfort-Key": "s3cret"}).status_code)

    def test_responses_are_private(self):
        # Wall photos are served with a long max-age; a proxy must not share them.
        client = self.make_client("s3cret")
        cc = client.get("/status", headers={"X-Picfort-Key": "s3cret"}).headers["Cache-Control"]
        self.assertIn("private", cc)
        self.assertNotIn("public", cc)

    def test_no_key_configured(self):
        self.assertEqual(200, self.make_client("").get("/status").status_code)


if __name__ == "__main__":
    unittest.main()
