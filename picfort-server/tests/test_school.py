import os
import sqlite3
import tempfile
import unittest
from contextlib import closing
from io import BytesIO

from helpers import FakeImmich
from test_wall import MAMA, TATA, Clock, FakeImmichAuth, image_bytes

import frame
from app import FrameController, create_app
from auth import Authenticator
from school import SchoolStore
from slideshow import Picker, SettingsStore, Slideshow
from wall import WallStore

PERSON = "44444444-4444-4444-4444-444444444444"
PERSON_B = "55555555-5555-5555-5555-555555555555"
BELLS = [
    {"number": 1, "start_time": "08:00", "end_time": "08:45"},
    {"number": 2, "start_time": "08:55", "end_time": "09:40"},
    {"number": 3, "start_time": "09:50", "end_time": "10:35"},
]


class SchoolApiTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.db = os.path.join(self.dir, "school.db")
        self.photos = os.path.join(self.dir, "child_photos")
        self.store = SchoolStore(self.db, self.photos, clock=Clock())
        self.wall_db = os.path.join(self.dir, "wall.db")
        wall = WallStore(self.wall_db, os.path.join(self.dir, "wall_photos"), clock=Clock())
        cfg = frame.Config(immich_url="http://immich", immich_api_key="k",
                           preview_path=os.path.join(self.dir, "p.png"))
        controller = FrameController(cfg, min_interval=30, display=lambda img: None, prepare=lambda a: a)
        slideshow = Slideshow(SettingsStore(os.path.join(self.dir, "s.json")), Picker(FakeImmich()),
                              controller.try_show)
        self.client = create_app(controller, slideshow, FakeImmich(), wall, Authenticator(FakeImmichAuth()),
                                 school=self.store).test_client()
        self.client.environ_base["HTTP_AUTHORIZATION"] = "Bearer mama-token"

    TATA_AUTH = {"Authorization": "Bearer tata-token"}

    # --- helpers ---

    def child(self, name="Ola", color="pink"):
        r = self.client.post("/school/children", json={"name": name, "color": color})
        self.assertEqual(201, r.status_code, r.get_json())
        return r.get_json()

    def bells(self, child_id, periods=BELLS):
        r = self.client.put(f"/school/children/{child_id}/periods", json=periods)
        self.assertEqual(200, r.status_code, r.get_json())
        return r.get_json()

    def entry(self, child_id, expect=201, **body):
        r = self.client.post(f"/school/children/{child_id}/entries", json=body)
        self.assertEqual(expect, r.status_code, r.get_json())
        return r.get_json()

    def lesson(self, child_id, weekday, period, title="Maths", **extra):
        return self.entry(child_id, kind="lesson", weekday=weekday, period=period, title=title, **extra)

    def get(self, child_id, headers=None):
        children = self.client.get("/school", headers=headers or {}).get_json()["children"]
        return next(c for c in children if c["id"] == child_id)

    def put_photo(self, child_id, data, filename="face.jpg"):
        form = {"photo": (BytesIO(data), filename)} if data is not None else {}
        return self.client.put(f"/school/children/{child_id}/photo", data=form, content_type="multipart/form-data")

    # --- children ---

    def test_empty(self):
        self.assertEqual({"children": []}, self.client.get("/school").get_json())

    def test_needs_token(self):
        r = self.client.get("/school", headers={"Authorization": ""})
        self.assertEqual(401, r.status_code)

    def test_create_rename_recolor_and_order_by_name(self):
        ola = self.child("  Ola  ", "pink")
        self.assertEqual({
            "id": ola["id"], "name": "Ola", "color": "pink", "immich_user_id": None, "photo_url": None,
            "my_person_id": None, "created_by": MAMA, "updated": "2026-11-10T12:01:00Z",
            "periods": [], "entries": [],
        }, ola)
        stas = self.child("Adam", "blue")
        self.assertEqual(["Adam", "Ola"], [c["name"] for c in self.client.get("/school").get_json()["children"]])

        r = self.client.patch(f"/school/children/{stas['id']}", json={"name": "Staś", "color": "green"},
                              headers=self.TATA_AUTH)
        self.assertEqual(200, r.status_code)
        self.assertEqual(("Staś", "green"), (r.get_json()["name"], r.get_json()["color"]))
        r = self.client.patch(f"/school/children/{stas['id']}", json={"immich_user_id": TATA})
        self.assertEqual(TATA, r.get_json()["immich_user_id"])
        self.assertEqual(["Ola", "Staś"], [c["name"] for c in self.client.get("/school").get_json()["children"]])

    def test_child_from_a_family_member(self):
        r = self.client.post("/school/children", json={"name": "Ola", "color": "pink", "immich_user_id": TATA})
        self.assertEqual(201, r.status_code, r.get_json())
        self.assertEqual(TATA, r.get_json()["immich_user_id"])
        r = self.client.post("/school/children", json={"name": "Ola", "color": "pink", "immich_user_id": "ola"})
        self.assertEqual(400, r.status_code)

    def test_child_validation(self):
        for body in ({"color": "pink"}, {"name": " ", "color": "pink"}, {"name": "x" * 51, "color": "pink"},
                     {"name": "Ola", "color": "teal"}, {"name": "Ola"}):
            r = self.client.post("/school/children", json=body)
            self.assertEqual(400, r.status_code, body)
        self.assertEqual(400, self.client.post("/school/children", json=["Ola"]).status_code)
        ola = self.child()
        self.assertEqual(400, self.client.patch(f"/school/children/{ola['id']}",
                                                json={"immich_user_id": "x"}).status_code)
        self.assertEqual(404, self.client.patch("/school/children/99", json={"name": "A"}).status_code)
        self.assertEqual(404, self.client.delete("/school/children/99").status_code)

    def test_delete_cascades(self):
        ola = self.child()
        self.bells(ola["id"])
        self.lesson(ola["id"], 1, 1)
        self.client.put(f"/school/children/{ola['id']}/person", json={"person_id": PERSON})
        self.client.put(f"/school/children/{ola['id']}/person", json={"person_id": PERSON_B}, headers=self.TATA_AUTH)
        self.put_photo(ola["id"], image_bytes())
        self.assertEqual(1, len(os.listdir(self.photos)))

        self.assertEqual(204, self.client.delete(f"/school/children/{ola['id']}", headers=self.TATA_AUTH).status_code)
        self.assertEqual([], self.client.get("/school").get_json()["children"])
        self.assertEqual([], os.listdir(self.photos))
        with closing(sqlite3.connect(self.db)) as conn:
            for table in ("periods", "entries", "child_people"):
                self.assertEqual(0, conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0], table)

    # --- photo ---

    def test_photo_upload_replace_delete(self):
        ola = self.child()
        r = self.put_photo(ola["id"], image_bytes())
        self.assertEqual(200, r.status_code, r.get_json())
        first = r.get_json()["photo_url"]
        self.assertTrue(first.startswith("/school/children/photo/"))
        got = self.client.get(first)
        self.assertEqual(200, got.status_code)
        self.assertIn("private", got.headers["Cache-Control"])
        got.close()

        second = self.put_photo(ola["id"], image_bytes("PNG"), "face.png").get_json()["photo_url"]
        self.assertNotEqual(first, second)
        self.assertEqual(404, self.client.get(first).status_code)
        self.assertEqual(1, len(os.listdir(self.photos)))

        self.assertEqual(204, self.client.delete(f"/school/children/{ola['id']}/photo").status_code)
        self.assertIsNone(self.get(ola["id"])["photo_url"])
        self.assertEqual([], os.listdir(self.photos))

    def test_photo_validation(self):
        ola = self.child()
        self.assertEqual(400, self.put_photo(ola["id"], None).status_code)
        self.assertEqual(415, self.put_photo(ola["id"], b"not an image").status_code)
        self.assertEqual(415, self.put_photo(ola["id"], image_bytes("GIF"), "a.gif").status_code)
        self.assertEqual(413, self.put_photo(ola["id"], b"x" * (2 * 1024 * 1024 + 1)).status_code)
        self.assertEqual(404, self.put_photo(99, image_bytes()).status_code)
        self.assertEqual(404, self.client.get("/school/children/photo/../school.db").status_code)
        self.assertEqual([], os.listdir(self.photos))

    # --- person links ---

    def test_person_links_are_per_caller(self):
        ola = self.child()
        r = self.client.put(f"/school/children/{ola['id']}/person", json={"person_id": PERSON})
        self.assertEqual(PERSON, r.get_json()["my_person_id"])
        self.assertIsNone(self.get(ola["id"], self.TATA_AUTH)["my_person_id"])

        self.client.put(f"/school/children/{ola['id']}/person", json={"person_id": PERSON_B}, headers=self.TATA_AUTH)
        self.assertEqual(PERSON, self.get(ola["id"])["my_person_id"])
        self.assertEqual(PERSON_B, self.get(ola["id"], self.TATA_AUTH)["my_person_id"])

        self.assertEqual(204, self.client.delete(f"/school/children/{ola['id']}/person").status_code)
        self.assertIsNone(self.get(ola["id"])["my_person_id"])
        self.assertEqual(PERSON_B, self.get(ola["id"], self.TATA_AUTH)["my_person_id"])

        self.assertEqual(400, self.client.put(f"/school/children/{ola['id']}/person",
                                              json={"person_id": "nope"}).status_code)
        self.assertEqual(404, self.client.put("/school/children/99/person", json={"person_id": PERSON}).status_code)

    # --- bell schedule ---

    def test_bell_schedule_replace_moves_lessons(self):
        ola = self.child()
        self.bells(ola["id"], list(reversed(BELLS)))
        self.assertEqual([1, 2, 3], [p["number"] for p in self.get(ola["id"])["periods"]])
        on_two = self.lesson(ola["id"], 1, 2)
        on_three = self.lesson(ola["id"], 2, 3, "Art")
        self.assertEqual(("08:55", "09:40"), (on_two["start_time"], on_two["end_time"]))

        child = self.bells(ola["id"], [{"number": 1, "start_time": "08:00", "end_time": "08:45"},
                                       {"number": 2, "start_time": "09:00", "end_time": "09:45"}])
        entries = {e["id"]: e for e in child["entries"]}
        self.assertEqual(("09:00", "09:45", 2), (entries[on_two["id"]]["start_time"],
                                                 entries[on_two["id"]]["end_time"], entries[on_two["id"]]["period"]))
        # Period 3 was removed: the lesson keeps its times, loses the period.
        self.assertEqual(("09:50", "10:35", None), (entries[on_three["id"]]["start_time"],
                                                    entries[on_three["id"]]["end_time"],
                                                    entries[on_three["id"]]["period"]))
        self.assertEqual([], self.bells(ola["id"], [])["periods"])

    def test_bell_schedule_validation(self):
        ola = self.child()
        url = f"/school/children/{ola['id']}/periods"
        for body in ({"number": 1}, [{"number": 1, "start_time": "8:00", "end_time": "08:45"}],
                     [{"number": 1, "start_time": "08:45", "end_time": "08:45"}],
                     [{"number": 16, "start_time": "08:00", "end_time": "08:45"}],
                     [{"number": -1, "start_time": "08:00", "end_time": "08:45"}],
                     [{"number": "1", "start_time": "08:00", "end_time": "08:45"}],
                     [{"number": 1, "start_time": "24:00", "end_time": "24:45"}],
                     BELLS + [{"number": 1, "start_time": "11:00", "end_time": "11:45"}], ["x"]):
            self.assertEqual(400, self.client.put(url, json=body).status_code, body)
        zero = [{"number": 0, "start_time": "07:10", "end_time": "07:55"}]
        self.assertEqual(0, self.bells(ola["id"], zero)["periods"][0]["number"])
        self.assertEqual(404, self.client.put("/school/children/99/periods", json=BELLS).status_code)

    def test_copy_periods(self):
        ola, stas = self.child("Ola"), self.child("Staś")
        self.bells(ola["id"])
        self.bells(stas["id"], [{"number": 2, "start_time": "10:00", "end_time": "10:45"},
                                {"number": 7, "start_time": "14:00", "end_time": "14:45"}])
        on_two = self.lesson(stas["id"], 1, 2)
        on_seven = self.lesson(stas["id"], 1, 7)
        r = self.client.post(f"/school/children/{stas['id']}/copy-periods", json={"from_child": ola["id"]})
        self.assertEqual(200, r.status_code, r.get_json())
        child = r.get_json()
        self.assertEqual(BELLS, child["periods"])
        entries = {e["id"]: e for e in child["entries"]}
        self.assertEqual(("08:55", 2), (entries[on_two["id"]]["start_time"], entries[on_two["id"]]["period"]))
        self.assertEqual(("14:00", None), (entries[on_seven["id"]]["start_time"], entries[on_seven["id"]]["period"]))
        url = f"/school/children/{stas['id']}/copy-periods"
        self.assertEqual(400, self.client.post(url, json={"from_child": stas["id"]}).status_code)
        self.assertEqual(400, self.client.post(url, json={"from_child": "1"}).status_code)
        self.assertEqual(404, self.client.post(url, json={"from_child": 99}).status_code)

    # --- entries ---

    def test_lesson_by_period_and_by_times_and_activity(self):
        ola = self.child()
        self.bells(ola["id"])
        by_period = self.lesson(ola["id"], 1, 1, place=" 12 ", teacher="Mrs Nowak")
        self.assertEqual({
            "id": by_period["id"], "kind": "lesson", "weekday": 1, "start_time": "08:00", "end_time": "08:45",
            "title": "Maths", "period": 1, "place": "12", "teacher": "Mrs Nowak", "note": None,
        }, by_period)
        # Times given with a period are ignored on create: the bell schedule decides.
        given = self.lesson(ola["id"], 2, 2, start_time="07:00", end_time="07:30")
        self.assertEqual("08:55", given["start_time"])
        custom = self.entry(ola["id"], kind="lesson", weekday=1, title="Choir", start_time="13:00", end_time="13:45")
        self.assertIsNone(custom["period"])
        activity = self.entry(ola["id"], kind="activity", weekday=1, title="Swimming", start_time="15:00",
                              end_time="16:00", place="Aquapark", note="bring swimsuit")
        self.assertEqual(("activity", "bring swimsuit"), (activity["kind"], activity["note"]))

        self.entry(ola["id"], 400, kind="activity", weekday=1, title="Chess", period=1)
        self.entry(ola["id"], 400, kind="lesson", weekday=1, title="Maths", period=5)
        self.entry(ola["id"], 400, kind="lesson", weekday=1, title="Maths")
        self.entry(ola["id"], 400, kind="activity", weekday=1, title="Chess", start_time="15:00")

    def test_entries_ordered_by_day_then_time(self):
        ola = self.child()
        self.bells(ola["id"])
        late = self.entry(ola["id"], kind="activity", weekday=1, title="Judo", start_time="16:00", end_time="17:00")
        tue = self.lesson(ola["id"], 2, 1)
        early = self.lesson(ola["id"], 1, 1)
        same = self.lesson(ola["id"], 1, 1, "Polish")
        self.assertEqual([early["id"], same["id"], late["id"], tue["id"]],
                         [e["id"] for e in self.get(ola["id"])["entries"]])

    def test_entry_validation(self):
        ola = self.child()
        base = {"kind": "activity", "weekday": 1, "title": "Judo", "start_time": "16:00", "end_time": "17:00"}
        for change in ({"kind": "trip"}, {"weekday": 0}, {"weekday": 8}, {"weekday": "1"}, {"title": ""},
                       {"title": "x" * 101}, {"start_time": "16:0"}, {"end_time": "15:00"},
                       {"place": "x" * 101}, {"teacher": "x" * 101}, {"note": "x" * 501}, {"title": 5}):
            self.entry(ola["id"], 400, **{**base, **change})
        self.assertEqual(400, self.client.post(f"/school/children/{ola['id']}/entries", json=[base]).status_code)
        self.assertEqual(404, self.client.post("/school/children/99/entries", json=base).status_code)
        for _ in range(20):
            self.entry(ola["id"], **base)  # overlaps are fine
        self.entry(ola["id"], 400, **base)
        self.entry(ola["id"], **{**base, "weekday": 2})

    def test_update_and_delete_entry(self):
        ola, stas = self.child("Ola"), self.child("Staś")
        self.bells(ola["id"])
        maths = self.lesson(ola["id"], 1, 1, place="12")
        url = f"/school/children/{ola['id']}/entries/{maths['id']}"

        r = self.client.patch(url, json={"period": 2, "place": None, "teacher": "Mr Kowal"}, headers=self.TATA_AUTH)
        self.assertEqual(200, r.status_code, r.get_json())
        self.assertEqual(("08:55", "09:40", None, "Mr Kowal"),
                         (r.get_json()["start_time"], r.get_json()["end_time"], r.get_json()["place"],
                          r.get_json()["teacher"]))
        # Times on a lesson that keeps its period are refused; dropping the period allows them.
        self.assertEqual(400, self.client.patch(url, json={"start_time": "11:00"}).status_code)
        r = self.client.patch(url, json={"period": None, "start_time": "11:00", "end_time": "11:45"})
        self.assertEqual(("11:00", None), (r.get_json()["start_time"], r.get_json()["period"]))
        self.assertEqual(400, self.client.patch(url, json={"end_time": "10:00"}).status_code)
        # An activity can't keep a period.
        self.client.patch(url, json={"period": 1})
        self.assertEqual(400, self.client.patch(url, json={"kind": "activity"}).status_code)
        r = self.client.patch(url, json={"kind": "activity", "period": None, "start_time": "15:00",
                                         "end_time": "16:00"})
        self.assertEqual("activity", r.get_json()["kind"])

        self.assertEqual(404, self.client.patch(f"/school/children/{stas['id']}/entries/{maths['id']}",
                                                json={"title": "X"}).status_code)
        self.assertEqual(404, self.client.delete(f"/school/children/{stas['id']}/entries/{maths['id']}").status_code)
        self.assertEqual(204, self.client.delete(url).status_code)
        self.assertEqual(404, self.client.delete(url).status_code)
        self.assertEqual([], self.get(ola["id"])["entries"])

    # --- copy day ---

    def test_copy_day_replaces_only_given_kinds(self):
        ola = self.child()
        self.bells(ola["id"])
        self.lesson(ola["id"], 1, 1, "Maths")
        self.lesson(ola["id"], 1, 2, "Polish")
        self.entry(ola["id"], kind="activity", weekday=1, title="Judo", start_time="16:00", end_time="17:00")
        self.lesson(ola["id"], 3, 1, "Art")
        self.entry(ola["id"], kind="activity", weekday=3, title="Swimming", start_time="15:00", end_time="16:00")

        r = self.client.post(f"/school/children/{ola['id']}/copy-day", json={"from": 1, "to": 3})
        self.assertEqual(200, r.status_code, r.get_json())
        wednesday = [(e["kind"], e["title"], e["period"]) for e in r.get_json()["entries"] if e["weekday"] == 3]
        self.assertEqual([("lesson", "Maths", 1), ("lesson", "Polish", 2), ("activity", "Swimming", None)],
                         wednesday)

        r = self.client.post(f"/school/children/{ola['id']}/copy-day",
                             json={"from": 1, "to": 3, "kinds": ["lesson", "activity"]})
        wednesday = [e["title"] for e in r.get_json()["entries"] if e["weekday"] == 3]
        self.assertEqual(["Maths", "Polish", "Judo"], wednesday)
        self.assertEqual(3, len([e for e in r.get_json()["entries"] if e["weekday"] == 1]))

        url = f"/school/children/{ola['id']}/copy-day"
        for body in ({"from": 1, "to": 1}, {"from": 1, "to": 8}, {"from": 1}, {"from": 1, "to": 2, "kinds": []},
                     {"from": 1, "to": 2, "kinds": ["trip"]}):
            self.assertEqual(400, self.client.post(url, json=body).status_code, body)
        self.assertEqual(404, self.client.post("/school/children/99/copy-day", json={"from": 1, "to": 2}).status_code)

    # --- schema ---

    def test_schema_change_keeps_backup_and_leaves_other_databases(self):
        self.child()
        self.client.post("/wall", data={"text": "hello"}, content_type="multipart/form-data")
        with closing(sqlite3.connect(self.db)) as conn:
            conn.execute("PRAGMA user_version = 99")
        SchoolStore(self.db, self.photos, clock=Clock())
        self.assertTrue(os.path.exists(self.db + ".v99.bak"))
        self.assertEqual([], self.client.get("/school").get_json()["children"])
        self.assertEqual(1, len(self.client.get("/wall").get_json()))


if __name__ == "__main__":
    unittest.main()
