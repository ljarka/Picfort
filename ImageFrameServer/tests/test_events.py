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
from datetime import date

from events import EventStore, MAX_LOCATION, MAX_NOTE, MAX_TITLE, last_occurrence, next_occurrence, occurrence_start
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


    # --- T-035: recurring events -----------------------------------------------------------

    def upcoming(self, after):
        return self.client.get(f"/events?after={after}").get_json()

    def past(self, before, **params):
        query = "".join(f"&{k}={v}" for k, v in params.items())
        return self.client.get(f"/events?before={before}{query}").get_json()

    def test_non_repeating_events_report_their_own_dates_as_occurrence(self):
        one_day = self.new_event(start_date="2026-11-20")
        trip = self.new_event(start_date="2026-11-10", end_date="2026-11-14")
        self.assertIsNone(one_day["repeat"])
        self.assertEqual(("2026-11-20", None), (one_day["occurrence_start"], one_day["occurrence_end"]))
        self.assertEqual(("2026-11-10", "2026-11-14"), (trip["occurrence_start"], trip["occurrence_end"]))

    def test_weekly_series_lists_its_next_occurrence_and_moves_on(self):
        ev = self.new_event(start_date="2026-11-02", repeat="weekly")
        self.assertEqual("weekly", ev["repeat"])
        self.assertEqual("2026-11-02", ev["start_date"])  # the form edits the first occurrence
        entry = self.upcoming("2026-11-11")[0]
        self.assertEqual("2026-11-16", entry["occurrence_start"])
        self.assertEqual("2026-11-02", entry["start_date"])
        self.assertEqual("2026-11-16", self.upcoming("2026-11-16")[0]["occurrence_start"])  # today counts
        self.assertEqual("2026-11-23", self.upcoming("2026-11-17")[0]["occurrence_start"])
        self.assertEqual([], self.past("2026-12-31"))  # repeats forever: never past

    def test_series_in_the_future_starts_at_its_first_occurrence(self):
        self.new_event(start_date="2027-03-01", repeat="yearly")
        self.assertEqual("2027-03-01", self.upcoming("2026-11-11")[0]["occurrence_start"])

    def test_monthly_31st_uses_the_last_day_of_shorter_months_without_drifting(self):
        row = {"start_date": "2026-01-31", "end_date": None, "repeat": "monthly", "repeat_until": None}
        starts = [occurrence_start(date(2026, 1, 31), "monthly", n).isoformat() for n in range(5)]
        self.assertEqual(["2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31"], starts)
        self.assertEqual((date(2026, 4, 30), date(2026, 4, 30)), next_occurrence(row, date(2026, 4, 1)))
        self.assertEqual((date(2026, 5, 31), date(2026, 5, 31)), next_occurrence(row, date(2026, 5, 1)))
        self.assertEqual((date(2028, 2, 29), date(2028, 2, 29)), next_occurrence(row, date(2028, 2, 1)))

    def test_yearly_29_february_falls_back_to_28_in_other_years(self):
        row = {"start_date": "2024-02-29", "end_date": None, "repeat": "yearly", "repeat_until": None}
        self.assertEqual((date(2025, 2, 28), date(2025, 2, 28)), next_occurrence(row, date(2024, 3, 1)))
        self.assertEqual((date(2028, 2, 29), date(2028, 2, 29)), next_occurrence(row, date(2027, 3, 1)))
        self.assertEqual((date(2026, 2, 28), date(2026, 2, 28)), next_occurrence(row, date(2026, 2, 28)))

    def test_multi_day_series_keeps_its_length_and_is_ongoing_during_an_occurrence(self):
        self.new_event(title="Camp", start_date="2026-07-30", end_date="2026-08-02", repeat="yearly")
        during = self.upcoming("2027-08-01")[0]
        self.assertEqual(("2027-07-30", "2027-08-02"), (during["occurrence_start"], during["occurrence_end"]))
        after = self.upcoming("2027-08-03")[0]
        self.assertEqual(("2028-07-30", "2028-08-02"), (after["occurrence_start"], after["occurrence_end"]))
        # A monthly series whose multi-day length crosses a month end.
        row = {"start_date": "2026-01-30", "end_date": "2026-02-02", "repeat": "monthly", "repeat_until": None}
        self.assertEqual((date(2026, 2, 28), date(2026, 3, 3)), next_occurrence(row, date(2026, 2, 3)))

    def test_repeat_until_is_inclusive_and_the_ended_series_moves_to_past(self):
        ev = self.new_event(title="Swimming", start_date="2026-11-02", repeat="weekly", repeat_until="2026-11-16")
        self.assertEqual("2026-11-16", self.upcoming("2026-11-16")[0]["occurrence_start"])  # last one, inclusive
        self.assertEqual([], self.past("2026-11-16"))  # still running today
        self.assertEqual([], self.upcoming("2026-11-17"))
        past = self.past("2026-11-17")
        self.assertEqual([ev["id"]], [e["id"] for e in past])
        self.assertEqual("2026-11-16", past[0]["occurrence_start"])  # its last occurrence
        # An end date between occurrences ends the series at the previous occurrence.
        self.new_event(title="Choir", start_date="2026-09-01", repeat="monthly", repeat_until="2026-11-30")
        self.assertEqual("2026-11-16", self.past("2026-12-01")[0]["occurrence_start"])
        self.assertEqual("2026-11-01", self.past("2026-12-01")[1]["occurrence_start"])

    def test_past_sorts_series_and_single_events_by_end_and_pages(self):
        a = self.new_event(title="a", start_date="2026-05-01")
        b = self.new_event(title="b", start_date="2026-03-01", repeat="monthly", repeat_until="2026-06-15")
        c = self.new_event(title="c", start_date="2026-01-01")
        ids = [e["id"] for e in self.past("2026-12-01")]
        self.assertEqual([b["id"], a["id"], c["id"]], ids)  # series' last occurrence is 2026-06-01
        self.assertEqual([a["id"]], [e["id"] for e in self.past("2026-12-01", limit=1, offset=1)])

    def test_upcoming_orders_series_and_single_events_together(self):
        late = self.new_event(title="late", start_date="2026-11-30")
        weekly = self.new_event(title="weekly", start_date="2026-11-01", repeat="weekly")  # next: 11-15
        timed = self.new_event(title="timed", start_date="2026-11-15", start_time="09:00")
        untimed = self.new_event(title="untimed", start_date="2026-11-15")
        ids = [e["id"] for e in self.upcoming("2026-11-11")]
        self.assertEqual([weekly["id"], untimed["id"], timed["id"], late["id"]], ids)
        self.assertEqual(weekly["id"], ids[0])

    def test_get_one_event_for_an_occurrence_date(self):
        ev = self.new_event(start_date="2026-11-02", repeat="weekly", repeat_until="2026-11-30")
        base = f"/events/{ev['id']}"
        self.assertEqual("2026-11-02", self.client.get(base).get_json()["occurrence_start"])
        self.assertEqual("2026-11-16", self.client.get(base + "?on=2026-11-11").get_json()["occurrence_start"])
        self.assertEqual("2026-11-30", self.client.get(base + "?on=2027-05-01").get_json()["occurrence_start"])
        self.assertEqual(400, self.client.get(base + "?on=yesterday").status_code)

    def test_repeat_validation_and_patch_rules(self):
        def post(**kw):
            return self.client.post("/events", json={"title": "x", "start_date": "2026-11-10", **kw})
        for bad in ({"repeat": "daily"}, {"repeat": 1}, {"repeat": ""}, {"repeat_until": "2026-12-01"},
                    {"repeat": "weekly", "repeat_until": "2026-11-09"},
                    {"repeat": "weekly", "repeat_until": "nope"}, {"repeat": "weekly", "repeat_until": "2026-02-30"}):
            self.assertEqual(400, post(**bad).status_code, bad)
        self.assertEqual(201, post(repeat="weekly", repeat_until="2026-11-10").status_code)  # same day is fine

        ev = self.new_event(repeat="monthly", repeat_until="2027-01-10")
        url = f"/events/{ev['id']}"
        # Moving the first date past the end of the series is refused.
        self.assertEqual(400, self.client.patch(url, json={"start_date": "2027-02-01"}).status_code)
        # Clearing repeat clears its end date too.
        r = self.client.patch(url, json={"repeat": None})
        self.assertEqual(200, r.status_code)
        self.assertIsNone(r.get_json()["repeat"])
        self.assertIsNone(r.get_json()["repeat_until"])
        # An end date with no repeat is refused, also together with a null repeat.
        self.assertEqual(400, self.client.patch(url, json={"repeat_until": "2027-01-10"}).status_code)
        self.assertEqual(400, self.client.patch(url, json={"repeat": None, "repeat_until": "2027-01-10"}).status_code)
        # Turning repeat on and off again keeps other fields.
        r = self.client.patch(url, json={"repeat": "yearly", "repeat_until": None})
        self.assertEqual("yearly", r.get_json()["repeat"])
        self.assertEqual("Mazury trip", r.get_json()["title"])

    def test_deleting_a_series_removes_it_and_creator_rule_still_applies(self):
        ev = self.new_event(repeat="weekly")
        self.assertEqual(403, self.client.delete(f"/events/{ev['id']}", headers=self.as_tata()).status_code)
        self.assertEqual(204, self.client.delete(f"/events/{ev['id']}").status_code)
        self.assertEqual([], self.upcoming("2026-11-11"))

    def test_v1_database_is_migrated_without_losing_events(self):
        old_db = os.path.join(self.dir, "old-events.db")
        with closing(sqlite3.connect(old_db)) as conn, conn:
            conn.executescript(
                "CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,"
                " start_date TEXT NOT NULL, end_date TEXT, start_time TEXT, location TEXT, note TEXT,"
                " album_id TEXT, created_by TEXT NOT NULL, created TEXT NOT NULL, updated TEXT NOT NULL);"
                "CREATE INDEX events_start ON events(start_date);"
                f"INSERT INTO events (title, start_date, end_date, created_by, created, updated)"
                f" VALUES ('Mazury', '2026-11-10', '2026-11-14', '{MAMA}', '2026-11-01T10:00:00Z', '2026-11-01T10:00:00Z');"
                "PRAGMA user_version = 1;"
            )
        store = EventStore(old_db)
        events = store.upcoming("2026-11-11")
        self.assertEqual(["Mazury"], [e["title"] for e in events])
        self.assertIsNone(events[0]["repeat"])
        self.assertEqual(("2026-11-10", "2026-11-14"), (events[0]["occurrence_start"], events[0]["occurrence_end"]))
        self.assertTrue(os.path.exists(old_db + ".v1.bak"))
        with closing(sqlite3.connect(old_db + ".v1.bak")) as conn:
            self.assertEqual(1, conn.execute("PRAGMA user_version").fetchone()[0])
        with closing(sqlite3.connect(old_db)) as conn:
            self.assertEqual(4, conn.execute("PRAGMA user_version").fetchone()[0])
        created = store.create(MAMA, {"title": "Weekly", "start_date": "2026-11-12", "repeat": "weekly"})
        self.assertEqual("weekly", created["repeat"])
        self.assertEqual(2, len(store.upcoming("2026-11-11")))

    def test_series_helpers_agree_with_each_other(self):
        row = {"start_date": "2026-01-31", "end_date": "2026-02-01", "repeat": "monthly", "repeat_until": "2026-12-15"}
        self.assertEqual((date(2026, 11, 30), date(2026, 12, 1)), last_occurrence(row))
        for day in range(0, 400, 7):
            on = date(2026, 1, 1) + __import__("datetime").timedelta(days=day)
            found = next_occurrence(row, on)
            if found is not None:
                self.assertGreaterEqual(found[1], on)
                self.assertLessEqual(found[0], date(2026, 12, 15))

    def test_post_events_rejects_kind_or_member_id(self):
        r1 = self.client.post("/events", json={"title": "Party", "start_date": "2026-11-20", "kind": "birthday"})
        self.assertEqual(400, r1.status_code)
        r2 = self.client.post("/events", json={"title": "Party", "start_date": "2026-11-20", "member_id": MAMA})
        self.assertEqual(400, r2.status_code)

    def test_birthday_event_patch_and_delete_rules(self):
        bday = self.store.sync_birthday(MAMA, "Mama", "2015-06-12", MAMA)
        self.assertEqual("birthday", bday["kind"])
        self.assertEqual(MAMA, bday["member_id"])
        self.assertEqual(11, self.store.upcoming("2026-01-01")[0]["turns"])

        ev_id = bday["id"]
        # Forbidden fields return 409
        for key, val in [("title", "New"), ("start_date", "2026-01-01"), ("end_date", "2026-01-02"),
                         ("repeat", "weekly"), ("repeat_until", "2030-01-01")]:
            r = self.client.patch(f"/events/{ev_id}", json={key: val})
            self.assertEqual(409, r.status_code, f"{key} should be forbidden")
            self.assertIn("Family settings", r.get_json()["message"])

        # Delete returns 409
        r = self.client.delete(f"/events/{ev_id}")
        self.assertEqual(409, r.status_code)

        # Allowed fields: note, album_id, location, start_time
        r = self.client.patch(f"/events/{ev_id}", json={
            "album_id": ALBUM_ID,
            "note": "Bring cake",
            "location": "Garden",
            "start_time": "15:00",
        })
        self.assertEqual(200, r.status_code)
        updated = r.get_json()
        self.assertEqual(ALBUM_ID, updated["album_id"])
        self.assertEqual("Bring cake", updated["note"])
        self.assertEqual("Garden", updated["location"])
        self.assertEqual("15:00", updated["start_time"])
        self.assertEqual("Mama", updated["title"])

    def test_v2_database_migration_to_v3_and_unique_index(self):
        v2_db = os.path.join(self.dir, "v2-events.db")
        with closing(sqlite3.connect(v2_db)) as conn, conn:
            conn.executescript(
                "CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,"
                " start_date TEXT NOT NULL, end_date TEXT, start_time TEXT, location TEXT, note TEXT,"
                " album_id TEXT, repeat TEXT, repeat_until TEXT, created_by TEXT NOT NULL, created TEXT NOT NULL, updated TEXT NOT NULL);"
                "CREATE INDEX events_start ON events(start_date);"
                f"INSERT INTO events (title, start_date, created_by, created, updated)"
                f" VALUES ('Old', '2026-11-10', '{MAMA}', '2026-11-01T10:00:00Z', '2026-11-01T10:00:00Z');"
                "PRAGMA user_version = 2;"
            )
        store = EventStore(v2_db)
        self.assertTrue(os.path.exists(v2_db + ".v2.bak"))
        with closing(sqlite3.connect(v2_db)) as conn:
            self.assertEqual(4, conn.execute("PRAGMA user_version").fetchone()[0])
            # Check unique index constraint on member_id
            conn.execute("INSERT INTO events (title, start_date, kind, member_id, created_by, created, updated)"
                         f" VALUES ('B1', '2026-11-10', 'birthday', '{MAMA}', '{MAMA}', 'now', 'now')")
            with self.assertRaises(sqlite3.IntegrityError):
                conn.execute("INSERT INTO events (title, start_date, kind, member_id, created_by, created, updated)"
                             f" VALUES ('B2', '2026-11-10', 'birthday', '{MAMA}', '{MAMA}', 'now', 'now')")

    def test_feb_29_birthday_turns(self):
        self.store.sync_birthday(MAMA, "Leap Baby", "2000-02-29", MAMA)
        # Non-leap year 2025: clamped to Feb 28, turns 25
        up_2025 = self.store.upcoming("2025-01-01")
        self.assertEqual("2025-02-28", up_2025[0]["occurrence_start"])
        self.assertEqual(25, up_2025[0]["turns"])
        # Leap year 2028: Feb 29, turns 28
        up_2028 = self.store.upcoming("2028-01-01")
        self.assertEqual("2028-02-29", up_2028[0]["occurrence_start"])
        self.assertEqual(28, up_2028[0]["turns"])

    # --- T-053: who the event is for ---

    def test_members_saved_in_order_without_repeats_and_kept_on_edit(self):
        e = self.new_event(members=["ola", MAMA, "ola"])
        self.assertEqual(["ola", MAMA], e["members"])
        r = self.client.patch(f"/events/{e['id']}", json={"title": "Mazury"})
        self.assertEqual(["ola", MAMA], r.get_json()["members"])
        self.assertEqual(["ola", MAMA], self.client.get(f"/events/{e['id']}").get_json()["members"])
        r = self.client.patch(f"/events/{e['id']}", json={"members": []})
        self.assertEqual([], r.get_json()["members"])
        r = self.client.patch(f"/events/{e['id']}", json={"members": ["kuba"]})
        self.assertEqual(["kuba"], r.get_json()["members"])
        self.assertEqual(["kuba"], self.client.get("/events?after=2026-11-01").get_json()[0]["members"])

    def test_event_without_members_has_an_empty_list(self):
        self.assertEqual([], self.new_event()["members"])

    def test_members_are_validated(self):
        for bad in ("ola", [1], ["a b"], ["x" * 65], [f"m{i}" for i in range(31)]):
            r = self.client.post("/events", json={"title": "T", "start_date": "2026-11-10", "members": bad})
            self.assertEqual(400, r.status_code, bad)

    def test_v3_database_migration_to_v4_keeps_events(self):
        v3_db = os.path.join(self.dir, "v3-events.db")
        with closing(sqlite3.connect(v3_db)) as conn, conn:
            conn.executescript(
                "CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,"
                " start_date TEXT NOT NULL, end_date TEXT, start_time TEXT, location TEXT, note TEXT,"
                " album_id TEXT, repeat TEXT, repeat_until TEXT, kind TEXT, member_id TEXT,"
                " created_by TEXT NOT NULL, created TEXT NOT NULL, updated TEXT NOT NULL);"
                "CREATE INDEX events_start ON events(start_date);"
                f"INSERT INTO events (title, start_date, created_by, created, updated)"
                f" VALUES ('Old', '2026-11-10', '{MAMA}', '2026-11-01T10:00:00Z', '2026-11-01T10:00:00Z');"
                "PRAGMA user_version = 3;"
            )
        store = EventStore(v3_db)
        self.assertTrue(os.path.exists(v3_db + ".v3.bak"))
        old = store.get(1)
        self.assertEqual("Old", old["title"])
        self.assertEqual([], old["members"])
        self.assertEqual(["ola"], store.update(1, {"members": ["ola"]})["members"])


if __name__ == "__main__":
    unittest.main()
