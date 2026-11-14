"""T-072: the frame in a browser, behind the Picfort key."""

import os
import tempfile
import time
import unittest
from io import BytesIO

from helpers import Clock, FakeImmich
from PIL import Image

import app as server
import frame
from app import FrameController, create_app
from browser_frame import scale_for_screen, screen_size
from slideshow import Picker, SettingsStore, Slideshow

ASSET = "aaaaaaaa-0000-0000-0000-000000000000"
KEY = {"X-Picfort-Key": "s3cret"}


def jpeg(size=(4000, 3000), colour=(200, 40, 40)) -> bytes:
    out = BytesIO()
    Image.new("RGB", size, colour).save(out, "JPEG")
    return out.getvalue()


class BrowserFrameTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.fetched = []
        self.client = self.make_client("s3cret")

    def make_client(self, api_key, clock=None):
        cfg = frame.Config(immich_url="http://immich", immich_api_key="k",
                           preview_path=os.path.join(self.dir, "p.png"))
        self.controller = FrameController(cfg, min_interval=0, display=lambda _: None, prepare=lambda a: a)
        slideshow = Slideshow(SettingsStore(os.path.join(self.dir, "s.json")), Picker(FakeImmich(album={})),
                              self.controller.try_show)

        def fetch(asset_id):  # Immich, as the browser frame sees it: the asset's original
            self.fetched.append(asset_id)
            return Image.open(BytesIO(jpeg()))
        return create_app(self.controller, slideshow, FakeImmich(album={}), api_key=api_key,
                          frame_fetch=fetch, clock=clock or Clock()).test_client()

    def wait_idle(self):
        for _ in range(200):
            if not self.controller.busy:
                return
            time.sleep(0.01)

    def test_page_opens_without_the_key_but_nothing_else_does(self):
        r = self.client.get("/frame")
        self.assertEqual(200, r.status_code)
        self.assertIn(b"Picfort frame", r.data)
        self.assertIn("no-store", r.headers["Cache-Control"])
        self.assertEqual(401, self.client.get("/frame/state").status_code)
        self.assertEqual(401, self.client.get("/frame/photo").status_code)
        self.assertEqual(401, self.client.get("/frame/state", headers={"X-Picfort-Key": "nope"}).status_code)

    def test_off_without_a_server_key(self):
        client = self.make_client("")
        for path in ["/frame", "/frame/state", "/frame/photo"]:
            self.assertEqual(404, client.get(path).status_code, path)

    def test_follows_the_frame_in_full_colour(self):
        state = self.client.get("/frame/state", headers=KEY).get_json()
        self.assertEqual({"version": 0, "shown": False, "fit": "cover"}, state)
        self.assertEqual(404, self.client.get("/frame/photo", headers=KEY).status_code)

        self.controller.show(ASSET)
        self.wait_idle()
        state = self.client.get("/frame/state", headers=KEY).get_json()
        self.assertEqual((1, True), (state["version"], state["shown"]))
        r = self.client.get("/frame/photo?w=1000&h=700", headers=KEY)
        self.assertEqual(200, r.status_code)
        self.assertEqual("image/jpeg", r.mimetype)
        img = Image.open(BytesIO(r.data))
        # 4000x3000 covering 1024x768 (sizes rounded up to 128): exactly that, colours kept
        self.assertEqual((1024, 768), img.size)
        red, green, _ = img.getpixel((500, 380))
        self.assertGreater(red, 150)
        self.assertLess(green, 80)
        # A second tablet of the same size: from the cache, not Immich again
        self.client.get("/frame/photo?w=1024&h=768", headers=KEY)
        self.assertEqual([ASSET], self.fetched)

    def test_photo_from_the_phone(self):
        self.controller.show_local(jpeg((800, 600), (30, 30, 200)))
        self.wait_idle()
        r = self.client.get("/frame/photo?w=3000&h=3000", headers=KEY)
        self.assertEqual(200, r.status_code)
        self.assertEqual((800, 600), Image.open(BytesIO(r.data)).size)  # never enlarged
        self.assertEqual([], self.fetched)

    def test_survives_a_restart(self):
        self.controller.show_local(jpeg((800, 600)))
        self.wait_idle()
        client = self.make_client("s3cret")  # a new controller over the same data
        self.assertTrue(client.get("/frame/state", headers=KEY).get_json()["shown"])
        self.assertEqual(200, client.get("/frame/photo", headers=KEY).status_code)

    def test_too_many_wrong_keys(self):
        clock = Clock()
        client = self.make_client("s3cret", clock=clock)
        for _ in range(server.MAX_WRONG_KEYS):
            self.assertEqual(401, client.get("/status", headers={"X-Picfort-Key": "guess"}).status_code)
        r = client.get("/status", headers=KEY)
        self.assertEqual(429, r.status_code)  # even the right key, for a while
        self.assertIn("Retry-After", r.headers)
        # Another address (Cloudflare names the caller) is not affected
        self.assertEqual(200, client.get("/status", headers={**KEY, "CF-Connecting-IP": "203.0.113.9"}).status_code)
        # The page stays open (it has no secrets); no key at all is not a guess
        self.assertEqual(200, client.get("/frame").status_code)
        for _ in range(server.MAX_WRONG_KEYS):
            client.get("/status", headers={"CF-Connecting-IP": "203.0.113.10"})
        self.assertEqual(200, client.get("/status", headers={**KEY, "CF-Connecting-IP": "203.0.113.10"}).status_code)
        clock.advance(server.WRONG_KEY_WINDOW + 1)
        self.assertEqual(200, client.get("/status", headers=KEY).status_code)


class ScalingTest(unittest.TestCase):
    def test_screen_size(self):
        self.assertEqual((1920, 1152), screen_size(None, "1080"))
        self.assertEqual((128, 4096), screen_size("1", "99999"))
        self.assertEqual((2816, 1920), screen_size("2736", "1824"))  # Surface Pro

    def test_cover_and_contain(self):
        img = Image.new("RGB", (4000, 2000))
        self.assertEqual((2000, 1000), scale_for_screen(img, (1000, 1000), "cover").size)
        self.assertEqual((1000, 500), scale_for_screen(img, (1000, 1000), "contain").size)
        self.assertEqual((4000, 2000), scale_for_screen(img, (8000, 8000), "cover").size)


if __name__ == "__main__":
    unittest.main()
