import os
import tempfile
import unittest

from helpers import FakeImmich

import frame
from app import FrameController, create_app
from auth import Authenticator, User
from events import EventStore
from family import FamilyStore
from shopping import ShoppingStore
from slideshow import Picker, SettingsStore, Slideshow
from wall import WallStore

MAMA = "11111111-1111-1111-1111-111111111111"
TATA = "22222222-2222-2222-2222-222222222222"
USERS = {"mama-token": User(MAMA, "Mama"), "tata-token": User(TATA, "Tata")}


class ApiContractTest(unittest.TestCase):
    """Protects the app/server contract from accidental route or payload drift."""

    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.wall = WallStore(os.path.join(self.dir, "wall.db"), os.path.join(self.dir, "wall_photos"))
        self.shopping = ShoppingStore(os.path.join(self.dir, "shopping.db"))
        self.family = FamilyStore(os.path.join(self.dir, "family.db"), os.path.join(self.dir, "profile_photos"))
        self.events = EventStore(os.path.join(self.dir, "events.db"))
        self.family.events = self.events
        self.events.family = self.family

        cfg = frame.Config(
            immich_url="http://immich",
            immich_api_key="k",
            preview_path=os.path.join(self.dir, "preview.png"),
        )
        controller = FrameController(cfg, min_interval=30, display=lambda _: None, prepare=lambda asset_id: asset_id)
        slideshow = Slideshow(
            SettingsStore(os.path.join(self.dir, "slideshow.json")),
            Picker(FakeImmich()),
            controller.try_show,
        )

        auth = Authenticator(lambda token: USERS.get(token))
        self.app = create_app(
            controller,
            slideshow,
            FakeImmich(),
            wall=self.wall,
            auth=auth,
            shopping=self.shopping,
            family=self.family,
            events=self.events,
        )
        self.client = self.app.test_client()
        self.client.environ_base["HTTP_AUTHORIZATION"] = "Bearer mama-token"

    def test_route_methods_contract(self):
        expected = {
            "/albums": ["GET"],
            "/current": ["GET"],
            "/events": ["GET", "POST"],
            "/events/<int:event_id>": ["DELETE", "GET", "PATCH"],
            "/frame": ["GET"],
            "/frame/photo": ["GET"],
            "/frame/state": ["GET"],
            "/family": ["GET"],
            "/family/members": ["POST"],
            "/family/people": ["GET"],
            "/family/photo/<filename>": ["GET"],
            "/family/<user_id>": ["DELETE", "PATCH"],
            "/family/<user_id>/person": ["DELETE", "PUT"],
            "/family/<user_id>/photo": ["DELETE", "PUT"],
            "/lists": ["GET", "POST"],
            "/lists/<int:list_id>": ["DELETE", "GET", "PATCH"],
            "/lists/<int:list_id>/delete-checked": ["POST"],
            "/lists/<int:list_id>/items": ["POST"],
            "/lists/<int:list_id>/items/<int:item_id>": ["DELETE", "PATCH"],
            "/lists/<int:list_id>/uncheck-all": ["POST"],
            "/next": ["POST"],
            "/run": ["GET"],
            "/run-local": ["POST"],
            "/slideshow": ["GET", "PUT"],
            "/status": ["GET"],
            "/wall": ["GET", "POST"],
            "/wall/<int:post_id>": ["DELETE"],
            "/wall/<int:post_id>/pin": ["PUT"],
            "/wall/photo/<filename>": ["GET"],
        }

        actual = {}
        for rule in self.app.url_map.iter_rules():
            if rule.rule.startswith("/static/"):
                continue
            methods = {m for m in rule.methods if m not in {"HEAD", "OPTIONS"}}
            actual.setdefault(rule.rule, set()).update(methods)
        normalized = {path: sorted(methods) for path, methods in actual.items()}

        self.assertEqual(expected, normalized)

    def test_status_and_slideshow_response_keys(self):
        status = self.client.get("/status")
        self.assertEqual(200, status.status_code)
        body = status.get_json()
        self.assertEqual(
            {"status", "busy", "currentAsset", "lastError", "width", "height", "hardware", "slideshow"},
            set(body.keys()),
        )
        self.assertEqual(
            {
                "mode",
                "album_id",
                "interval_minutes",
                "quiet_start",
                "quiet_end",
                "next_change_at",
                "last_error",
                "modes",
                "intervals",
            },
            set(body["slideshow"].keys()),
        )

        slideshow = self.client.get("/slideshow")
        self.assertEqual(200, slideshow.status_code)
        self.assertEqual(set(body["slideshow"].keys()), set(slideshow.get_json().keys()))

    def test_family_events_and_lists_minimal_shapes(self):
        self.assertEqual({"profiles": []}, self.client.get("/family").get_json())
        self.assertEqual([], self.client.get("/events?after=2026-11-11").get_json())
        self.assertEqual([], self.client.get("/lists").get_json())

        default_auth = self.client.environ_base.pop("HTTP_AUTHORIZATION", None)
        try:
            no_token = self.client.get("/family")
            self.assertEqual(401, no_token.status_code)
        finally:
            if default_auth is not None:
                self.client.environ_base["HTTP_AUTHORIZATION"] = default_auth


if __name__ == "__main__":
    unittest.main()
