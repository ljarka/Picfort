"""Base for the family-hub SQLite stores (ADR 0008, ADR 0016): one file per feature.

When a store's SCHEMA_VERSION changes, the old file is copied to `<file>.v<old>.bak` before
running registered migrations in `MIGRATIONS` (ADR 0016). If a migration is missing or fails,
the error is logged, the database file is left untouched, and the store's endpoints answer
503 until fixed — no store ever starts empty.
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
        self.migration_error: str | None = None
        self._init_schema()

    def summary(self) -> dict:
        """File size, schema version and row counts per table, for the dashboard (T-095).

        Counts only: no contents. Works even when a migration failed (reads the raw file).
        """
        info = {
            "file": os.path.basename(self.db_path),
            "size_bytes": os.path.getsize(self.db_path) if os.path.exists(self.db_path) else None,
            "schema_version": None,
            "expected_version": self.SCHEMA_VERSION,
            "migration_error": self.migration_error,
            "tables": {},
        }
        try:
            with closing(self._raw_connect()) as conn:
                info["schema_version"] = conn.execute("PRAGMA user_version").fetchone()[0]
                names = [r[0] for r in conn.execute(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
                )]
                for name in names:
                    quoted = '"' + name.replace('"', '""') + '"'
                    info["tables"][name] = conn.execute(f"SELECT COUNT(*) FROM {quoted}").fetchone()[0]
        except sqlite3.Error as e:
            info["error"] = str(e)
        return info

    def require_ready(self):
        if self.migration_error:
            raise StoreError(self.migration_error, status=503)

    def connect(self) -> sqlite3.Connection:
        self.require_ready()
        return self._raw_connect()

    def _raw_connect(self) -> sqlite3.Connection:
        # One connection per call: Flask serves requests on several threads. SQLite leaves
        # foreign keys off unless every connection turns them on.
        conn = sqlite3.connect(self.db_path, timeout=10)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys = ON")
        return conn

    def _init_schema(self):
        with closing(self._raw_connect()) as conn:
            version = conn.execute("PRAGMA user_version").fetchone()[0]
            has_tables = conn.execute("SELECT 1 FROM sqlite_master WHERE type = 'table'").fetchone()
        if version == self.SCHEMA_VERSION:
            return
        if version == 0 and not has_tables:
            with closing(self._raw_connect()) as conn, conn:
                conn.executescript(self.SCHEMA)
                conn.execute(f"PRAGMA user_version = {self.SCHEMA_VERSION}")
            return
        if not self._can_migrate(version):
            msg = f"{self.NAME} schema changed ({version} -> {self.SCHEMA_VERSION}): no migration path; store unavailable"
            log.error(msg)
            self.migration_error = msg
            return
        try:
            self._migrate(version)
        except Exception as e:
            msg = f"{self.NAME} schema migration ({version} -> {self.SCHEMA_VERSION}) failed: {e}"
            log.exception(msg)
            self.migration_error = msg

    def _can_migrate(self, version: int) -> bool:
        return 0 < version < self.SCHEMA_VERSION and all(v in self.MIGRATIONS for v in range(version, self.SCHEMA_VERSION))

    def _migrate(self, version: int):
        backup = f"{self.db_path}.v{version}.bak"
        log.warning("%s schema changed (%s -> %s): migrating, copy of the old data in %s",
                    self.NAME, version, self.SCHEMA_VERSION, backup)
        shutil.copy2(self.db_path, backup)
        with closing(self._raw_connect()) as conn, conn:
            for step in range(version, self.SCHEMA_VERSION):
                for statement in self.MIGRATIONS[step]:
                    conn.execute(statement)
            conn.execute(f"PRAGMA user_version = {self.SCHEMA_VERSION}")
