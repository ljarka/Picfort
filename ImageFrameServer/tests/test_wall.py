import os
import sqlite3
import tempfile
import unittest
from io import BytesIO

from helpers import FakeImmich

import frame
import wall
from auth import Authenticator, AuthUnavailable, User
from app import FrameController, create_app
from PIL import Image
from slideshow import Picker, SettingsStore, Slideshow
from wall import WallStore

MAMA = "11111111-1111-1111-1111-111111111111"
TATA = "22222222-2222-2222-2222-222222222222"
USERS = {"mama-token": User(MAMA, "Mama"), "tata-token": User(TATA, "Tata")}


class FakeImmichAuth:
    """Stands in for Immich's /api/users/me."""

    def __init__(self):
        self.calls = []
        self.down = False

    def __call__(self, token):
        self.calls.append(token)
        if self.down:
            raise AuthUnavailable("Could not reach Immich")
        return USERS.get(token)


def image_bytes(fmt="JPEG", size=(40, 30), orientation=None):
    img = Image.new("RGB", size, "red")
    out = BytesIO()
    if orientation is not None:
        exif = Image.Exif()
        exif[0x0112] = orientation
        img.save(out, fmt, exif=exif)
    else:
        img.save(out, fmt)
    return out.getvalue()


class Clock:
    """Returns the next timestamp on each call, one minute apart."""

    def __init__(self):
        self.minute = 0

    def __call__(self):
        self.minute += 1
        return f"2026-11-10T12:{self.minute:02d}:00Z"


class WallApiTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.photos = os.path.join(self.dir, "wall_photos")
        self.store = WallStore(os.path.join(self.dir, "wall.db"), self.photos, clock=Clock())
        cfg = frame.Config(immich_url="http://immich", immich_api_key="k",
                           preview_path=os.path.join(self.dir, "p.png"))
        controller = FrameController(cfg, min_interval=30, display=lambda img: None, prepare=lambda a: a)
        slideshow = Slideshow(SettingsStore(os.path.join(self.dir, "s.json")), Picker(FakeImmich()),
                              controller.try_show)
        self.immich = FakeImmichAuth()
        self.client = create_app(controller, slideshow, FakeImmich(), self.store,
                                 Authenticator(self.immich)).test_client()
        self.client.environ_base["HTTP_AUTHORIZATION"] = "Bearer mama-token"

    def post(self, token="mama-token", text=None, photo=None, filename="p.jpg", **form):
        data = dict(form)
        if text is not None:
            data["text"] = text
        if photo is not None:
            data["photo"] = (BytesIO(photo), filename)
        return self.client.post("/wall", data=data, content_type="multipart/form-data",
                                headers={"Authorization": f"Bearer {token}"})

    def test_empty_wall(self):
        r = self.client.get("/wall")
        self.assertEqual(200, r.status_code)
        self.assertEqual([], r.get_json())

    def test_text_post(self):
        r = self.post(text="  Pierwsze pomidory!  ")
        self.assertEqual(201, r.status_code, r.get_json())
        post = r.get_json()
        self.assertEqual(
            {"author_id": MAMA, "author": "Mama", "text": "Pierwsze pomidory!", "photo_url": None,
             "photo_width": None, "photo_height": None, "pinned": False, "created": "2026-11-10T12:01:00Z"},
            {k: v for k, v in post.items() if k != "id"},
        )
        self.assertEqual([post], self.client.get("/wall").get_json())

    def test_photo_post_is_served(self):
        photo = image_bytes("PNG", (40, 30))
        r = self.post(token="tata-token", photo=photo, filename="whatever.jpg")
        self.assertEqual(201, r.status_code, r.get_json())
        post = r.get_json()
        self.assertIsNone(post["text"])
        self.assertRegex(post["photo_url"], r"^/wall/photo/[0-9a-f]{32}\.png$")  # extension from content
        self.assertEqual((40, 30), (post["photo_width"], post["photo_height"]))
        served = self.client.get(post["photo_url"])
        self.assertEqual(200, served.status_code)
        self.assertIn("private", served.headers["Cache-Control"])
        self.assertEqual("image/png", served.mimetype)
        self.assertEqual(photo, served.data)
        served.close()

    def test_text_and_photo_post(self):
        post = self.post(text="Wycieczka", photo=image_bytes()).get_json()
        self.assertEqual("Wycieczka", post["text"])
        self.assertTrue(post["photo_url"].endswith(".jpg"))

    def test_rotated_photo_reports_upright_size(self):
        post = self.post(photo=image_bytes("JPEG", (40, 30), orientation=6)).get_json()
        self.assertEqual((30, 40), (post["photo_width"], post["photo_height"]))

    def test_invalid_posts(self):
        self.assertEqual(400, self.post(text="   ").status_code)  # neither text nor photo
        r = self.post(photo=b"not an image", filename="x.jpg")
        self.assertEqual(415, r.status_code)
        self.assertEqual("error", r.get_json()["status"])
        gif = BytesIO()
        Image.new("RGB", (4, 4)).save(gif, "GIF")
        self.assertEqual(415, self.post(photo=gif.getvalue(), filename="x.gif").status_code)
        self.assertEqual([], os.listdir(self.photos))  # nothing left behind
        self.assertEqual([], self.client.get("/wall").get_json())

    def test_photo_too_large(self):
        big = image_bytes() + b"\0" * wall.MAX_PHOTO_BYTES
        self.assertEqual(413, self.post(photo=big).status_code)
        self.assertEqual([], os.listdir(self.photos))

    def test_order_pinned_first_then_newest(self):
        ids = [self.post(text=f"post {i}").get_json()["id"] for i in range(3)]
        self.assertEqual(ids[::-1], [p["id"] for p in self.client.get("/wall").get_json()])
        self.client.put(f"/wall/{ids[0]}/pin")
        self.assertEqual([ids[0], ids[2], ids[1]], [p["id"] for p in self.client.get("/wall").get_json()])
        self.assertEqual([ids[0]], [p["id"] for p in self.client.get("/wall?limit=1").get_json()])

    def test_limit_validation(self):
        for bad in ("0", "101", "abc"):
            self.assertEqual(400, self.client.get(f"/wall?limit={bad}").status_code, bad)
        self.assertEqual(200, self.client.get("/wall?limit=100").status_code)

    def test_pin_toggles(self):
        post_id = self.post(text="hi").get_json()["id"]
        self.assertTrue(self.client.put(f"/wall/{post_id}/pin").get_json()["pinned"])
        self.assertFalse(self.client.put(f"/wall/{post_id}/pin").get_json()["pinned"])
        self.assertEqual(404, self.client.put("/wall/999/pin").status_code)

    def test_delete_removes_photo(self):
        post = self.post(photo=image_bytes()).get_json()
        self.assertEqual(1, len(os.listdir(self.photos)))
        self.assertEqual(204, self.client.delete(f"/wall/{post['id']}").status_code)
        self.assertEqual([], os.listdir(self.photos))
        self.assertEqual(404, self.client.get(post["photo_url"]).status_code)
        self.assertEqual(404, self.client.delete(f"/wall/{post['id']}").status_code)
        self.assertEqual([], self.client.get("/wall").get_json())

    def test_photo_names_are_checked(self):
        open(os.path.join(self.dir, "secret.txt"), "w").close()
        for name in ("..%2Fsecret.txt", "secret.txt", "../wall.db"):
            self.assertEqual(404, self.client.get(f"/wall/photo/{name}").status_code, name)

    def test_posts_survive_restart(self):
        post = self.post(text="hi").get_json()
        reopened = WallStore(self.store.db_path, self.photos)
        self.assertEqual([post], reopened.list())

    def test_schema_change_starts_empty(self):
        self.post(text="hi", photo=image_bytes())
        conn = sqlite3.connect(self.store.db_path)
        conn.execute("PRAGMA user_version = 0")  # as if written by another schema
        conn.close()
        reopened = WallStore(self.store.db_path, self.photos)
        self.assertEqual([], reopened.list())
        self.assertEqual([], os.listdir(self.photos))

    def test_author_comes_from_the_token(self):
        post = self.post(token="tata-token", text="hi", author_id=MAMA, author="Mama").get_json()
        self.assertEqual((TATA, "Tata"), (post["author_id"], post["author"]))

    def test_wall_needs_a_valid_token(self):
        photo_url = self.post(photo=image_bytes()).get_json()["photo_url"]
        for header in (None, "Bearer", "Basic bWFtYTp4", "Bearer wrong-token"):
            headers = {"Authorization": header} if header else {}
            if header is None:
                self.client.environ_base.pop("HTTP_AUTHORIZATION")
            for method, path in (("get", "/wall"), ("post", "/wall"), ("delete", "/wall/1"),
                                 ("put", "/wall/1/pin"), ("get", photo_url)):
                r = getattr(self.client, method)(path, headers=headers)
                self.assertEqual(401, r.status_code, (header, method, path))
                self.assertEqual("Bearer", r.headers["WWW-Authenticate"])
        self.assertEqual(1, len(self.store.list()))

    def test_immich_down_is_502_not_401(self):
        self.immich.down = True
        self.assertEqual(502, self.client.get("/wall").status_code)

    def test_token_lookups_are_cached(self):
        for _ in range(3):
            self.client.get("/wall")
        self.assertEqual(["mama-token"], self.immich.calls)
        self.assertEqual(401, self.client.get("/wall", headers={"Authorization": "Bearer x"}).status_code)
        self.assertEqual(401, self.client.get("/wall", headers={"Authorization": "Bearer x"}).status_code)
        self.assertEqual(["mama-token", "x", "x"], self.immich.calls)  # rejections aren't cached

    def test_only_the_author_can_delete_or_pin(self):
        post_id = self.post(text="Mama's").get_json()["id"]
        tata = {"Authorization": "Bearer tata-token"}
        self.assertEqual(403, self.client.delete(f"/wall/{post_id}", headers=tata).status_code)
        self.assertEqual(403, self.client.put(f"/wall/{post_id}/pin", headers=tata).status_code)
        self.assertEqual(200, self.client.get("/wall", headers=tata).status_code)  # everyone can read
        self.assertFalse(self.store.get(post_id)["pinned"])
        self.assertEqual(204, self.client.delete(f"/wall/{post_id}").status_code)

    def test_frame_endpoints_need_no_token(self):
        self.client.environ_base.pop("HTTP_AUTHORIZATION")
        self.assertEqual(200, self.client.get("/status").status_code)
        self.assertEqual([], self.immich.calls)

    def test_status_still_works(self):
        self.assertEqual("ok", self.client.get("/status").get_json()["status"])


if __name__ == "__main__":
    unittest.main()
