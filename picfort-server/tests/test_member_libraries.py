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
            DROP INDEX idx_album_suggestions_owner;
            ALTER TABLE album_suggestions DROP COLUMN owner_id;
            INSERT INTO photo_insights (asset_id, model, analyzed_at) VALUES ('a', 'm', '2026-11-28T00:00:00Z');
            PRAGMA user_version = 4;
        """)
        conn.close()
        store = PhotoAiStore(path)
        self.assertIsNone(store.migration_error)
        self.assertEqual({"": 1}, store.owned_counts())
        store.set_library_key(MOM, "Mom", "k")
        self.assertEqual(["Mom"], [k["user_name"] for k in store.library_keys()])


    def test_dashboard_lists_each_library_with_its_own_coverage(self):
        self.store.set_library_key(MOM, "Mom", "mom-key")
        self.store.save("d1", "m", {"category": "photo"}, owner_id=DAD)
        self.store.save("m1", "m", {"category": "photo"}, owner_id=MOM)
        self.store.save("x1", "m", {"category": "photo"}, owner_id="someone-else")  # a partner without a key
        self.store.save("old", "m", {"category": "photo"})  # analyzed before owners were recorded
        cfg = frame.Config(immich_url="http://immich", immich_api_key="k",
                           preview_path=os.path.join(self.tmp.name, "p.png"))
        controller = FrameController(cfg, min_interval=0, display=lambda _: None, prepare=lambda a: a)
        slideshow = Slideshow(SettingsStore(os.path.join(self.tmp.name, "s.json")), Picker(FakeImmich()),
                              controller.try_show)
        self.server.check = lambda: "ok"
        self.server.server_version = lambda: "v3"
        app = create_app(controller, slideshow, self.server, photo_ai=self.store, photo_ai_worker=self.worker,
                         data_dir=self.tmp.name)
        libs = app.test_client().get("/dashboard/data").get_json()["photo_ai"]["libraries"]
        self.assertEqual(["11111111-1111-1111-1111-111111111111", "Mom"], [lib["name"] for lib in libs])
        self.assertEqual((1, 1, 1), (libs[0]["analyzed"], libs[0]["partners"], libs[0]["unknown"]))
        self.assertEqual((1, 2, 0.5), (libs[1]["analyzed"], libs[1]["images"], libs[1]["coverage"]))
        self.assertNotIn("mom-key", str(libs))


    def test_owners_of_older_analyses_are_filled_from_immich(self):
        self.store.save("d1", "m", {"category": "photo"})
        self.store.save("m1", "m", {"category": "photo"})
        self.store.save("gone", "m", {"category": "photo"})
        owners = {"d1": DAD, "m1": MOM}

        def asset_owner(asset_id):
            if asset_id not in owners:
                raise http_error(404)
            return owners[asset_id]

        self.server.asset_owner = asset_owner
        self.assertEqual(3, self.worker.fill_unknown_owners())
        self.assertEqual({DAD: 1, MOM: 1, "-": 1}, self.store.owned_counts())  # "gone" is not asked again
        self.assertEqual(0, self.worker.fill_unknown_owners())

    def test_cleanup_status_dismiss_delete_are_isolated_per_owner(self):
        dad_asset = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"
        mom_asset = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"
        self.store.save(
            dad_asset, "m",
            {"category": "photo", "quality": 1, "cleanup_candidate": True, "cleanup_reason": "blurry"},
            ["ai/cleanup/low-quality"], owner_id=DAD,
        )
        self.store.save(
            mom_asset, "m",
            {"category": "screenshot", "quality": 3, "cleanup_candidate": True, "cleanup_reason": "screenshot"},
            ["ai/type/screenshot"], owner_id=MOM,
        )
        dad_client = self.client("dad-token")
        mom_client = self.client("mom-token")

        # Even though Mom shares with Dad (partner), cleanup & status are strictly own photos only.
        self.assertEqual([dad_asset], [i["asset_id"] for i in dad_client.get("/photo-insights/cleanup").get_json()["items"]])
        self.assertEqual([mom_asset], [i["asset_id"] for i in mom_client.get("/photo-insights/cleanup").get_json()["items"]])
        self.assertEqual(1, dad_client.get("/photo-insights/status").get_json()["cleanup_count"])
        self.assertEqual(1, mom_client.get("/photo-insights/status").get_json()["cleanup_count"])

        # Cross-owner dismiss and delete are rejected with 403.
        self.assertEqual(403, dad_client.post(f"/photo-insights/{mom_asset}/dismiss").status_code)
        self.assertEqual(403, mom_client.post(f"/photo-insights/{dad_asset}/dismiss").status_code)
        self.assertEqual(403, dad_client.delete(f"/photo-insights/{mom_asset}").status_code)
        self.assertEqual(403, mom_client.delete(f"/photo-insights/{dad_asset}").status_code)

        # Owners can dismiss and delete their own items.
        self.assertEqual(200, mom_client.post(f"/photo-insights/{mom_asset}/dismiss").status_code)
        self.assertEqual([], mom_client.get("/photo-insights/cleanup").get_json()["items"])
        self.assertEqual(204, dad_client.delete(f"/photo-insights/{dad_asset}").status_code)
        self.assertEqual([], dad_client.get("/photo-insights/cleanup").get_json()["items"])

    def test_album_suggestions_are_isolated_per_owner(self):
        dad_cluster = {
            "cluster_key": "2026-02-14..2026-02-16|zakopane",
            "fallback_title": "Zakopane",
            "subtitle": "12 photos",
            "asset_ids": ["d1", "d2"],
            "start_date": "2026-02-14",
            "end_date": "2026-02-16",
        }
        mom_cluster = {
            "cluster_key": "2026-03-01..2026-03-02|gdansk",
            "fallback_title": "Gdansk",
            "subtitle": "15 photos",
            "asset_ids": ["m1", "m2"],
            "start_date": "2026-03-01",
            "end_date": "2026-03-02",
        }
        self.store.save_album_suggestion(dad_cluster, "Dad trip", "d1", owner_id=DAD)
        self.store.save_album_suggestion(mom_cluster, "Mom trip", "m1", owner_id=MOM)

        dad_client = self.client("dad-token")
        mom_client = self.client("mom-token")
        self.assertEqual(["Dad trip"], [s["title"] for s in dad_client.get("/photo-insights/album-suggestions").get_json()["items"]])
        self.assertEqual(["Mom trip"], [s["title"] for s in mom_client.get("/photo-insights/album-suggestions").get_json()["items"]])

        # Cross-owner dismiss / created is rejected with 403.
        self.assertEqual(403, dad_client.post(f"/photo-insights/album-suggestions/{mom_cluster['cluster_key']}/dismiss").status_code)
        self.assertEqual(403, mom_client.post(f"/photo-insights/album-suggestions/{dad_cluster['cluster_key']}/created").status_code)

        # Own dismiss / created succeeds.
        self.assertEqual(200, dad_client.post(f"/photo-insights/album-suggestions/{dad_cluster['cluster_key']}/created").status_code)
        self.assertEqual(200, mom_client.post(f"/photo-insights/album-suggestions/{mom_cluster['cluster_key']}/dismiss").status_code)
        self.assertEqual([], dad_client.get("/photo-insights/album-suggestions").get_json()["items"])
        self.assertEqual([], mom_client.get("/photo-insights/album-suggestions").get_json()["items"])

    def test_frame_worthy_ids_and_picker_exclude_private_member_libraries(self):
        from datetime import date
        from slideshow import SlideshowSettings

        PRIVATE_MEMBER = "33333333-3333-3333-3333-333333333333"
        self.store.save("dad-best", "m", {"category": "photo", "quality": 5, "frame_worthy": True}, owner_id=DAD)
        self.store.save("mom-partner-best", "m", {"category": "photo", "quality": 5, "frame_worthy": True}, owner_id=MOM)
        self.store.save("private-best", "m", {"category": "photo", "quality": 5, "frame_worthy": True}, owner_id=PRIVATE_MEMBER)

        self.assertEqual({"dad-best", "mom-partner-best"}, set(self.store.frame_worthy_ids(owners={DAD, MOM})))

        immich = FakeImmich(library=["fallback"])
        immich.me = lambda: {"id": DAD, "name": "Dad"}
        immich.partners = lambda: {MOM}
        picker = Picker(immich, photo_ai=self.store)
        chosen = {picker.next(SlideshowSettings(mode="random", best_only=True), date(2026, 11, 28)) for _ in range(20)}
        self.assertEqual({"dad-best", "mom-partner-best"}, chosen)

    def test_remove_my_library_evicts_cached_clients(self):
        c = self.client("mom-token")
        self.assertEqual(200, c.put("/photo-insights/my-library", json={"api_key": "mom-key", "key_id": "k-1"}).status_code)
        self.worker.libraries()
        self.assertIn(MOM, self.worker._member_clients)
        self.assertEqual(200, c.delete("/photo-insights/my-library").status_code)
        self.assertNotIn(MOM, self.worker._member_clients)

    def test_server_user_retries_after_transient_error_and_caches_403(self):
        from photo_ai import SERVER_USER_RETRY_TTL
        from helpers import Clock

        clock = Clock()
        calls = []

        class FlakyImmich:
            def __init__(self, responses):
                self._responses = list(responses)

            def me(self):
                calls.append(1)
                r = self._responses.pop(0)
                if isinstance(r, Exception):
                    raise r
                return r

        # Transient ConnectionError -> returns None, throttled during TTL, then succeeds after TTL.
        flaky = FlakyImmich([requests.ConnectionError("timeout"), {"id": DAD, "name": "Dad"}])
        worker = PhotoAiWorker(PhotoAiConfig(), self.store, flaky, None, None, clock=clock)
        self.assertIsNone(worker.server_user())
        self.assertEqual(1, len(calls))
        # Within TTL: doesn't hammer Immich.
        self.assertIsNone(worker.server_user())
        self.assertEqual(1, len(calls))
        # After TTL: retries and resolves permanently.
        clock.advance(SERVER_USER_RETRY_TTL + 1)
        self.assertEqual({"id": DAD, "name": "Dad"}, worker.server_user())
        self.assertEqual(2, len(calls))
        self.assertEqual({"id": DAD, "name": "Dad"}, worker.server_user())
        self.assertEqual(2, len(calls))

        # 403 (missing user.read) -> cached permanently without retrying.
        calls.clear()
        forbidden = FlakyImmich([http_error(403), {"id": DAD, "name": "Dad"}])
        worker2 = PhotoAiWorker(PhotoAiConfig(), self.store, forbidden, None, None, clock=clock)
        self.assertIsNone(worker2.server_user())
        clock.advance(SERVER_USER_RETRY_TTL * 10)
        self.assertIsNone(worker2.server_user())
        self.assertEqual(1, len(calls))


if __name__ == "__main__":
    unittest.main()
