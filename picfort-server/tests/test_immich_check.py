"""T-086: the server says whether it reaches Immich with its key (/status "immich", start log)."""
import os
import tempfile
import unittest
from unittest import mock

import requests

from helpers import Clock, FakeImmich  # noqa: F401  (sets sys.path)
from app import FrameController, create_app
from frame import Config
from immich import (IMMICH_CHECK_LOG, IMMICH_KEY_REJECTED, IMMICH_NOT_IMMICH, IMMICH_OK, IMMICH_UNREACHABLE,
                    ImmichClient)
from slideshow import Picker, SettingsStore, Slideshow


def response(status, text=""):
    r = mock.Mock()
    r.status_code, r.text = status, text
    return r


class ImmichClientCheckTest(unittest.TestCase):
    def check(self, *answers):
        with mock.patch("immich.requests.get", side_effect=list(answers)) as get:
            result = ImmichClient("http://photos.example.com", "key").check()
        return result, get

    def test_ok(self):
        result, get = self.check(response(200, '{"res":"pong"}'), response(200, "[]"))
        self.assertEqual(IMMICH_OK, result)
        self.assertEqual("key", get.call_args_list[1].kwargs["headers"]["x-api-key"])

    def test_nothing_answers(self):
        self.assertEqual(IMMICH_UNREACHABLE, self.check(requests.ConnectionError())[0])
        self.assertEqual(IMMICH_UNREACHABLE, self.check(requests.Timeout())[0])

    def test_something_else_answers(self):
        self.assertEqual(IMMICH_NOT_IMMICH, self.check(response(404, "Not Found"))[0])
        self.assertEqual(IMMICH_NOT_IMMICH, self.check(response(200, "<html>router</html>"))[0])

    def test_key_rejected_or_without_album_read(self):
        self.assertEqual(IMMICH_KEY_REJECTED, self.check(response(200, "pong"), response(401))[0])
        self.assertEqual(IMMICH_KEY_REJECTED, self.check(response(200, "pong"), response(403))[0])

    def test_every_result_has_a_log_line_with_the_fix(self):
        for state in (IMMICH_OK, IMMICH_UNREACHABLE, IMMICH_NOT_IMMICH, IMMICH_KEY_REJECTED):
            self.assertIn("%s", IMMICH_CHECK_LOG[state])
        self.assertIn("INSTALL.md", IMMICH_CHECK_LOG[IMMICH_UNREACHABLE])
        self.assertIn("INSTALL.md", IMMICH_CHECK_LOG[IMMICH_KEY_REJECTED])


class StatusImmichTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        config = Config(immich_url="http://photos.example.com", immich_api_key="k")
        controller = FrameController(config, min_interval=30, display=lambda _: None, prepare=lambda a: a)
        self.immich = FakeImmich()
        slideshow = Slideshow(SettingsStore(os.path.join(self.tmp.name, "s.json")), Picker(self.immich),
                              controller.try_show)
        self.clock = Clock()
        self.client = create_app(controller, slideshow, self.immich, clock=self.clock).test_client()

    def checks(self):
        return sum(1 for c in self.immich.calls if c[0] == "check")

    def test_reported_and_cached_while_fine(self):
        self.assertEqual("ok", self.client.get("/status").get_json()["immich"])
        self.client.get("/status")
        self.clock.advance(30)
        self.client.get("/status")
        self.assertEqual(1, self.checks())
        self.clock.advance(31)
        self.client.get("/status")
        self.assertEqual(2, self.checks())

    def test_a_problem_is_checked_again_soon(self):
        self.immich.health = IMMICH_KEY_REJECTED
        self.assertEqual("key_rejected", self.client.get("/status").get_json()["immich"])
        self.immich.health = IMMICH_OK
        self.clock.advance(5)
        self.assertEqual("ok", self.client.get("/status").get_json()["immich"])


if __name__ == "__main__":
    unittest.main()
