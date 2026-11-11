import os
import sqlite3
import tempfile
import unittest
from contextlib import closing

from helpers import FakeImmich
from test_wall import MAMA, TATA, Clock, FakeImmichAuth

import frame
from app import FrameController, create_app
from auth import Authenticator
from events import EventStore, MAX_LOCATION, MAX_NOTE, MAX_TITLE
from shopping import ShoppingStore
from slideshow import Picker, SettingsStore, Slideshow
from wall import WallStore

ALBUM_ID = "11111111-2222-3333-4444-555555555555"


class EventsApiTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.db = os.path.join(self.dir, "events.db")
        self.wall_db = os.path.join(self.dir, "wall.db")
        self.shopping_db = os.path.join(self.dir, "shopping.db")
        self.clock = Clock()
        self.store = EventStore(self.db, clock=self.clock)
        self.wall = WallStore(self.wall_db, os.path.join(self.dir, "wall_photos"), clock=self.clock)
        self.shopping = ShoppingStore(self.shopping_db, clock=self.clock)
        self.client = self._make_client()
        self.client.environ_base["HTTP_AUTHORIZATION"] = "Bearer mama-token"

    def _make_client(self, api_key: str = ""):
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
        return create_app(
            controller,
            slideshow,
            FakeImmich(),
            self.wall,
            Authenticator(FakeImmichAuth()),
            api_key=api_key,
            shopping=self.shopping,
            events=self.store,
        ).test_client()

    def as_tata(self):
        return {"Authorization": "Bearer tata-token"}

    def new_event(self, **kwargs):
        body = {"title": "Mazury trip", "start_date": "2026-11-10", **kwargs}
        r = self.client.post("/events", json=body)
        self.assertEqual(201, r.status_code, r.get_json())
        return r.get_json()

    def test_create_read_update_and_clear_optional_fields(self):
        ev = self.new_event(
            title="  Mazury trip  ",
            start_date="2026-11-10",
            end_date="2026-11-14",
            start_time="09:30",
            location="  Giżycko  ",
            note="  Take raincoats  ",
            album_id=ALBUM_ID,
        )
        self.assertEqual("Mazury trip", ev["title"])
        self.assertEqual("2026-11-10", ev["start_date"])
        self.assertEqual("2026-11-14", ev["end_date"])
        self.assertEqual("09:30", ev["start_time"])
        self.assertEqual("Giżycko", ev["location"])
        self.assertEqual("Take raincoats", ev["note"])
        self.assertEqual(ALBUM_ID, ev["album_id"])
        self.assertEqual(MAMA, ev["created_by"])
        self.assertEqual(ev, self.client.get(f"/events/{ev['id']}").get_json())

        # Anyone in the family can edit; null clears optional fields.
        r = self.client.patch(
            f"/events/{ev['id']}",
            json={
                "title": "Mazury weekend",
                "end_date": None,
                "start_time": None,
                "location": None,
                "note": None,
                "album_id": None,
            },
            headers=self.as_tata(),
        )
        self.assertEqual(200, r.status_code, r.get_json())
        updated = r.get_json()
        self.assertEqual("Mazury weekend", updated["title"])
        self.assertEqual("2026-11-10", updated["start_date"])
        self.assertIsNone(updated["end_date"])
        self.assertIsNone(updated["start_time"])
        self.assertIsNone(updated["location"])
        self.assertIsNone(updated["note"])
        self.assertIsNone(updated["album_id"])
        self.assertEqual(MAMA, updated["created_by"])
        self.assertNotEqual(ev["updated"], updated["updated"])

    def test_end_date_equal_to_start_date_is_stored_as_null(self):
        ev = self.new_event(start_date="2026-11-11", end_date="2026-11-11")
        self.assertIsNone(ev["end_date"])

        multi = self.new_event(start_date="2026-11-10", end_date="2026-11-14")
        self.assertEqual("2026-11-14", multi["end_date"])
        # Moving start_date to equal stored end_date clears end_date.
        r = self.client.patch(f"/events/{multi['id']}", json={"start_date": "2026-11-14"})
        self.assertEqual(200, r.status_code, r.get_json())
        self.assertIsNone(r.get_json()["end_date"])

    def test_upcoming_and_past_split_around_ongoing_trip(self):
        ended_yesterday = self.new_event(title="Ended trip", start_date="2026-11-08", end_date="2026-11-10")
        one_day_yesterday = self.new_event(title="Yesterday", start_date="2026-11-10")
        ongoing = self.new_event(title="Ongoing trip", start_date="2026-11-10", end_date="2026-11-12")
        ends_today = self.new_event(title="Ends today", start_date="2026-11-09", end_date="2026-11-11")
        today_one_day = self.new_event(title="Today", start_date="2026-11-11")
        future = self.new_event(title="Christmas Eve", start_date="2026-12-24")

        upcoming = self.client.get("/events?after=2026-11-11").get_json()
        self.assertEqual(
            [ends_today["id"], ongoing["id"], today_one_day["id"], future["id"]],
            [e["id"] for e in upcoming],
        )

        past = self.client.get("/events?before=2026-11-11").get_json()
        self.assertEqual(
            [one_day_yesterday["id"], ended_yesterday["id"]],
            [e["id"] for e in past],
        )

    def test_upcoming_ordering_untimed_first_then_time_then_id(self):
        late = self.new_event(title="Dinner", start_date="2026-11-15", start_time="19:00")
        untimed_1 = self.new_event(title="Birthday A", start_date="2026-11-15")
        early = self.new_event(title="Breakfast", start_date="2026-11-15", start_time="08:30")
        untimed_2 = self.new_event(title="Birthday B", start_date="2026-11-15")
        next_day = self.new_event(title="Next day", start_date="2026-11-16", start_time="07:00")

        upcoming = self.client.get("/events?after=2026-11-15").get_json()
        self.assertEqual(
            [untimed_1["id"], untimed_2["id"], early["id"], late["id"], next_day["id"]],
            [e["id"] for e in upcoming],
        )

    def test_past_ordering_and_paging(self):
        e1 = self.new_event(title="Oct", start_date="2026-10-01")
        e2 = self.new_event(title="Early Nov trip", start_date="2026-11-01", end_date="2026-11-08")
        e3 = self.new_event(title="Mid Nov", start_date="2026-11-05")

        # Ordered by coalesce(end_date, start_date) DESC: e2 ends 11-08, e3 ends 11-05, e1 ends 10-01.
        all_past = self.client.get("/events?before=2026-11-11").get_json()
        self.assertEqual([e2["id"], e3["id"], e1["id"]], [e["id"] for e in all_past])

        page1 = self.client.get("/events?before=2026-11-11&limit=2&offset=0").get_json()
        self.assertEqual([e2["id"], e3["id"]], [e["id"] for e in page1])

        page2 = self.client.get("/events?before=2026-11-11&limit=2&offset=2").get_json()
        self.assertEqual([e1["id"]], [e["id"] for e in page2])

    def test_delete_only_by_creator(self):
        ev = self.new_event()
        self.assertEqual(403, self.client.delete(f"/events/{ev['id']}", headers=self.as_tata()).status_code)
        self.assertEqual(204, self.client.delete(f"/events/{ev['id']}").status_code)
        self.assertEqual(404, self.client.get(f"/events/{ev['id']}").status_code)
        self.assertEqual(404, self.client.delete(f"/events/{ev['id']}").status_code)

    def test_validation_and_patch_against_stored_values(self):
        base = {"title": "Trip", "start_date": "2026-11-10"}
        bad_bodies = [
            {},
            {"start_date": "2026-11-10"},
            {**base, "title": ""},
            {**base, "title": "   "},
            {**base, "title": "x" * (MAX_TITLE + 1)},
            {**base, "title": 123},
            {"title": "Trip"},
            {**base, "start_date": "10-11-2026"},
            {**base, "start_date": "2026-02-30"},
            {**base, "start_date": "2026-13-01"},
            {**base, "start_date": 20261110},
            {**base, "end_date": "2026-11-09"},
            {**base, "end_date": "2026-02-29"},
            {**base, "end_date": "not-a-date"},
            {**base, "start_time": "9:00"},
            {**base, "start_time": "24:00"},
            {**base, "start_time": "18:60"},
            {**base, "start_time": "18:00:00"},
            {**base, "start_time": 1800},
            {**base, "location": "x" * (MAX_LOCATION + 1)},
            {**base, "location": 42},
            {**base, "note": "x" * (MAX_NOTE + 1)},
            {**base, "note": ["a"]},
            {**base, "album_id": "not-a-uuid"},
            {**base, "album_id": 123},
        ]
        for body in bad_bodies:
            r = self.client.post("/events", json=body)
            self.assertEqual(400, r.status_code, body)

        self.assertEqual(
            400,
            self.client.post("/events", data="not json", content_type="application/json").status_code,
        )
        self.assertEqual(400, self.client.post("/events", json=["a"]).status_code)

        ev = self.new_event(start_date="2026-11-10", end_date="2026-11-14")
        # Changing only start_date past the stored end_date is 400.
        self.assertEqual(
            400,
            self.client.patch(f"/events/{ev['id']}", json={"start_date": "2026-11-15"}).status_code,
        )
        # Setting end_date before the stored start_date is 400.
        self.assertEqual(
            400,
            self.client.patch(f"/events/{ev['id']}", json={"end_date": "2026-11-09"}).status_code,
        )
        # Clearing required fields is 400.
        self.assertEqual(
            400,
            self.client.patch(f"/events/{ev['id']}", json={"title": None}).status_code,
        )
        self.assertEqual(
            400,
            self.client.patch(f"/events/{ev['id']}", json={"start_date": None}).status_code,
        )
        self.assertEqual(404, self.client.patch("/events/999", json={"title": "x"}).status_code)

        # Query parameter validation on GET /events.
        for query in (
            "/events",
            "/events?after=2026-11-11&before=2026-11-11",
            "/events?after=bad",
            "/events?before=2026-02-30",
            "/events?before=2026-11-11&limit=0",
            "/events?before=2026-11-11&limit=101",
            "/events?before=2026-11-11&limit=abc",
            "/events?before=2026-11-11&offset=-1",
            "/events?before=2026-11-11&offset=abc",
        ):
            self.assertEqual(400, self.client.get(query).status_code, query)

    def test_auth_and_optional_api_key(self):
        ev = self.new_event()
        for method, path in (
            (self.client.get, "/events?after=2026-11-11"),
            (self.client.post, "/events"),
            (self.client.get, f"/events/{ev['id']}"),
            (self.client.patch, f"/events/{ev['id']}"),
            (self.client.delete, f"/events/{ev['id']}"),
        ):
            r = method(path, headers={"Authorization": ""})
            self.assertEqual(401, r.status_code, path)

        keyed = self._make_client(api_key="secret-key")
        self.assertEqual(
            401,
            keyed.get("/events?after=2026-11-11", headers={"Authorization": "Bearer mama-token"}).status_code,
        )
        self.assertEqual(
            200,
            keyed.get(
                "/events?after=2026-11-11",
                headers={"Authorization": "Bearer mama-token", "X-Picfort-Key": "secret-key"},
            ).status_code,
        )

    def test_schema_change_keeps_backup_and_leaves_wall_and_shopping_alone(self):
        self.new_event()
        self.client.post("/wall", data={"text": "hello"}, content_type="multipart/form-data")
        self.client.post("/lists", json={"title": "Groceries", "items": ["milk"]})

        with closing(sqlite3.connect(self.db)) as conn:
            conn.execute("PRAGMA user_version = 99")

        reopened = EventStore(self.db)
        self.assertEqual([], reopened.upcoming("2000-01-01"))
        self.assertTrue(os.path.exists(self.db + ".v99.bak"))
        self.assertEqual(1, len(WallStore(self.wall_db, os.path.join(self.dir, "wall_photos")).list()))
        self.assertEqual(1, len(ShoppingStore(self.shopping_db).lists()))


if __name__ == "__main__":
    unittest.main()
