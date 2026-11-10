import unittest
from unittest import mock

import helpers  # noqa: F401  (puts the server on sys.path)
import requests

from auth import AuthUnavailable, User, immich_lookup


def response(status, body=None):
    r = mock.Mock(status_code=status, ok=200 <= status < 300)
    r.json.return_value = body or {}
    return r


class ImmichLookupTest(unittest.TestCase):
    def setUp(self):
        self.lookup = immich_lookup("https://immich.example/")

    @mock.patch("auth.requests.get")
    def test_valid_token(self, get):
        get.return_value = response(200, {"id": "u1", "name": "Mama", "email": "m@example"})
        self.assertEqual(User("u1", "Mama"), self.lookup("tok"))
        get.assert_called_once_with("https://immich.example/api/users/me",
                                    headers={"Authorization": "Bearer tok"}, timeout=10.0)

    @mock.patch("auth.requests.get")
    def test_name_falls_back_to_email(self, get):
        get.return_value = response(200, {"id": "u1", "name": "", "email": "m@example"})
        self.assertEqual("m@example", self.lookup("tok").name)

    @mock.patch("auth.requests.get")
    def test_rejected_token(self, get):
        for status in (401, 403):
            get.return_value = response(status)
            self.assertIsNone(self.lookup("tok"))

    @mock.patch("auth.requests.get")
    def test_immich_problems_are_not_rejections(self, get):
        get.return_value = response(500)
        self.assertRaises(AuthUnavailable, self.lookup, "tok")
        get.side_effect = requests.ConnectionError("down")
        self.assertRaises(AuthUnavailable, self.lookup, "tok")


if __name__ == "__main__":
    unittest.main()
