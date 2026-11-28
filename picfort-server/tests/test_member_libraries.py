"""T-104: every family member can add their own Immich API key, so their library is analyzed and
tagged in their own Immich."""

import os
import tempfile
import unittest

import requests
from helpers import FakeImmich

import frame
from app import FrameController, create_app
from auth import Authenticator, User
from photo_ai import PhotoAiConfig, PhotoAiStore, PhotoAiWorker
from slideshow import Picker, SettingsStore, Slideshow

DAD = "11111111-1111-1111-1111-111111111111"
MOM = "22222222-2222-2222-2222-222222222222"
USERS = {"dad-token": User(DAD, "Dad"), "mom-token": User(MOM, "Mom")}


def http_error(status):
    resp = requests.Response()
    resp.status_code = status
    return requests.HTTPError(response=resp)


class FakeLibrary:
    """An Immich client for one key: sees its owner's photos (and partners' when given)."""

    def __init__(self, api_key, owner, photos, user_read=True):
        self.api_key = api_key
        self.owner = owner
        self.photos = photos  # list of (asset_id, owner_id)
        self.user_read = user_read
        self.tagged = []
        self.previews = []

    def me(self):
        if not self.user_read:
            raise http_error(403)
        return {"id": self.owner, "name": self.owner}

    def recent_images(self, page=1, size=100):
        if page != 1:
            return [], False
        return [{"id": aid, "ownerId": owner} for aid, owner in self.photos], False

    def preview_bytes(self, asset_id):
        self.previews.append(asset_id)
        return asset_id.encode()

    def upsert_tags(self, names):
        return {n: f"{self.owner}:{n}" for n in names}

    def tag_assets(self, tag_ids, asset_ids):
        # Immich lets only the owner tag an asset (AssetUpdate access).
        if any(owner != self.owner for aid, owner in self.photos if aid in asset_ids):
            raise http_error(400)
        self.tagged.append((list(tag_ids), list(asset_ids)))

    def untag_asset(self, tag_id, asset_id):
        pass

    def asset_statistics(self):
        return {"images": len([p for p in self.photos if p[1] == self.owner])}


class BestAnalyzer:
    """Every photo is a sharp nature shot: it gets ai/type/nature (so tagging is visible)."""

    def analyze(self, image_bytes):
        return {"category": "photo", "quality": 4, "is_nature": True}


class MemberLibrariesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = PhotoAiStore(os.path.join(self.tmp.name, "photo_ai.db"))
        # Dad's key is the server's; Mom shares her library with Dad (partner, in his timeline).
        self.server = FakeLibrary("server-key", DAD, [("d1", DAD), ("m1", MOM)])
        self.mom = FakeLibrary("mom-key", MOM, [("m1", MOM), ("m2", MOM)])
        self.keys = {"mom-key": self.mom, "dad-other-key": FakeLibrary("dad-other-key", DAD, [])}
        from photo_ai import ImmichTagger
        self.worker = PhotoAiWorker(
            PhotoAiConfig(ollama_url="http://x", ollama_model="m", batch_size=10),
            self.store, self.server, BestAnalyzer(), ImmichTagger(self.server),
            client_factory=lambda key: self.keys[key] if key in self.keys else FakeLibrary(key, "", [], False),
        )

    def tearDown(self):
        self.tmp.cleanup()

    def client(self, token="mom-token"):
        cfg = frame.Config(immich_url="http://immich", immich_api_key="k",
                           preview_path=os.path.join(self.tmp.name, "p.png"))
        controller = FrameController(cfg, min_interval=0, display=lambda _: None, prepare=lambda a: a)
        slideshow = Slideshow(SettingsStore(os.path.join(self.tmp.name, "s.json")), Picker(FakeImmich()),
                              controller.try_show)
        auth = Authenticator(lambda t: USERS.get(t))
        partners = {"Bearer dad-token": {MOM}, "Bearer mom-token": set()}
        c = create_app(controller, slideshow, FakeImmich(), auth=auth, photo_ai=self.store,
                       photo_ai_worker=self.worker, partner_lookup=lambda h: partners.get(h, set())).test_client()
        c.environ_base["HTTP_AUTHORIZATION"] = f"Bearer {token}"
        return c

    def test_member_adds_and_removes_their_key(self):
        c = self.client()
        self.assertEqual({"enabled": False, "via_server": False}, {k: c.get("/photo-insights/my-library").get_json()[k]
                                                                    for k in ("enabled", "via_server")})
        ok = c.put("/photo-insights/my-library", json={"api_key": "mom-key", "key_id": "k-1"})
        self.assertEqual(200, ok.status_code)
        body = ok.get_json()
        self.assertTrue(body["enabled"])
        self.assertEqual(2, body["images"])
        self.assertNotIn("mom-key", ok.get_data(as_text=True))  # write-only
        self.assertEqual({"key_id": "k-1"}, c.delete("/photo-insights/my-library").get_json())
        self.assertEqual(404, c.delete("/photo-insights/my-library").status_code)

    def test_key_must_be_the_callers(self):
        c = self.client()
        self.assertEqual(403, c.put("/photo-insights/my-library", json={"api_key": "dad-other-key"}).status_code)
        self.assertEqual(400, c.put("/photo-insights/my-library", json={"api_key": "bogus"}).status_code)
        self.assertEqual(400, c.put("/photo-insights/my-library", json={}).status_code)
        self.assertEqual(401, self.client(token="nobody").get("/photo-insights/my-library").status_code)

    def test_server_owner_is_covered_without_a_key(self):
        body = self.client(token="dad-token").get("/photo-insights/my-library").get_json()
        self.assertTrue(body["via_server"])
        self.assertFalse(body["enabled"])

    def test_libraries_take_turns_and_each_owner_tags_their_own_photos(self):
        self.store.set_library_key(MOM, "Mom", "mom-key")
        # Turn 1: the server library (Dad's key) analyzes d1 and Mom's shared m1; it may tag only d1.
        self.assertEqual(2, self.worker.run_batch())
        self.assertEqual([["d1"]], [assets for _, assets in self.server.tagged])
        # Turn 2: Mom's library: tags her m1 (backlog, no new analysis) and analyzes m2 itself.
        self.assertEqual(1, self.worker.run_batch())
        self.assertEqual(["m2"], self.mom.previews)
        self.assertEqual({"m1", "m2"}, {a for _, assets in self.mom.tagged for a in assets})
        self.assertEqual({DAD: 1, MOM: 2}, self.store.owned_counts())

    def test_labels_only_for_own_and_partners_photos(self):
        self.store.save("d1", "m", {"category": "photo"}, owner_id=DAD)
        self.store.save("m1", "m", {"category": "screenshot", "is_sensitive": True}, owner_id=MOM)
        self.store.save("old", "m", {"category": "photo"})  # analyzed before owners were known
        mom = {row[0] for row in self.client("mom-token").get("/photo-insights/labels").get_json()["items"]}
        dad = {row[0] for row in self.client("dad-token").get("/photo-insights/labels").get_json()["items"]}
        self.assertEqual({"m1", "old"}, mom)  # Dad doesn't share with Mom
        self.assertEqual({"d1", "m1", "old"}, dad)  # Mom shares with Dad

    def test_migrates_v4_and_keeps_rows(self):
        import sqlite3
        path = os.path.join(self.tmp.name, "v4.db")
        PhotoAiStore(path)  # current schema
        conn = sqlite3.connect(path)
        conn.executescript("""
            DROP TABLE library_keys; DROP INDEX idx_photo_insights_owner;
            ALTER TABLE photo_insights DROP COLUMN owner_id;
            INSERT INTO photo_insights (asset_id, model, analyzed_at) VALUES ('a', 'm', '2026-11-28T00:00:00Z');
            PRAGMA user_version = 4;
        """)
        conn.close()
        store = PhotoAiStore(path)
        self.assertIsNone(store.migration_error)
        self.assertEqual({"": 1}, store.owned_counts())
        store.set_library_key(MOM, "Mom", "k")
        self.assertEqual(["Mom"], [k["user_name"] for k in store.library_keys()])


if __name__ == "__main__":
    unittest.main()
