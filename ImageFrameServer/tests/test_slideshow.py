import os
import tempfile
import unittest
from datetime import date, datetime

from helpers import Clock, FakeImmich

from slideshow import (Picker, SettingsStore, Slideshow, SlideshowSettings, in_quiet_hours,
                       on_this_day_ranges)


class QuietHoursTest(unittest.TestCase):
    def test_wraps_midnight(self):
        s = SlideshowSettings(quiet_start=22, quiet_end=7)
        self.assertTrue(in_quiet_hours(s, datetime(2025, 1, 1, 23)))
        self.assertTrue(in_quiet_hours(s, datetime(2025, 1, 1, 6, 59)))
        self.assertFalse(in_quiet_hours(s, datetime(2025, 1, 1, 7)))
        self.assertFalse(in_quiet_hours(s, datetime(2025, 1, 1, 21, 59)))

    def test_same_day_range_and_off(self):
        s = SlideshowSettings(quiet_start=1, quiet_end=5)
        self.assertTrue(in_quiet_hours(s, datetime(2025, 1, 1, 3)))
        self.assertFalse(in_quiet_hours(s, datetime(2025, 1, 1, 5)))
        self.assertFalse(in_quiet_hours(SlideshowSettings(), datetime(2025, 1, 1, 3)))


class SettingsTest(unittest.TestCase):
    def test_validation(self):
        for bad in [{"mode": "x"}, {"mode": "album"}, {"interval_minutes": 7},
                    {"quiet_start": 22}, {"quiet_start": 22, "quiet_end": 24}]:
            with self.assertRaises(ValueError, msg=bad):
                SlideshowSettings.from_dict(bad)

    def test_store_round_trip_and_bad_file(self):
        with tempfile.TemporaryDirectory() as d:
            store = SettingsStore(os.path.join(d, "s.json"))
            self.assertEqual("off", store.load().mode)
            s = SlideshowSettings(mode="album", album_id="a", interval_minutes=30, quiet_start=22, quiet_end=7)
            store.save(s)
            self.assertEqual(s, store.load())
            with open(store.path, "w") as f:
                f.write("{not json")
            self.assertEqual("off", store.load().mode)


class OnThisDayTest(unittest.TestCase):
    def test_previous_years(self):
        ranges = list(on_this_day_ranges(date(2025, 7, 14), years_back=2))
        self.assertEqual([("2024-07-14T00:00:00.000Z", "2024-07-15T00:00:00.000Z"),
                          ("2023-07-14T00:00:00.000Z", "2023-07-15T00:00:00.000Z")], ranges)

    def test_leap_day(self):
        ranges = list(on_this_day_ranges(date(2024, 2, 29), years_back=1))
        self.assertEqual("2023-02-28T00:00:00.000Z", ranges[0][0])


class PickerTest(unittest.TestCase):
    def test_avoids_repeats_until_exhausted(self):
        picker = Picker(FakeImmich(album={"a": ["1", "2", "3"]}))
        s = SlideshowSettings(mode="album", album_id="a")
        self.assertEqual({"1", "2", "3"}, {picker.next(s, date.today()) for _ in range(3)})
        self.assertIn(picker.next(s, date.today()), {"1", "2", "3"})  # then repeats are allowed

    def test_memories_fall_back_to_random_and_cache_per_day(self):
        immich = FakeImmich(library=["r"], by_day={"2024-07-14": ["m"]})
        picker = Picker(immich)
        s = SlideshowSettings(mode="memories")
        self.assertEqual("m", picker.next(s, date(2025, 7, 14)))
        calls = len(immich.calls)
        picker.next(s, date(2025, 7, 14))
        self.assertEqual(calls, len(immich.calls))  # cached for the day
        self.assertEqual("r", picker.next(s, date(2025, 7, 15)))  # nothing that day -> random


class SlideshowTest(unittest.TestCase):
    def make(self, settings, show_result=True):
        self.shown = []
        self.clock = Clock(datetime(2025, 7, 14, 12).timestamp())
        d = tempfile.mkdtemp()
        store = SettingsStore(os.path.join(d, "s.json"))
        store.save(settings)

        def show(asset_id):
            self.shown.append(asset_id)
            return show_result

        return Slideshow(store, Picker(FakeImmich(album={"a": ["1"]}, library=["2"])), show, clock=self.clock)

    def test_changes_every_interval(self):
        sl = self.make(SlideshowSettings(mode="random", interval_minutes=15))
        self.assertGreater(sl.tick(), 0)
        self.assertEqual([], self.shown)  # not due yet
        self.clock.advance(15 * 60)
        sl.tick()
        self.assertEqual(["2"], self.shown)
        self.assertAlmostEqual(self.clock() + 15 * 60, sl.next_change_at())

    def test_quiet_hours_postpone(self):
        sl = self.make(SlideshowSettings(mode="random", interval_minutes=15, quiet_start=12, quiet_end=14))
        self.clock.advance(15 * 60)
        sl.tick()
        self.assertEqual([], self.shown)
        self.assertEqual(datetime(2025, 7, 14, 14).timestamp(), sl.next_change_at())

    def test_switching_source_shows_immediately_and_manual_send_resets_timer(self):
        sl = self.make(SlideshowSettings(mode="off"))
        self.assertIsNone(sl.next_change_at())
        sl.update(SlideshowSettings(mode="album", album_id="a"))
        sl.tick()
        self.assertEqual(["1"], self.shown)
        self.clock.advance(50 * 60)
        sl.photo_changed()  # e.g. sent from the app
        self.clock.advance(15 * 60)
        sl.tick()
        self.assertEqual(["1"], self.shown)  # interval restarted

    def test_busy_frame_retries_soon(self):
        sl = self.make(SlideshowSettings(mode="random"), show_result=False)
        sl.skip()
        self.assertEqual(30, sl.tick())
