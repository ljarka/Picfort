import io
import os
import tempfile
import unittest

from PIL import Image

from helpers import FakeImmich
from test_wall import MAMA, TATA, Clock, FakeImmichAuth

import frame
from app import FrameController, create_app
from auth import Authenticator
from photos import MAX_PHOTO_BYTES
from shopping import ShoppingStore
from slideshow import Picker, SettingsStore, Slideshow
from wall import WallStore


def make_jpeg(color=(255, 0, 0), size=(20, 20)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", size, color).save(buf, format="JPEG")
    return buf.getvalue()


def make_png(color=(0, 255, 0), size=(20, 20)) -> bytes:
    buf = io.BytesIO()
    Image.new("RGB", size, color).save(buf, format="PNG")
    return buf.getvalue()


class ReceiptsApiTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()
        self.db = os.path.join(self.dir, "shopping.db")
        self.receipts_dir = os.path.join(self.dir, "receipts")
        self.wall_db = os.path.join(self.dir, "wall.db")
        self.store = ShoppingStore(
            self.db,
            clock=Clock(),
            receipts_dir=self.receipts_dir,
            currency="PLN",
        )
        self.wall = WallStore(self.wall_db, os.path.join(self.dir, "wall_photos"), clock=Clock())
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
        self.client = create_app(
            controller,
            slideshow,
            FakeImmich(),
            self.wall,
            Authenticator(FakeImmichAuth()),
            shopping=self.store,
        ).test_client()
        self.client.environ_base["HTTP_AUTHORIZATION"] = "Bearer mama-token"

    def as_tata(self):
        return {"Authorization": "Bearer tata-token"}

    def new_list(self, title="Biedronka"):
        r = self.client.post("/lists", json={"title": title, "items": []})
        self.assertEqual(201, r.status_code, r.get_json())
        return r.get_json()

    def upload_receipt(self, date="2026-11-10", list_id=None, payer_id=None,
                       photo=None, filename="receipt.jpg", headers=None):
        data = {"date": date}
        if list_id is not None:
            data["list_id"] = str(list_id)
        if payer_id is not None:
            data["payer_id"] = payer_id
        if photo is not False:
            raw = make_jpeg() if photo is None else photo
            data["photo"] = (io.BytesIO(raw), filename)
        return self.client.post(
            "/receipts",
            data=data,
            content_type="multipart/form-data",
            headers=headers or {},
        )

    def test_upload_and_serve_photo(self):
        lst = self.new_list()
        r = self.upload_receipt(date="2026-11-10", list_id=lst["id"], photo=make_png(), filename="r.png")
        self.assertEqual(201, r.status_code, r.get_json())
        rec = r.get_json()
        self.assertEqual(lst["id"], rec["list_id"])
        self.assertEqual("2026-11-10", rec["date"])
        self.assertEqual("PLN", rec["currency"])
        self.assertEqual(MAMA, rec["payer_id"])
        self.assertEqual(MAMA, rec["created_by"])
        self.assertEqual("manual", rec["status"])
        self.assertIsNone(rec["total_minor"])
        self.assertIsNone(rec["read_error"])
        self.assertTrue(rec["photo_url"].startswith("/receipts/photo/"))

        # Serving photo requires token and sets Cache-Control: private
        no_auth = self.client.get(rec["photo_url"], headers={"Authorization": ""})
        self.assertEqual(401, no_auth.status_code)

        photo_resp = self.client.get(rec["photo_url"])
        self.assertEqual(200, photo_resp.status_code)
        self.assertTrue(photo_resp.cache_control.private)
        self.assertFalse(photo_resp.cache_control.public)
        photo_resp.close()

    def test_upload_validation(self):
        # Missing photo
        self.assertEqual(400, self.upload_receipt(photo=False).status_code)
        # Non-image
        self.assertEqual(415, self.upload_receipt(photo=b"not an image", filename="x.txt").status_code)
        # Too large (> 10 MB)
        self.assertEqual(413, self.upload_receipt(photo=b"x" * (MAX_PHOTO_BYTES + 1)).status_code)
        # Bad dates
        self.assertEqual(400, self.upload_receipt(date="2026-13-01").status_code)
        self.assertEqual(400, self.upload_receipt(date="2026-11-31").status_code)
        self.assertEqual(400, self.upload_receipt(date="10-11-2026").status_code)
        # Unknown list_id
        self.assertEqual(400, self.upload_receipt(list_id=9999).status_code)
        # Empty payer_id
        self.assertEqual(400, self.upload_receipt(payer_id="").status_code)

    def test_patch_rules_and_clearing(self):
        lst = self.new_list("Lidl")
        rec = self.upload_receipt(date="2026-11-10").get_json()

        # Anyone can edit; setting total_minor marks confirmed
        r = self.client.patch(
            f"/receipts/{rec['id']}",
            json={"shop": "  Lidl  ", "total_minor": 8410, "list_id": lst["id"], "payer_id": TATA},
            headers=self.as_tata(),
        )
        self.assertEqual(200, r.status_code, r.get_json())
        updated = r.get_json()
        self.assertEqual("Lidl", updated["shop"])
        self.assertEqual(8410, updated["total_minor"])
        self.assertEqual("confirmed", updated["status"])
        self.assertEqual(lst["id"], updated["list_id"])
        self.assertEqual(TATA, updated["payer_id"])

        # Clearing fields with null; clearing total_minor reverts status to manual
        r2 = self.client.patch(
            f"/receipts/{rec['id']}",
            json={"shop": None, "total_minor": None, "list_id": None},
        )
        self.assertEqual(200, r2.status_code, r2.get_json())
        cleared = r2.get_json()
        self.assertIsNone(cleared["shop"])
        self.assertIsNone(cleared["total_minor"])
        self.assertIsNone(cleared["list_id"])
        self.assertEqual("manual", cleared["status"])

        # Validation errors on PATCH
        self.assertEqual(400, self.client.patch(f"/receipts/{rec['id']}", json={"total_minor": 0}).status_code)
        self.assertEqual(400, self.client.patch(f"/receipts/{rec['id']}", json={"total_minor": True}).status_code)
        self.assertEqual(400, self.client.patch(f"/receipts/{rec['id']}", json={"total_minor": 100_000_001}).status_code)
        self.assertEqual(400, self.client.patch(f"/receipts/{rec['id']}", json={"shop": "x" * 101}).status_code)
        self.assertEqual(400, self.client.patch(f"/receipts/{rec['id']}", json={"payer_id": "   "}).status_code)
        self.assertEqual(400, self.client.patch(f"/receipts/{rec['id']}", json={"list_id": 9999}).status_code)

    def test_delete_creator_only_and_removes_file(self):
        rec = self.upload_receipt().get_json()
        filename = rec["photo_url"].rsplit("/", 1)[1]
        photo_path = os.path.join(self.receipts_dir, filename)
        self.assertTrue(os.path.isfile(photo_path))

        # Tata cannot delete Mama's receipt
        self.assertEqual(403, self.client.delete(f"/receipts/{rec['id']}", headers=self.as_tata()).status_code)
        self.assertTrue(os.path.isfile(photo_path))

        # Mama deletes it -> file is gone
        self.assertEqual(204, self.client.delete(f"/receipts/{rec['id']}").status_code)
        self.assertFalse(os.path.isfile(photo_path))
        self.assertEqual(404, self.client.get(f"/receipts/{rec['id']}").status_code)

    def test_deleting_list_keeps_receipts_with_null_list_id(self):
        lst = self.new_list()
        rec = self.upload_receipt(list_id=lst["id"]).get_json()
        self.assertEqual(204, self.client.delete(f"/lists/{lst['id']}").status_code)
        after = self.client.get(f"/receipts/{rec['id']}").get_json()
        self.assertIsNone(after["list_id"])

    def test_list_receipts_filters_and_ordering(self):
        lst = self.new_list()
        r_oct = self.upload_receipt(date="2026-10-31").get_json()
        r_nov1 = self.upload_receipt(date="2026-11-01", list_id=lst["id"]).get_json()
        r_nov2 = self.upload_receipt(date="2026-11-05").get_json()

        all_recs = self.client.get("/receipts").get_json()
        self.assertEqual([r_nov2["id"], r_nov1["id"], r_oct["id"]], [x["id"] for x in all_recs])

        nov_recs = self.client.get("/receipts?month=2026-11").get_json()
        self.assertEqual([r_nov2["id"], r_nov1["id"]], [x["id"] for x in nov_recs])

        list_recs = self.client.get(f"/receipts?list_id={lst['id']}").get_json()
        self.assertEqual([r_nov1["id"]], [x["id"] for x in list_recs])

        self.assertEqual(400, self.client.get("/receipts?month=2026-13").status_code)
        self.assertEqual(400, self.client.get("/receipts?list_id=abc").status_code)

    def test_spending_summary(self):
        # Oct 31 vs Nov 1 boundary, shop spelling grouping, missing total, unconfirmed with total
        r1 = self.upload_receipt(date="2026-10-31", payer_id=MAMA).get_json()
        self.client.patch(f"/receipts/{r1['id']}", json={"shop": "Biedronka", "total_minor": 1000})

        r2 = self.upload_receipt(date="2026-11-01", payer_id=MAMA).get_json()
        self.client.patch(f"/receipts/{r2['id']}", json={"shop": "Lidl ", "total_minor": 2000})

        r3 = self.upload_receipt(date="2026-11-02", payer_id=TATA).get_json()
        self.client.patch(f"/receipts/{r3['id']}", json={"shop": "LIDL", "total_minor": 3000})

        r4 = self.upload_receipt(date="2026-11-03", payer_id=TATA).get_json()
        self.client.patch(f"/receipts/{r4['id']}", json={"shop": "Lidl", "total_minor": 4000})

        # Receipt without shop
        r5 = self.upload_receipt(date="2026-11-04", payer_id=MAMA).get_json()
        self.client.patch(f"/receipts/{r5['id']}", json={"total_minor": 500})

        # Receipt without total
        self.upload_receipt(date="2026-11-05", payer_id=MAMA)

        res = self.client.get("/spending?from=2026-09&to=2026-11")
        self.assertEqual(200, res.status_code, res.get_json())
        body = res.get_json()
        self.assertEqual("PLN", body["currency"])
        self.assertEqual(["2026-09", "2026-10", "2026-11"], [m["month"] for m in body["months"]])

        sep, oct_m, nov = body["months"]
        self.assertEqual(0, sep["total_minor"])
        self.assertEqual(0, sep["receipt_count"])
        self.assertEqual([], sep["by_shop"])

        self.assertEqual(1000, oct_m["total_minor"])
        self.assertEqual(1, oct_m["receipt_count"])
        self.assertEqual([{"shop": "Biedronka", "total_minor": 1000}], oct_m["by_shop"])

        self.assertEqual(9500, nov["total_minor"])
        self.assertEqual(5, nov["receipt_count"])
        self.assertEqual(0, nov["unconfirmed_count"])
        self.assertEqual(1, nov["missing_total_count"])
        # "Lidl" appeared twice ("Lidl " trimmed + "Lidl") vs "LIDL" once
        self.assertEqual(
            [{"shop": "Lidl", "total_minor": 9000}, {"shop": None, "total_minor": 500}],
            nov["by_shop"],
        )
        self.assertEqual(
            [{"payer_id": TATA, "total_minor": 7000}, {"payer_id": MAMA, "total_minor": 2500}],
            nov["by_payer"],
        )

    def test_spending_validation(self):
        self.assertEqual(400, self.client.get("/spending").status_code)
        self.assertEqual(400, self.client.get("/spending?from=2026-11&to=2026-10").status_code)
        self.assertEqual(400, self.client.get("/spending?from=2024-01&to=2026-01").status_code)  # 25 months
        self.assertEqual(400, self.client.get("/spending?from=bad&to=2026-11").status_code)
