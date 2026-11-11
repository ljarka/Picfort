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
from events import EventStore
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
        self.events_db = os.path.join(self.dir, "events.db")
        self.events = EventStore(self.events_db, clock=Clock())
        self.store = FamilyStore(self.db, self.photos, clock=Clock(), events=self.events)
        self.events.family = self.store
        self.shopping = ShoppingStore(self.shopping_db, clock=Clock())
        self.wall = WallStore(self.wall_db, os.path.join(self.dir, "wall_photos"), clock=Clock())
        cfg = frame.Config(immich_url="http://immich", immich_api_key="k",
                           preview_path=os.path.join(self.dir, "p.png"))
        controller = FrameController(cfg, min_interval=30, display=lambda img: None, prepare=lambda a: a)
        slideshow = Slideshow(SettingsStore(os.path.join(self.dir, "s.json")), Picker(FakeImmich()),
                              controller.try_show)
        self.client = create_app(controller, slideshow, FakeImmich(), self.wall,
                                 Authenticator(FakeImmichAuth()), shopping=self.shopping,
                                 family=self.store, events=self.events).test_client()
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
            "manual": False,
            "birth_date": None,
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


    # --- T-033: manual members and birth dates ---------------------------------------------

    def test_add_rename_and_delete_a_manual_member(self):
        r = self.client.post("/family/members", json={"display_name": "  Babcia Zosia ", "birth_date": "1950-03-02"})
        self.assertEqual(201, r.status_code)
        babcia = r.get_json()
        self.assertTrue(babcia["manual"])
        self.assertEqual("Babcia Zosia", babcia["display_name"])
        self.assertEqual("1950-03-02", babcia["birth_date"])
        self.assertRegex(babcia["user_id"], family.UUID_RE)
        self.assertIsNone(babcia["photo_url"])

        r = self.client.patch(f"/family/{babcia['user_id']}", json={"display_name": "Babcia", "birth_date": None})
        self.assertEqual("Babcia", r.get_json()["display_name"])
        self.assertIsNone(r.get_json()["birth_date"])
        self.assertTrue(r.get_json()["manual"])

        # A manual member keeps a name, and is not dropped when every optional field is empty.
        r = self.client.patch(f"/family/{babcia['user_id']}", json={"display_name": None})
        self.assertEqual(400, r.status_code)
        self.assertEqual(1, len(self.client.get("/family").get_json()["profiles"]))

        self.assertEqual(204, self.client.delete(f"/family/{babcia['user_id']}").status_code)
        self.assertEqual([], self.client.get("/family").get_json()["profiles"])
        self.assertEqual(404, self.client.delete(f"/family/{babcia['user_id']}").status_code)

    def test_manual_member_photo_is_removed_with_the_member(self):
        member = self.client.post("/family/members", json={"display_name": "Ola"}).get_json()
        r = self.put_photo(member["user_id"], image_bytes("JPEG"))
        self.assertEqual(200, r.status_code)
        self.assertTrue(r.get_json()["manual"])
        self.assertEqual(1, len(os.listdir(self.photos)))
        # Removing the photo keeps the member.
        self.assertEqual(204, self.client.delete(f"/family/{member['user_id']}/photo").status_code)
        self.assertEqual("Ola", self.client.get("/family").get_json()["profiles"][0]["display_name"])
        self.put_photo(member["user_id"], image_bytes("PNG"))
        self.assertEqual(204, self.client.delete(f"/family/{member['user_id']}").status_code)
        self.assertEqual([], os.listdir(self.photos))

    def test_immich_user_row_cannot_be_deleted_only_cleared(self):
        self.client.patch(f"/family/{MAMA}", json={"display_name": "Mama", "birth_date": "--05-17"})
        self.assertEqual(409, self.client.delete(f"/family/{MAMA}").status_code)
        profile = self.client.patch(f"/family/{MAMA}", json={"display_name": None, "birth_date": None}).get_json()
        self.assertFalse(profile["manual"])
        self.assertEqual([], self.client.get("/family").get_json()["profiles"])

    def test_birth_date_on_an_immich_user_keeps_the_row_alive(self):
        self.client.patch(f"/family/{MAMA}", json={"birth_date": "1985-05-17"})
        profile = self.client.get("/family").get_json()["profiles"][0]
        self.assertEqual("1985-05-17", profile["birth_date"])
        self.assertIsNone(profile["display_name"])
        self.assertFalse(profile["manual"])
        # A profile photo removal doesn't drop a row that still has a birth date.
        self.put_photo(MAMA, image_bytes("JPEG"))
        self.client.delete(f"/family/{MAMA}/photo")
        self.assertEqual(1, len(self.client.get("/family").get_json()["profiles"]))

    def test_birth_date_validation(self):
        # The test clock is 2026-11-10.
        for good in ("1990-05-17", "--05-17", "--02-29", "2000-02-29", "2026-11-10", "2026-11-11", None):
            r = self.client.patch(f"/family/{MAMA}", json={"birth_date": good})
            self.assertEqual(200, r.status_code, good)
        for bad in ("2026-11-12", "2030-01-01", "1850-01-01", "1990-02-30", "--02-30", "--13-01", "2001-02-29",
                    "17-05-1990", "1990-5-17", "text", "", 19900517, True, ["1990-05-17"]):
            r = self.client.patch(f"/family/{MAMA}", json={"birth_date": bad})
            self.assertEqual(400, r.status_code, bad)
        for body in ({}, {"display_name": ""}, {"display_name": "   "}, {"display_name": None},
                     {"display_name": "x" * 51}, {"display_name": "Ola", "birth_date": "2090-01-01"}):
            self.assertEqual(400, self.client.post("/family/members", json=body).status_code, body)
        self.assertEqual(400, self.client.post("/family/members", json=["a"]).status_code)
        self.assertEqual(400, self.client.delete("/family/not-a-uuid").status_code)

    def test_manual_member_endpoints_require_a_token(self):
        member = self.client.post("/family/members", json={"display_name": "Ola"}).get_json()
        anon = {"Authorization": ""}
        self.assertEqual(401, self.client.post("/family/members", json={"display_name": "x"}, headers=anon).status_code)
        self.assertEqual(401, self.client.delete(f"/family/{member['user_id']}", headers=anon).status_code)
        self.assertEqual(1, len(self.client.get("/family").get_json()["profiles"]))

    def test_v1_database_is_migrated_without_losing_data(self):
        old_db = os.path.join(self.dir, "old.db")
        with closing(sqlite3.connect(old_db)) as conn, conn:
            conn.executescript(
                "CREATE TABLE profiles (user_id TEXT PRIMARY KEY, display_name TEXT, photo TEXT, hidden INTEGER,"
                " updated_by TEXT NOT NULL, updated TEXT NOT NULL);"
                f"INSERT INTO profiles VALUES ('{MAMA}', 'Mama Anna', 'abc.jpg', 0, '{MAMA}', '2026-11-01T10:00:00Z');"
                f"INSERT INTO profiles VALUES ('{TATA}', NULL, NULL, 1, '{MAMA}', '2026-11-02T10:00:00Z');"
                "PRAGMA user_version = 1;"
            )
        migrated = FamilyStore(old_db, self.photos)
        profiles = {p["user_id"]: p for p in migrated.profiles()}
        self.assertEqual("Mama Anna", profiles[MAMA]["display_name"])
        self.assertTrue(profiles[MAMA]["photo_url"].endswith("abc.jpg"))
        self.assertIs(False, profiles[MAMA]["hidden"])
        self.assertIs(True, profiles[TATA]["hidden"])
        self.assertFalse(profiles[MAMA]["manual"])
        self.assertIsNone(profiles[MAMA]["birth_date"])
        self.assertTrue(os.path.exists(old_db + ".v1.bak"))
        with closing(sqlite3.connect(old_db + ".v1.bak")) as conn:
            self.assertEqual(2, conn.execute("SELECT count(*) FROM profiles").fetchone()[0])
            self.assertEqual(1, conn.execute("PRAGMA user_version").fetchone()[0])
        with closing(sqlite3.connect(old_db)) as conn:
            self.assertEqual(2, conn.execute("PRAGMA user_version").fetchone()[0])
        # Reopening a current database changes nothing.
        self.assertEqual(2, len(FamilyStore(old_db, self.photos).profiles()))
        # The migrated database accepts the new features.
        migrated.add_member(MAMA, {"display_name": "Babcia"})
        self.assertEqual(3, len(migrated.profiles()))

    def test_family_member_creates_birthday_event_and_updates_and_deletes(self):
        # Adding a member with birth_date creates a birthday event
        r = self.client.post("/family/members", json={"display_name": "Ola", "birth_date": "2015-06-12"})
        self.assertEqual(201, r.status_code)
        member_id = r.get_json()["user_id"]

        events = self.client.get("/events?after=2026-01-01").get_json()
        bday = [e for e in events if e.get("member_id") == member_id]
        self.assertEqual(1, len(bday))
        self.assertEqual("Ola", bday[0]["title"])
        self.assertEqual("birthday", bday[0]["kind"])
        self.assertEqual("2015-06-12", bday[0]["start_date"])
        self.assertEqual("2026-06-12", bday[0]["occurrence_start"])
        self.assertEqual("yearly", bday[0]["repeat"])
        self.assertEqual(11, bday[0]["turns"])

        # Renaming the member updates the event's title
        self.client.patch(f"/family/{member_id}", json={"display_name": "Aleksandra"})
        events = self.client.get("/events?after=2026-01-01").get_json()
        bday = [e for e in events if e.get("member_id") == member_id]
        self.assertEqual("Aleksandra", bday[0]["title"])

        # Changing the birth date updates the start_date and turns
        self.client.patch(f"/family/{member_id}", json={"birth_date": "2014-06-12"})
        events = self.client.get("/events?after=2026-01-01").get_json()
        bday = [e for e in events if e.get("member_id") == member_id]
        self.assertEqual("2014-06-12", bday[0]["start_date"])
        self.assertEqual(12, bday[0]["turns"])

        # Clearing the birth date deletes the birthday event
        self.client.patch(f"/family/{member_id}", json={"birth_date": None})
        events = self.client.get("/events?after=2026-01-01").get_json()
        self.assertEqual([], [e for e in events if e.get("member_id") == member_id])

    def test_family_member_birthday_without_year(self):
        r = self.client.post("/family/members", json={
            "display_name": "Babcia",
            "birth_date": "--12-05",
            "today": "2026-11-11",
        })
        self.assertEqual(201, r.status_code)
        member_id = r.get_json()["user_id"]

        events = self.client.get("/events?after=2026-01-01").get_json()
        bday = [e for e in events if e.get("member_id") == member_id]
        self.assertEqual(1, len(bday))
        self.assertEqual("2026-12-05", bday[0]["start_date"])
        self.assertEqual("2026-12-05", bday[0]["occurrence_start"])
        self.assertIsNone(bday[0]["turns"])

    def test_delete_manual_member_removes_birthday_event(self):
        r = self.client.post("/family/members", json={"display_name": "Dziadek", "birth_date": "1950-03-10"})
        self.assertEqual(201, r.status_code)
        member_id = r.get_json()["user_id"]

        events = self.client.get("/events?after=2026-01-01").get_json()
        self.assertEqual(1, len([e for e in events if e.get("member_id") == member_id]))

        del_r = self.client.delete(f"/family/{member_id}")
        self.assertEqual(204, del_r.status_code)

        events_after = self.client.get("/events?after=2026-01-01").get_json()
        self.assertEqual([], [e for e in events_after if e.get("member_id") == member_id])

    def test_immich_user_birthday_cleared_when_profile_empty(self):
        self.client.patch(f"/family/{MAMA}", json={"birth_date": "1980-05-15"})
        events = self.client.get("/events?after=2026-01-01").get_json()
        bday = [e for e in events if e.get("member_id") == MAMA]
        self.assertEqual(1, len(bday))
        self.assertEqual("Birthday", bday[0]["title"])

        self.client.patch(f"/family/{MAMA}", json={"display_name": "Mama"})
        events = self.client.get("/events?after=2026-01-01").get_json()
        self.assertEqual("Mama", [e for e in events if e.get("member_id") == MAMA][0]["title"])

        # When clearing all profile fields, member row is deleted and birthday event is removed
        self.client.patch(f"/family/{MAMA}", json={"birth_date": None, "display_name": None})
        events_after = self.client.get("/events?after=2026-01-01").get_json()
        self.assertEqual([], [e for e in events_after if e.get("member_id") == MAMA])


if __name__ == "__main__":
    unittest.main()
