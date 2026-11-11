"""Base for the family-hub SQLite stores (ADR 0008): one file per feature.

No migrations: when a store's SCHEMA_VERSION changes, the old file is kept as
`<file>.v<old>.bak` and the feature starts empty. Other features' files are untouched.
"""

import logging
import os
import sqlite3
from contextlib import closing
from datetime import datetime, timezone

log = logging.getLogger("store")


class StoreError(Exception):
    """A request the store refuses; `status` is the HTTP status to answer with."""

    def __init__(self, message: str, status: int = 400):
        super().__init__(message)
        self.status = status


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class SqliteStore:
    SCHEMA_VERSION = 1
    SCHEMA = ""  # an executescript() of CREATE statements
    NAME = "store"  # for log messages

    def __init__(self, db_path: str, clock=utc_now):
        self.db_path = db_path
        self.clock = clock
        self._init_schema()

    def connect(self) -> sqlite3.Connection:
        # One connection per call: Flask serves requests on several threads. SQLite leaves
        # foreign keys off unless every connection turns them on.
        conn = sqlite3.connect(self.db_path, timeout=10)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON")
        return conn

    def _init_schema(self):
        with closing(self.connect()) as conn:
            version = conn.execute("PRAGMA user_version").fetchone()[0]
            has_tables = conn.execute("SELECT 1 FROM sqlite_master WHERE type = 'table'").fetchone()
        if version == self.SCHEMA_VERSION:
            return
        if version != 0 or has_tables:
            backup = f"{self.db_path}.v{version}.bak"
            log.warning("%s schema changed (%s -> %s): kept the old data in %s, starting empty",
                        self.NAME, version, self.SCHEMA_VERSION, backup)
            os.replace(self.db_path, backup)
        with closing(self.connect()) as conn, conn:
            conn.executescript(self.SCHEMA)
            conn.execute(f"PRAGMA user_version = {self.SCHEMA_VERSION}")
