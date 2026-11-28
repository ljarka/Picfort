"""Tests for wishlist CRUD, OpenGraph preview, filtering, and permissions (T-041)."""

import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
sys.path.insert(0, os.path.dirname(__file__))

import frame
from app import FrameController, create_app
from auth import Authenticator
from helpers import FakeImmich
from shopping import ShoppingStore
from slideshow import Picker, SettingsStore, Slideshow
from test_wall import Clock, FakeImmichAuth, MAMA, TATA
from wall import WallStore
from store import StoreError
from wishlist_preview import _SafeRedirectHandler, fetch_link_preview, parse_link_preview, validate_public_url


SAMPLE_HTML = """<!doctype html>
<html>
<head>
  <title>Ignored fallback title</title>
  <meta property="og:title" content="LEGO Technic Crane" />
  <meta property="og:image" content="/images/crane.jpg" />
  <meta property="og:price:amount" content="349,99" />
  <meta property="og:site_name" content="Allegro" />
</head>
<body></body>
</html>
"""

FALLBACK_TITLE_HTML = """<!doctype html>
<html>
<head><title>  Simple   Product Page  </title></head>
<body></body>
</html>
"""


class WishlistPreviewTest(unittest.TestCase):
    def test_extracts_opengraph_tags_and_resolves_relative_image(self):
        preview = parse_link_preview(SAMPLE_HTML, "https://allegro.pl/oferta/lego-123")
        self.assertEqual("LEGO Technic Crane", preview["title"])
        self.assertEqual("https://allegro.pl/images/crane.jpg", preview["image_url"])
        self.assertEqual(34999, preview["price_minor"])
        self.assertEqual("Allegro", preview["shop_name"])

    def test_falls_back_to_title_tag_and_hostname(self):
        preview = parse_link_preview(FALLBACK_TITLE_HTML, "https://www.example.com/item")
        self.assertEqual("Simple Product Page", preview["title"])
        self.assertIsNone(preview["image_url"])
        self.assertIsNone(preview["price_minor"])
        self.assertEqual("example.com", preview["shop_name"])

    def test_extracts_json_ld_product_price_and_image(self):
        html = """<!doctype html><html><head>
        <script type="application/ld+json">
        {"@type":"Product","name":"Klocki","image":["https://cdn.allegro.pl/klocki.jpg"],"offers":{"price":"149.90"}}
        </script></head><body></body></html>"""
        preview = parse_link_preview(html, "https://allegro.pl/oferta/klocki")
        self.assertEqual("Klocki", preview["title"])
        self.assertEqual("https://cdn.allegro.pl/klocki.jpg", preview["image_url"])
        self.assertEqual(14990, preview["price_minor"])
        self.assertEqual("allegro.pl", preview["shop_name"])


class WishlistSsrfTest(unittest.TestCase):
    """T-108: block private, loopback, link-local and redirect SSRF targets."""

    @staticmethod
    def _fake_resolve(mapping):
        import socket

        def resolve(host, port, type=0):
            ips = mapping.get(host)
            if ips is None:
                raise socket.gaierror("unknown host")
            return [(socket.AF_INET6 if ":" in ip else socket.AF_INET, socket.SOCK_STREAM, 0, "", (ip, port)) for ip in ips]

        return resolve

    def test_blocks_loopback_private_link_local_and_ipv6_loopback(self):
        resolver = self._fake_resolve({
            "localhost": ["127.0.0.1"],
            "lan.local": ["192.168.1.10"],
            "docker-svc": ["172.17.0.2"],
            "corp.internal": ["10.0.0.5"],
            "metadata.google.internal": ["169.254.169.254"],
            "v6loop.local": ["::1"],
            "mapped.local": ["::ffff:127.0.0.1"],
            "mixed.example": ["93.184.216.34", "10.0.0.1"],
            "public.example": ["93.184.216.34"],
        })
        for blocked in (
            "http://localhost/admin",
            "http://lan.local/secret",
            "http://docker-svc:5000/status",
            "http://corp.internal/",
            "http://metadata.google.internal/latest/meta-data/",
            "http://v6loop.local/",
            "http://mapped.local/",
            "http://mixed.example/",
            "http://unresolvable.invalid/",
        ):
            with self.subTest(url=blocked):
                with self.assertRaises(StoreError) as ctx:
                    validate_public_url(blocked, resolve=resolver)
                self.assertEqual(400, ctx.exception.status)

        self.assertEqual("https://public.example/item", validate_public_url("https://public.example/item", resolve=resolver))

    def test_blocks_redirect_to_private_ip_and_excessive_redirects(self):
        import urllib.request

        resolver = self._fake_resolve({
            "shop.example": ["93.184.216.34"],
            "internal.lan": ["192.168.1.1"],
        })
        handler = _SafeRedirectHandler(resolve=resolver, max_redirects=3)
        req = urllib.request.Request("https://shop.example/start")
        with self.assertRaises(StoreError) as ctx:
            handler.redirect_request(req, None, 302, "Found", {}, "http://internal.lan/secret")
        self.assertEqual(400, ctx.exception.status)

        # >3 redirects to public hosts are rejected with 502.
        handler2 = _SafeRedirectHandler(resolve=resolver, max_redirects=3)
        for i in range(3):
            next_req = handler2.redirect_request(req, None, 302, "Found", {}, f"https://shop.example/hop{i}")
            self.assertIsNotNone(next_req)
        with self.assertRaises(StoreError) as ctx2:
            handler2.redirect_request(req, None, 302, "Found", {}, "https://shop.example/hop3")
        self.assertEqual(502, ctx2.exception.status)

    def test_fetch_link_preview_blocks_ssrf_before_network(self):
        resolver = self._fake_resolve({"localhost": ["127.0.0.1"]})
        with self.assertRaises(StoreError) as ctx:
            fetch_link_preview("http://localhost:5000/status", resolve=resolver)
        self.assertEqual(400, ctx.exception.status)


class WishlistApiTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.db = os.path.join(self.dir, "shopping.db")
        self.wall_db = os.path.join(self.dir, "wall.db")
        self.preview_calls = []

        def fake_preview(url: str) -> dict:
            self.preview_calls.append(url)
            return {
                "title": "Previewed Bike",
                "image_url": "https://shop.example/bike.png",
                "price_minor": 129900,
                "shop_name": "Shop",
            }

        self.store = ShoppingStore(self.db, clock=Clock(), link_preview=fake_preview)
        self.wall = WallStore(self.wall_db, os.path.join(self.dir, "wall_photos"), clock=Clock())
        cfg = frame.Config(
            immich_url="http://immich",
            immich_api_key="k",
            preview_path=os.path.join(self.dir, "p.png"),
        )
        controller = FrameController(cfg, min_interval=30, display=lambda img: None, prepare=lambda a: a)
        slideshow = Slideshow(
            SettingsStore(os.path.join(self.dir, "s.json")),
            Picker(FakeImmich()),
            controller.try_show,
        )
        self.client = create_app(
            controller,
            slideshow,
            FakeImmich(),
            self.wall,
            Authenticator(FakeImmichAuth()),
            shopping=self.store,
        ).test_client()
        self.client.environ_base["HTTP_AUTHORIZATION"] = "Bearer mama-token"

    def tearDown(self):
        import shutil
        shutil.rmtree(self.dir, ignore_errors=True)

    def as_tata(self):
        return {"Authorization": "Bearer tata-token"}

    def test_preview_endpoint_validates_url_and_returns_metadata(self):
        self.assertEqual(400, self.client.post("/wishlist/preview", json={"url": "ftp://bad"}).status_code)
        r = self.client.post("/wishlist/preview", json={"url": "https://shop.example/bike"})
        self.assertEqual(200, r.status_code)
        self.assertEqual("Previewed Bike", r.get_json()["title"])
        self.assertEqual(129900, r.get_json()["price_minor"])

    def test_create_auto_fetches_metadata_when_title_omitted(self):
        r = self.client.post("/wishlist", json={"url": "https://shop.example/bike", "event_id": 7})
        self.assertEqual(201, r.status_code, r.get_json())
        item = r.get_json()
        self.assertEqual("Previewed Bike", item["title"])
        self.assertEqual("https://shop.example/bike.png", item["image_url"])
        self.assertEqual(129900, item["price_minor"])
        self.assertEqual(7, item["event_id"])
        self.assertFalse(item["bought"])
        self.assertEqual(MAMA, item["created_by"])

    def test_filter_by_event_and_bought_and_reserve_and_delete(self):
        a = self.client.post("/wishlist", json={"url": "https://a.example", "title": "A", "event_id": 10}).get_json()
        b = self.client.post("/wishlist", json={"url": "https://b.example", "title": "B", "event_id": 20}).get_json()

        # Filter by event_id
        ev10 = self.client.get("/wishlist?event_id=10").get_json()["items"]
        self.assertEqual([a["id"]], [i["id"] for i in ev10])

        # Tata reserves and marks B as bought
        r = self.client.patch(
            f"/wishlist/{b['id']}",
            json={"reserved_by": TATA, "bought": True},
            headers=self.as_tata(),
        )
        self.assertEqual(200, r.status_code)
        self.assertEqual(TATA, r.get_json()["reserved_by"])
        self.assertTrue(r.get_json()["bought"])

        # Unbought sorted first, bought last
        all_items = self.client.get("/wishlist").get_json()["items"]
        self.assertEqual([a["id"], b["id"]], [i["id"] for i in all_items])

        # Filter by bought=1
        bought_only = self.client.get("/wishlist?bought=1").get_json()["items"]
        self.assertEqual([b["id"]], [i["id"] for i in bought_only])

        # Tata cannot delete unbought A (created by Mama), but can delete bought B
        self.assertEqual(403, self.client.delete(f"/wishlist/{a['id']}", headers=self.as_tata()).status_code)
        self.assertEqual(204, self.client.delete(f"/wishlist/{b['id']}", headers=self.as_tata()).status_code)
        self.assertEqual(204, self.client.delete(f"/wishlist/{a['id']}").status_code)

    def test_wishlist_requires_auth(self):
        self.assertEqual(401, self.client.get("/wishlist", headers={"Authorization": ""}).status_code)
        self.assertEqual(
            401,
            self.client.post("/wishlist/preview", json={"url": "https://a.example"}, headers={"Authorization": ""}).status_code,
        )


