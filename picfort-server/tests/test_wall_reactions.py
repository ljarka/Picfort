"""Wall reactions and the first-reaction feed for notifications (T-084)."""

import os
import shutil
import sqlite3
import tempfile
import unittest
from contextlib import closing

import test_wall
from test_wall import MAMA, TATA, Clock
from wall import REACTIONS, WallStore

HEART, THUMB = REACTIONS[0], REACTIONS[1]


class WallReactionsApiTest(unittest.TestCase):
    # The wall API test's fixtures, without running its tests again.
    setUp = test_wall.WallApiTest.setUp
    post = test_wall.WallApiTest.post

    def react(self, post_id, emoji, token="tata-token"):
        return self.client.put(f"/wall/{post_id}/reactions", json={"emoji": emoji},
                               headers={"Authorization": f"Bearer {token}"})

    def unreact(self, post_id, emoji, token="tata-token"):
        return self.client.delete(f"/wall/{post_id}/reactions", query_string={"emoji": emoji},
                                  headers={"Authorization": f"Bearer {token}"})

    def first(self, since, token="mama-token"):
        return self.client.get("/wall/first-reactions", query_string={"since": since},
                               headers={"Authorization": f"Bearer {token}"})

    def test_reactions_are_grouped_per_emoji_in_fixed_order(self):
        post_id = self.post(text="Obiad gotowy").get_json()["id"]
        self.react(post_id, THUMB)
        self.react(post_id, HEART)
        r = self.react(post_id, HEART, token="mama-token")
        self.assertEqual(200, r.status_code, r.get_json())
        self.assertEqual(
            [
                {"emoji": HEART, "count": 2, "user_ids": [TATA, MAMA], "names": ["Tata", "Mama"]},
                {"emoji": THUMB, "count": 1, "user_ids": [TATA], "names": ["Tata"]},
            ],
            r.get_json()["reactions"],
        )
        self.assertEqual(r.get_json()["reactions"], self.client.get("/wall").get_json()[0]["reactions"])

    def test_reacting_twice_is_idempotent_and_removal_only_removes_mine(self):
        post_id = self.post(text="x").get_json()["id"]
        self.react(post_id, HEART)
        self.react(post_id, HEART)
        self.react(post_id, HEART, token="mama-token")
        r = self.unreact(post_id, HEART)
        self.assertEqual([{"emoji": HEART, "count": 1, "user_ids": [MAMA], "names": ["Mama"]}],
                         r.get_json()["reactions"])

    def test_unknown_emoji_and_missing_post(self):
        post_id = self.post(text="x").get_json()["id"]
        self.assertEqual(400, self.react(post_id, "💩").status_code)
        self.assertEqual(400, self.react(post_id, None).status_code)
        self.assertEqual(404, self.react(999, HEART).status_code)
        self.assertEqual(404, self.unreact(999, HEART).status_code)

    def test_first_reaction_by_someone_else_is_reported_once(self):
        post_id = self.post(text="Pierwsze pomidory").get_json()["id"]  # 12:01
        self.react(post_id, THUMB, token="mama-token")  # own reaction: not a "first reaction"
        self.assertEqual([], self.first("2026-11-10T00:00:00Z").get_json()["items"])
        self.react(post_id, HEART)  # Tata, 12:03
        self.react(post_id, THUMB)  # later reactions don't change it
        items = self.first("2026-11-10T00:00:00Z").get_json()["items"]
        self.assertEqual(
            [{"post_id": post_id, "text": "Pierwsze pomidory", "has_photo": False,
              "at": "2026-11-10T12:03:00Z", "by": "Tata", "emoji": HEART}],
            items,
        )
        self.assertEqual([], self.first("2026-11-10T12:03:00Z").get_json()["items"])
        # Removing and re-adding never reports it again.
        self.unreact(post_id, HEART)
        self.unreact(post_id, THUMB)
        self.react(post_id, HEART)
        self.assertEqual("2026-11-10T12:03:00Z", self.first("2026-11-10T00:00:00Z").get_json()["items"][0]["at"])

    def test_first_reactions_are_only_for_my_posts_and_need_since(self):
        post_id = self.post(text="Tata's", token="tata-token").get_json()["id"]
        self.react(post_id, HEART, token="mama-token")
        self.assertEqual([], self.first("2026-11-10T00:00:00Z").get_json()["items"])
        self.assertEqual(1, len(self.first("2026-11-10T00:00:00Z", token="tata-token").get_json()["items"]))
        self.assertEqual(400, self.client.get("/wall/first-reactions").status_code)

    def test_deleting_a_post_deletes_its_reactions(self):
        post_id = self.post(text="x").get_json()["id"]
        self.react(post_id, HEART)
        self.assertEqual(204, self.client.delete(f"/wall/{post_id}").status_code)
        with closing(sqlite3.connect(self.store.db_path)) as conn:
            self.assertEqual(0, conn.execute("SELECT COUNT(*) FROM reactions").fetchone()[0])


class WallMigrationTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.dir, ignore_errors=True)

    def test_v1_posts_survive_and_can_get_reactions(self):
        db = os.path.join(self.dir, "wall.db")
        with closing(sqlite3.connect(db)) as conn:
            conn.executescript(f"""
                CREATE TABLE posts (
                  id INTEGER PRIMARY KEY AUTOINCREMENT, author_id TEXT NOT NULL, author TEXT NOT NULL,
                  text TEXT, photo TEXT, photo_width INTEGER, photo_height INTEGER,
                  pinned INTEGER NOT NULL DEFAULT 0, created TEXT NOT NULL
                );
                INSERT INTO posts (author_id, author, text, created) VALUES ('{MAMA}', 'Mama', 'old', '2026-01-01T00:00:00Z');
                PRAGMA user_version = 1;
            """)
        store = WallStore(db, os.path.join(self.dir, "photos"), clock=Clock())
        self.assertIsNone(store.migration_error)
        self.assertTrue(os.path.exists(db + ".v1.bak"))
        [post] = store.list()
        self.assertEqual(("old", []), (post["text"], post["reactions"]))
        reacted = store.react(post["id"], TATA, "Tata", HEART)
        self.assertEqual(1, reacted["reactions"][0]["count"])
        self.assertEqual(1, len(store.first_reactions(MAMA, "2026-01-01T00:00:00Z")))


if __name__ == "__main__":
    unittest.main()
