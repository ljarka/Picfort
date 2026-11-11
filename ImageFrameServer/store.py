"""Base for the family-hub SQLite stores (ADR 0008): one file per feature.

When a store's SCHEMA_VERSION changes, the old file is kept as `<file>.v<old>.bak`. Then either
a registered additive migration (ADR 0010: only `family.db` and `events.db`, nullable columns) brings
the data forward, or, with no migration for that version, the feature starts empty (ADR 0008).
Other features' files are untouched.
"""

import logging
import os
import shutil
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
    # {old version: [SQL statements]} taking the schema from that version to the next one.
    MIGRATIONS: dict = {}

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
        if self._can_migrate(version):
            self._migrate(version)
            return
        if version != 0 or has_tables:
            backup = f"{self.db_path}.v{version}.bak"
            log.warning("%s schema changed (%s -> %s): kept the old data in %s, starting empty",
                        self.NAME, version, self.SCHEMA_VERSION, backup)
            os.replace(self.db_path, backup)
        with closing(self.connect()) as conn, conn:
            conn.executescript(self.SCHEMA)
            conn.execute(f"PRAGMA user_version = {self.SCHEMA_VERSION}")

    def _can_migrate(self, version: int) -> bool:
        return 0 < version < self.SCHEMA_VERSION and all(v in self.MIGRATIONS for v in range(version, self.SCHEMA_VERSION))

    def _migrate(self, version: int):
        backup = f"{self.db_path}.v{version}.bak"
        log.warning("%s schema changed (%s -> %s): migrating, copy of the old data in %s",
                    self.NAME, version, self.SCHEMA_VERSION, backup)
        shutil.copy2(self.db_path, backup)
        with closing(self.connect()) as conn, conn:
            for step in range(version, self.SCHEMA_VERSION):
                for statement in self.MIGRATIONS[step]:
                    conn.execute(statement)
            conn.execute(f"PRAGMA user_version = {self.SCHEMA_VERSION}")
