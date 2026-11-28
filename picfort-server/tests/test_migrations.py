"""Schema migration tests for all SQLite stores (T-075, ADR 0016)."""

import os
import sqlite3
import sys
import tempfile
import unittest
from contextlib import closing

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
sys.path.insert(0, os.path.dirname(__file__))

import app as server_app
import frame
from app import FrameController
from auth import Authenticator
from events import EventStore
from family import FamilyStore
from helpers import FakeImmich
from photo_ai import PhotoAiStore
from school import SchoolStore
from shopping import ShoppingStore
from slideshow import Picker, SettingsStore, Slideshow
from store import SqliteStore
from test_wall import Clock, FakeImmichAuth, MAMA
from wall import WallStore


def open_migrated(db_path: str, old_sql: str, open_store):
    """Create a SQLite DB with `old_sql` (including PRAGMA user_version), then open it with `open_store`."""
    with closing(sqlite3.connect(db_path)) as conn:
        conn.executescript(old_sql)
    return open_store(db_path)


class MigrationTests(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp()

    def tearDown(self):
        import shutil
        shutil.rmtree(self.dir, ignore_errors=True)

    def test_every_store_has_migrations_for_all_previous_versions(self):
        stores = [WallStore, ShoppingStore, FamilyStore, EventStore, SchoolStore, PhotoAiStore]
        for store_cls in stores:
            with self.subTest(store=store_cls.__name__):
                self.assertTrue(issubclass(store_cls, SqliteStore))
                for v in range(1, store_cls.SCHEMA_VERSION):
                    self.assertIn(v, store_cls.MIGRATIONS, f"{store_cls.__name__} missing migration for v{v}")

    def test_photo_ai_v5_migrates_album_suggestions_with_owner_id(self):
        db = os.path.join(self.dir, "photo_ai.db")
        PhotoAiStore(db)
        with closing(sqlite3.connect(db)) as conn:
            conn.executescript(
                """
                DROP INDEX idx_album_suggestions_owner;
                ALTER TABLE album_suggestions DROP COLUMN owner_id;
                INSERT INTO album_suggestions (
                    cluster_key, title, subtitle, cover_asset_id, asset_ids,
                    start_date, end_date, city, country, status, created_at
                ) VALUES ('k1', 'Trip', '12 photos', 'a1', '["a1"]', '2026-01-01', '2026-01-02', '', '', 'pending', '2026-01-02T00:00:00Z');
                PRAGMA user_version = 5;
                """
            )
        store = PhotoAiStore(db)
        self.assertIsNone(store.migration_error)
        self.assertTrue(os.path.exists(db + ".v5.bak"))
        self.assertEqual(1, len(store.list_album_suggestions()))
        self.assertEqual("", store.album_suggestion_owner("k1"))

    def test_family_v1_migrates_with_rows_intact(self):
        db = os.path.join(self.dir, "family.db")
        photos = os.path.join(self.dir, "family_photos")
        store = open_migrated(
            db,
            f"""
            CREATE TABLE profiles (
              user_id TEXT PRIMARY KEY,
              display_name TEXT,
              photo TEXT,
              hidden INTEGER,
              updated_by TEXT NOT NULL,
              updated TEXT NOT NULL
            );
            INSERT INTO profiles VALUES ('{MAMA}', 'Mama', NULL, 0, '{MAMA}', '2026-01-01T00:00:00Z');
            PRAGMA user_version = 1;
            """,
            lambda p: FamilyStore(p, photos),
        )
        self.assertIsNone(store.migration_error)
        self.assertTrue(os.path.exists(db + ".v1.bak"))
        profiles = {p["user_id"]: p for p in store.profiles()}
        self.assertEqual("Mama", profiles[MAMA]["display_name"])
        with closing(sqlite3.connect(db)) as conn:
            self.assertEqual(FamilyStore.SCHEMA_VERSION, conn.execute("PRAGMA user_version").fetchone()[0])

    def test_events_v1_migrates_with_rows_intact(self):
        db = os.path.join(self.dir, "events.db")
        store = open_migrated(
            db,
            """
            CREATE TABLE events (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              start_date TEXT NOT NULL,
              end_date TEXT,
              start_time TEXT,
              location TEXT,
              note TEXT,
              album_id TEXT,
              created_by TEXT NOT NULL,
              created TEXT NOT NULL,
              updated TEXT NOT NULL
            );
            CREATE INDEX events_start ON events(start_date);
            INSERT INTO events (title, start_date, end_date, created_by, created, updated)
              VALUES ('Picnic', '2026-06-15', '2026-06-15', 'u1', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z');
            PRAGMA user_version = 1;
            """,
            EventStore,
        )
        self.assertIsNone(store.migration_error)
        self.assertTrue(os.path.exists(db + ".v1.bak"))
        upcoming = store.upcoming("2026-01-01")
        self.assertEqual(1, len(upcoming))
        self.assertEqual("Picnic", upcoming[0]["title"])

    def test_shopping_v1_migrates_with_rows_intact_and_wishlist_table_ready(self):
        db = os.path.join(self.dir, "shopping.db")
        store = open_migrated(
            db,
            """
            CREATE TABLE lists (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              title TEXT NOT NULL,
              archived INTEGER NOT NULL DEFAULT 0,
              revision INTEGER NOT NULL DEFAULT 1,
              created_by TEXT NOT NULL,
              created TEXT NOT NULL,
              updated TEXT NOT NULL
            );
            CREATE TABLE items (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              list_id INTEGER NOT NULL REFERENCES lists(id) ON DELETE CASCADE,
              text TEXT NOT NULL,
              checked INTEGER NOT NULL DEFAULT 0,
              position REAL NOT NULL,
              checked_by TEXT,
              checked_at TEXT,
              created_by TEXT NOT NULL
            );
            CREATE TABLE receipts (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              list_id INTEGER REFERENCES lists(id) ON DELETE SET NULL,
              photo TEXT,
              shop TEXT,
              date TEXT NOT NULL,
              total_minor INTEGER,
              payer_id TEXT NOT NULL,
              status TEXT NOT NULL,
              read_error TEXT,
              created_by TEXT NOT NULL,
              created TEXT NOT NULL,
              updated TEXT NOT NULL
            );
            INSERT INTO lists (id, title, created_by, created, updated)
              VALUES (1, 'Biedronka', 'u1', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z');
            INSERT INTO items (list_id, text, position, created_by) VALUES (1, 'Milk', 1.0, 'u1');
            PRAGMA user_version = 1;
            """,
            ShoppingStore,
        )
        self.assertIsNone(store.migration_error)
        self.assertTrue(os.path.exists(db + ".v1.bak"))
        lists = store.lists()
        self.assertEqual(1, len(lists))
        self.assertEqual("Biedronka", lists[0]["title"])
        item = store.create_wishlist_item("u1", {"url": "https://shop.example/a", "title": "Toy"})
        self.assertEqual("Toy", item["title"])

    def test_failed_migration_leaves_db_untouched_and_endpoints_return_503(self):
        class BrokenStore(SqliteStore):
            SCHEMA_VERSION = 2
            NAME = "broken"
            SCHEMA = "CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT NOT NULL);"
            MIGRATIONS = {1: ["THIS IS INVALID SQL;"]}

        db = os.path.join(self.dir, "broken.db")
        with closing(sqlite3.connect(db)) as conn, conn:
            conn.executescript(
                """
                CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT NOT NULL);
                INSERT INTO items (id, name) VALUES (1, 'Keep me');
                PRAGMA user_version = 1;
                """
            )
        broken = BrokenStore(db)
        self.assertIsNotNone(broken.migration_error)
        with closing(sqlite3.connect(db)) as conn:
            self.assertEqual(1, conn.execute("PRAGMA user_version").fetchone()[0])
            self.assertEqual("Keep me", conn.execute("SELECT name FROM items WHERE id = 1").fetchone()[0])

        # Also check Flask returns 503 for a store with migration_error while healthy stores stay 200.
        wall = WallStore(os.path.join(self.dir, "wall.db"), os.path.join(self.dir, "wall_photos"), clock=Clock())
        wall.create(MAMA, "Mama", "still up")
        shopping_db = os.path.join(self.dir, "shopping.db")
        ShoppingStore(shopping_db)
        with closing(sqlite3.connect(shopping_db)) as conn:
            conn.execute("PRAGMA user_version = 99")
        bad_shopping = ShoppingStore(shopping_db)
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
        flask_app = server_app.create_app(
            controller,
            slideshow,
            FakeImmich(),
            wall=wall,
            auth=Authenticator(FakeImmichAuth()),
            shopping=bad_shopping,
        )
        client = flask_app.test_client()
        client.environ_base["HTTP_AUTHORIZATION"] = "Bearer mama-token"
        self.assertEqual(503, client.get("/lists").status_code)
        self.assertEqual(200, client.get("/wall").status_code)