class WishlistPhotoTest(unittest.TestCase):
    """A photo taken or chosen on the phone instead of an image URL (T-083)."""

    # The wishlist API test's fixtures, without running its tests again.
    setUp = WishlistApiTest.setUp
    tearDown = WishlistApiTest.tearDown

    def create_item(self):
        r = self.client.post("/wishlist", json={"url": "https://shop.example/bike", "title": "Bike"})
        self.assertEqual(201, r.status_code, r.get_json())
        return r.get_json()

    def upload(self, item_id, data, filename="photo.jpg"):
        from io import BytesIO
        return self.client.put(f"/wishlist/{item_id}/photo", data={"photo": (BytesIO(data), filename)},
                               content_type="multipart/form-data")

    def photos(self):
        return sorted(os.listdir(self.store.wishlist_photos_dir))

    def test_uploaded_photo_becomes_the_image_and_is_served_privately(self):
        from test_wall import image_bytes
        item = self.create_item()
        photo = image_bytes("PNG", (20, 10))
        r = self.upload(item["id"], photo)
        self.assertEqual(200, r.status_code, r.get_json())
        image_url = r.get_json()["image_url"]
        self.assertRegex(image_url, r"^/wishlist/photo/[0-9a-f]{32}\.png$")
        served = self.client.get(image_url)
        self.assertEqual(200, served.status_code)
        self.assertEqual(photo, served.data)
        self.assertIn("private", served.headers["Cache-Control"])
        served.close()
        self.assertEqual(401, self.client.get(image_url, headers={"Authorization": ""}).status_code)

    def test_new_photo_url_or_delete_removes_the_old_file(self):
        from test_wall import image_bytes
        item = self.create_item()
        self.upload(item["id"], image_bytes())
        first = self.photos()
        self.upload(item["id"], image_bytes("PNG"))
        self.assertEqual(1, len(self.photos()))
        self.assertNotEqual(first, self.photos())
        self.client.patch(f"/wishlist/{item['id']}", json={"image_url": "https://shop.example/x.png"})
        self.assertEqual([], self.photos())
        self.upload(item["id"], image_bytes())
        self.assertEqual(204, self.client.delete(f"/wishlist/{item['id']}").status_code)
        self.assertEqual([], self.photos())

    def test_invalid_uploads(self):
        item = self.create_item()
        self.assertEqual(400, self.client.put(f"/wishlist/{item['id']}/photo").status_code)
        self.assertEqual(415, self.upload(item["id"], b"not an image").status_code)
        from test_wall import image_bytes
        self.assertEqual(404, self.upload(999, image_bytes()).status_code)
        self.assertEqual([], self.photos())
        self.assertEqual(404, self.client.get("/wishlist/photo/../../etc.jpg").status_code)
