import os
import sqlite3
import tempfile
import unittest
from contextlib import closing

from helpers import FakeImmich
from test_wall import MAMA, TATA, Clock, FakeImmichAuth

import frame
from app import AUTH_PREFIXES, FrameController, create_app
from auth import Authenticator
from shopping import MAX_ITEMS, ShoppingStore
from slideshow import Picker, SettingsStore, Slideshow
from wall import WallStore


class ShoppingApiTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.db = os.path.join(self.dir, "shopping.db")
        self.wall_db = os.path.join(self.dir, "wall.db")
        self.store = ShoppingStore(self.db, clock=Clock())
        self.wall = WallStore(self.wall_db, os.path.join(self.dir, "wall_photos"), clock=Clock())
        cfg = frame.Config(immich_url="http://immich", immich_api_key="k",
                           preview_path=os.path.join(self.dir, "p.png"))
        controller = FrameController(cfg, min_interval=30, display=lambda img: None, prepare=lambda a: a)
        slideshow = Slideshow(SettingsStore(os.path.join(self.dir, "s.json")), Picker(FakeImmich()),
                              controller.try_show)
        self.client = create_app(controller, slideshow, FakeImmich(), self.wall,
                                 Authenticator(FakeImmichAuth()), shopping=self.store).test_client()
        self.client.environ_base["HTTP_AUTHORIZATION"] = "Bearer mama-token"

    def as_tata(self):
        return {"Authorization": "Bearer tata-token"}

    def new_list(self, title="Biedronka", items=("milk", "bread", "eggs")):
        r = self.client.post("/lists", json={"title": title, "items": list(items)})
        self.assertEqual(201, r.status_code, r.get_json())
        return r.get_json()

    def texts(self, lst):
        return [i["text"] for i in lst["items"]]

    # Lists

    def test_create_and_read(self):
        lst = self.new_list()
        self.assertEqual("Biedronka", lst["title"])
        self.assertEqual(MAMA, lst["created_by"])
        self.assertEqual(["milk", "bread", "eggs"], self.texts(lst))
        self.assertEqual((3, 0), (lst["item_count"], lst["checked_count"]))
        self.assertFalse(lst["archived"])
        self.assertEqual(lst, self.client.get(f"/lists/{lst['id']}").get_json())
        self.assertEqual([lst["id"]], [x["id"] for x in self.client.get("/lists").get_json()])

    def test_newest_list_first_and_archive(self):
        a = self.new_list("A")
        b = self.new_list("B")
        self.assertEqual([b["id"], a["id"]], [x["id"] for x in self.client.get("/lists").get_json()])
        r = self.client.patch(f"/lists/{a['id']}", json={"archived": True}, headers=self.as_tata())
        self.assertTrue(r.get_json()["archived"])
        self.assertEqual([b["id"]], [x["id"] for x in self.client.get("/lists").get_json()])
        self.assertEqual([a["id"]], [x["id"] for x in self.client.get("/lists?archived=1").get_json()])

    def test_rename_by_anyone(self):
        lst = self.new_list()
        r = self.client.patch(f"/lists/{lst['id']}", json={"title": "  Lidl  "}, headers=self.as_tata())
        self.assertEqual("Lidl", r.get_json()["title"])

    def test_delete_only_by_creator(self):
        lst = self.new_list()
        self.assertEqual(403, self.client.delete(f"/lists/{lst['id']}", headers=self.as_tata()).status_code)
        self.assertEqual(204, self.client.delete(f"/lists/{lst['id']}").status_code)
        self.assertEqual(404, self.client.get(f"/lists/{lst['id']}").status_code)

    def test_delete_cascades_items_and_keeps_receipts(self):
        lst = self.new_list()
        with closing(self.store.connect()) as conn, conn:
            conn.execute("INSERT INTO receipts (list_id, date, payer_id, status, created_by, created, updated) "
                         "VALUES (?, '2026-11-10', ?, 'manual', ?, 'x', 'x')", (lst["id"], MAMA, MAMA))
        self.client.delete(f"/lists/{lst['id']}")
        with closing(self.store.connect()) as conn:
            self.assertEqual(0, conn.execute("SELECT COUNT(*) FROM items").fetchone()[0])
            self.assertEqual([(None,)], [tuple(r) for r in conn.execute("SELECT list_id FROM receipts")])

    def test_list_validation(self):
        for body in ({}, {"title": ""}, {"title": "   "}, {"title": "x" * 101}, {"title": 5},
                     {"title": "ok", "items": "milk"}, {"title": "ok", "items": [""]},
                     {"title": "ok", "items": ["x"] * (MAX_ITEMS + 1)}):
            self.assertEqual(400, self.client.post("/lists", json=body).status_code, body)
        self.assertEqual(400, self.client.post("/lists", data="nope", content_type="application/json").status_code)
        self.assertEqual(400, self.client.post("/lists", json=["a"]).status_code)
        lst = self.new_list()
        self.assertEqual(400, self.client.patch(f"/lists/{lst['id']}", json={"archived": "yes"}).status_code)
        self.assertEqual(404, self.client.patch("/lists/999", json={"title": "x"}).status_code)
        # Unknown fields are ignored.
        self.assertEqual(200, self.client.patch(f"/lists/{lst['id']}", json={"color": "red"}).status_code)

    # Items

    def test_add_item_goes_last_or_at_position(self):
        lst = self.new_list()
        r = self.client.post(f"/lists/{lst['id']}/items", json={"text": " butter "}, headers=self.as_tata())
        self.assertEqual(201, r.status_code)
        self.assertEqual(("butter", 4.0, TATA), (r.get_json()["text"], r.get_json()["position"], r.get_json()["created_by"]))
        self.client.post(f"/lists/{lst['id']}/items", json={"text": "cheese", "position": 1.5})
        self.assertEqual(["milk", "cheese", "bread", "eggs", "butter"],
                         self.texts(self.client.get(f"/lists/{lst['id']}").get_json()))

    def test_check_order_and_fields(self):
        lst = self.new_list()
        milk, bread, eggs = (i["id"] for i in lst["items"])
        r = self.client.patch(f"/lists/{lst['id']}/items/{milk}", json={"checked": True}, headers=self.as_tata())
        self.assertEqual((True, TATA), (r.get_json()["checked"], r.get_json()["checked_by"]))
        self.assertIsNotNone(r.get_json()["checked_at"])
        self.client.patch(f"/lists/{lst['id']}/items/{eggs}", json={"checked": True})
        got = self.client.get(f"/lists/{lst['id']}").get_json()
        # Unchecked first by position, then checked with the latest check first.
        self.assertEqual(["bread", "eggs", "milk"], self.texts(got))
        self.assertEqual(2, got["checked_count"])
        r = self.client.patch(f"/lists/{lst['id']}/items/{milk}", json={"checked": False})
        self.assertEqual((False, None, None), (r.get_json()["checked"], r.get_json()["checked_by"], r.get_json()["checked_at"]))
        self.assertEqual(["milk", "bread", "eggs"], self.texts(self.client.get(f"/lists/{lst['id']}").get_json()))

    def test_edit_move_and_delete_item(self):
        lst = self.new_list()
        milk, bread, _ = (i["id"] for i in lst["items"])
        self.client.patch(f"/lists/{lst['id']}/items/{milk}", json={"text": "milk 2x", "position": 2.5})
        self.assertEqual(["bread", "milk 2x", "eggs"], self.texts(self.client.get(f"/lists/{lst['id']}").get_json()))
        self.assertEqual(204, self.client.delete(f"/lists/{lst['id']}/items/{bread}", headers=self.as_tata()).status_code)
        self.assertEqual(["milk 2x", "eggs"], self.texts(self.client.get(f"/lists/{lst['id']}").get_json()))

    def test_item_validation_and_wrong_list(self):
        lst = self.new_list()
        other = self.new_list("Other", ["x"])
        item = lst["items"][0]["id"]
        url = f"/lists/{lst['id']}/items"
        for body in ({}, {"text": ""}, {"text": "x" * 201}, {"text": "ok", "position": "first"},
                     {"text": "ok", "position": True}):
            self.assertEqual(400, self.client.post(url, json=body).status_code, body)
        self.assertEqual(400, self.client.patch(f"{url}/{item}", json={"checked": 1}).status_code)
        self.assertEqual(404, self.client.patch(f"/lists/{other['id']}/items/{item}", json={"text": "x"}).status_code)
        self.assertEqual(404, self.client.delete(f"/lists/{other['id']}/items/{item}").status_code)
        self.assertEqual(404, self.client.post("/lists/999/items", json={"text": "x"}).status_code)

    def test_item_limit(self):
        lst = self.new_list(items=["x"] * MAX_ITEMS)
        self.assertEqual(400, self.client.post(f"/lists/{lst['id']}/items", json={"text": "one more"}).status_code)

    def test_uncheck_all_and_delete_checked(self):
        lst = self.new_list()
        for item in lst["items"][:2]:
            self.client.patch(f"/lists/{lst['id']}/items/{item['id']}", json={"checked": True})
        r = self.client.post(f"/lists/{lst['id']}/uncheck-all")
        self.assertEqual(0, r.get_json()["checked_count"])
        self.assertEqual(["milk", "bread", "eggs"], self.texts(r.get_json()))
        self.client.patch(f"/lists/{lst['id']}/items/{lst['items'][1]['id']}", json={"checked": True})
        r = self.client.post(f"/lists/{lst['id']}/delete-checked")
        self.assertEqual(["milk", "eggs"], self.texts(r.get_json()))

    # Revision and ETag

    def test_etag_and_revision(self):
        lst = self.new_list()
        url = f"/lists/{lst['id']}"
        first = self.client.get(url)
        etag = first.headers["ETag"]
        self.assertEqual(304, self.client.get(url, headers={"If-None-Match": etag}).status_code)
        # A change in the same second still changes the ETag: it comes from the revision.
        self.store.clock = lambda: "2026-11-10T12:00:00Z"
        self.client.post(f"{url}/items", json={"text": "butter"})
        again = self.client.get(url, headers={"If-None-Match": etag})
        self.assertEqual(200, again.status_code)
        self.assertEqual(lst["revision"] + 1, again.get_json()["revision"])
        self.assertNotEqual(etag, again.headers["ETag"])
        revision = again.get_json()["revision"]
        for change in (lambda: self.client.patch(url, json={"title": "x"}),
                       lambda: self.client.post(f"{url}/uncheck-all"),
                       lambda: self.client.patch(f"{url}/items/{lst['items'][0]['id']}", json={"checked": True})):
            change()
            revision += 1
            self.assertEqual(revision, self.client.get(url).get_json()["revision"])

    # Storage

    def test_survives_restart(self):
        lst = self.new_list()
        self.assertEqual(lst, ShoppingStore(self.db).get(lst["id"]))

    def test_schema_change_keeps_a_backup_and_leaves_the_wall(self):
        self.new_list()
        self.client.post("/wall", data={"text": "hi"}, content_type="multipart/form-data")
        with closing(sqlite3.connect(self.db)) as conn:
            conn.execute("PRAGMA user_version = 99")
        store = ShoppingStore(self.db)
        self.assertEqual([], store.lists())
        self.assertTrue(os.path.exists(self.db + ".v99.bak"))
        self.assertEqual(1, len(WallStore(self.wall_db, os.path.join(self.dir, "wall_photos")).list()))

    # Auth

    def test_family_routes_need_a_token_frame_routes_dont(self):
        for prefix in AUTH_PREFIXES:
            for path in (prefix, prefix + "/1"):
                r = self.client.get(path, headers={"Authorization": ""})
                self.assertEqual(401, r.status_code, path)
        self.assertEqual(401, self.client.get("/lists", headers={"Authorization": "Bearer nobody"}).status_code)
        self.assertEqual(200, self.client.get("/status", headers={"Authorization": ""}).status_code)
        self.assertEqual(200, self.client.get("/slideshow", headers={"Authorization": ""}).status_code)
        # A path that only starts like a prefix is not a family route.
        self.assertEqual(404, self.client.get("/listsx", headers={"Authorization": ""}).status_code)


if __name__ == "__main__":
    unittest.main()
