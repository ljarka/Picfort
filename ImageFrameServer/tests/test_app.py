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


if __name__ == "__main__":
    unittest.main()
