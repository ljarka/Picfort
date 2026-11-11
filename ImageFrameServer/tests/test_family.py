import os
import sqlite3
import tempfile
import unittest
from contextlib import closing
from io import BytesIO

from PIL import Image

from helpers import FakeImmich
from test_wall import MAMA, TATA, Clock, FakeImmichAuth, image_bytes

import family
import frame
from app import FrameController, create_app
from auth import Authenticator
from family import FamilyStore
from shopping import ShoppingStore
from slideshow import Picker, SettingsStore, Slideshow
from wall import WallStore

OLA = "33333333-3333-3333-3333-333333333333"


class FamilyApiTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.db = os.path.join(self.dir, "family.db")
        self.photos = os.path.join(self.dir, "profile_photos")
        self.shopping_db = os.path.join(self.dir, "shopping.db")
        self.wall_db = os.path.join(self.dir, "wall.db")
        self.store = FamilyStore(self.db, self.photos, clock=Clock())
        self.shopping = ShoppingStore(self.shopping_db, clock=Clock())
        self.wall = WallStore(self.wall_db, os.path.join(self.dir, "wall_photos"), clock=Clock())
        cfg = frame.Config(immich_url="http://immich", immich_api_key="k",
                           preview_path=os.path.join(self.dir, "p.png"))
        controller = FrameController(cfg, min_interval=30, display=lambda img: None, prepare=lambda a: a)
        slideshow = Slideshow(SettingsStore(os.path.join(self.dir, "s.json")), Picker(FakeImmich()),
                              controller.try_show)
        self.client = create_app(controller, slideshow, FakeImmich(), self.wall,
                                 Authenticator(FakeImmichAuth()), shopping=self.shopping,
                                 family=self.store).test_client()
        self.client.environ_base["HTTP_AUTHORIZATION"] = "Bearer mama-token"

    def as_tata(self):
        return {"Authorization": "Bearer tata-token"}

    def put_photo(self, user_id, data, filename="face.jpg", headers=None):
        form = {"photo": (BytesIO(data), filename)} if data is not None else {}
        return self.client.put(f"/family/{user_id}/photo", data=form,
                               content_type="multipart/form-data", headers=headers or {})

    def test_empty(self):
        r = self.client.get("/family")
        self.assertEqual(200, r.status_code)
        self.assertEqual({"profiles": []}, r.get_json())

    def test_create_by_patch_clear_with_null_and_delete_when_empty(self):
        # Anyone in the family can update any user's profile.
        r = self.client.patch(f"/family/{MAMA}", json={"display_name": "  Mama Anna  ", "hidden": False},
                              headers=self.as_tata())
        self.assertEqual(200, r.status_code, r.get_json())
        self.assertEqual({
            "user_id": MAMA,
            "display_name": "Mama Anna",
            "photo_url": None,
            "hidden": False,
            "updated_by": TATA,
            "updated": "2026-11-10T12:01:00Z",
        }, r.get_json())
        self.assertEqual([r.get_json()], self.client.get("/family").get_json()["profiles"])

        # Clear display_name with null; hidden stays False so the row remains.
        r = self.client.patch(f"/family/{MAMA}", json={"display_name": None})
        self.assertIsNone(r.get_json()["display_name"])
        self.assertIs(False, r.get_json()["hidden"])
        self.assertEqual(MAMA, r.get_json()["updated_by"])
        self.assertEqual(1, len(self.client.get("/family").get_json()["profiles"]))

        # Clear hidden too -> every field is null, so the row is deleted.
        r = self.client.patch(f"/family/{MAMA}", json={"hidden": None})
        self.assertEqual(200, r.status_code)
        self.assertIsNone(r.get_json()["hidden"])
        self.assertEqual({"profiles": []}, self.client.get("/family").get_json())

        # Patching a missing row with only nulls keeps the table empty.
        r = self.client.patch(f"/family/{OLA}", json={"display_name": None})
        self.assertEqual(200, r.status_code)
        self.assertEqual({"profiles": []}, self.client.get("/family").get_json())

    def test_photo_upload_replace_and_delete_removes_files(self):
        jpg = image_bytes("JPEG", (64, 64))
        r = self.put_photo(MAMA, jpg, filename="avatar.png", headers=self.as_tata())
        self.assertEqual(200, r.status_code, r.get_json())
        first_url = r.get_json()["photo_url"]
        self.assertRegex(first_url, r"^/family/photo/[0-9a-f]{32}\.jpg$")
        self.assertEqual(TATA, r.get_json()["updated_by"])
        self.assertEqual(1, len(os.listdir(self.photos)))

        served = self.client.get(first_url)
        self.assertEqual(200, served.status_code)
        self.assertIn("private", served.headers["Cache-Control"])
        self.assertEqual("image/jpeg", served.mimetype)
        self.assertEqual(jpg, served.data)
        served.close()

        # Replace with a PNG: the old file is deleted and a new URL is returned.
        png = image_bytes("PNG", (64, 64))
        r = self.put_photo(MAMA, png, filename="avatar.png")
        second_url = r.get_json()["photo_url"]
        self.assertNotEqual(first_url, second_url)
        self.assertTrue(second_url.endswith(".png"))
        self.assertEqual(1, len(os.listdir(self.photos)))
        self.assertEqual(404, self.client.get(first_url).status_code)

        # Delete the photo when display_name and hidden are null: deletes the file and the row.
        self.assertEqual(204, self.client.delete(f"/family/{MAMA}/photo").status_code)
        self.assertEqual([], os.listdir(self.photos))
        self.assertEqual(404, self.client.get(second_url).status_code)
        self.assertEqual({"profiles": []}, self.client.get("/family").get_json())
        # Deleting again when there is no photo is still 204.
        self.assertEqual(204, self.client.delete(f"/family/{MAMA}/photo").status_code)

    def test_delete_photo_keeps_row_when_other_fields_are_set(self):
        self.client.patch(f"/family/{MAMA}", json={"display_name": "Mama"})
        self.put_photo(MAMA, image_bytes("JPEG"))
        self.assertEqual(204, self.client.delete(f"/family/{MAMA}/photo").status_code)
        profiles = self.client.get("/family").get_json()["profiles"]
        self.assertEqual(1, len(profiles))
        self.assertEqual("Mama", profiles[0]["display_name"])
        self.assertIsNone(profiles[0]["photo_url"])

    def test_validation(self):
        for bad_id in ("not-a-uuid", "123", "11111111-1111-1111-1111-11111111111z"):
            self.assertEqual(400, self.client.patch(f"/family/{bad_id}", json={"display_name": "x"}).status_code, bad_id)
            self.assertEqual(400, self.put_photo(bad_id, image_bytes()).status_code, bad_id)
            self.assertEqual(400, self.client.delete(f"/family/{bad_id}/photo").status_code, bad_id)

        for body in ({"display_name": ""}, {"display_name": "   "}, {"display_name": "x" * 51},
                     {"display_name": 123}, {"hidden": 1}, {"hidden": "true"}):
            self.assertEqual(400, self.client.patch(f"/family/{MAMA}", json=body).status_code, body)

        self.assertEqual(400, self.client.patch(f"/family/{MAMA}", data="nope",
                                                content_type="application/json").status_code)
        self.assertEqual(400, self.client.patch(f"/family/{MAMA}", json=["a"]).status_code)

        # Photo validation: missing file, not an image, GIF, > 2 MB.
        self.assertEqual(400, self.put_photo(MAMA, None).status_code)
        self.assertEqual(415, self.put_photo(MAMA, b"not an image").status_code)
        gif = BytesIO()
        Image.new("RGB", (4, 4)).save(gif, "GIF")
        self.assertEqual(415, self.put_photo(MAMA, gif.getvalue(), filename="x.gif").status_code)
        too_big = image_bytes() + b"\0" * family.MAX_PHOTO_BYTES
        self.assertEqual(413, self.put_photo(MAMA, too_big).status_code)
        self.assertEqual([], os.listdir(self.photos))

        # Path traversal check on /family/photo/<file>.
        open(os.path.join(self.dir, "secret.txt"), "w").close()
        for name in ("..%2Fsecret.txt", "secret.txt", "../family.db"):
            self.assertEqual(404, self.client.get(f"/family/photo/{name}").status_code, name)

    def test_requires_token(self):
        photo_url = self.put_photo(MAMA, image_bytes()).get_json()["photo_url"]
        for method, path in (("get", "/family"), ("patch", f"/family/{MAMA}"),
                             ("put", f"/family/{MAMA}/photo"), ("delete", f"/family/{MAMA}/photo"),
                             ("get", photo_url)):
            r = getattr(self.client, method)(path, headers={"Authorization": ""})
            self.assertEqual(401, r.status_code, (method, path))

    def test_schema_change_keeps_backup_and_leaves_other_databases_alone(self):
        self.client.patch(f"/family/{MAMA}", json={"display_name": "Mama"})
        self.client.post("/lists", json={"title": "Biedronka"})
        self.client.post("/wall", data={"text": "hi"}, content_type="multipart/form-data")
        with closing(sqlite3.connect(self.db)) as conn:
            conn.execute("PRAGMA user_version = 99")
        reopened = FamilyStore(self.db, self.photos)
        self.assertEqual([], reopened.profiles())
        self.assertTrue(os.path.exists(self.db + ".v99.bak"))
        self.assertEqual(1, len(ShoppingStore(self.shopping_db).lists()))
        self.assertEqual(1, len(WallStore(self.wall_db, os.path.join(self.dir, "wall_photos")).list()))


if __name__ == "__main__":
    unittest.main()
