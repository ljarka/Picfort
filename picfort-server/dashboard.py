"""Server dashboard (T-095): a read-only web page with the server's health and statistics.

`GET /dashboard` is the page (open, like `/frame`: it holds no data); `GET /dashboard/data` is
the JSON behind it, guarded by PICFORT_API_KEY like every other path. Counts only: no names,
texts, amounts, tokens or photo descriptions.
"""

import glob
import logging
import os
import platform
import shutil
import threading
import time
from collections import deque
from datetime import datetime, timezone

from flask import Flask, jsonify, send_file

log = logging.getLogger("dashboard")

DASHBOARD_PAGE_PATH = "/dashboard"
PAGE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dashboard_page.html")
RECENT_LOG_SIZE = 50
# Immich's version and library size, and the size of /data, change slowly: refreshed at most this often.
SLOW_TTL = 60.0


class RecentLogHandler(logging.Handler):
    """Keeps the last warnings and errors in memory for the dashboard; nothing is written to disk."""

    def __init__(self, capacity: int = RECENT_LOG_SIZE):
        super().__init__(level=logging.WARNING)
        self.records = deque(maxlen=capacity)

    def emit(self, record: logging.LogRecord):
        try:
            message = record.getMessage()
        except Exception:
            message = str(record.msg)
        self.records.append({
            "time": datetime.fromtimestamp(record.created, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "level": record.levelname,
            "logger": record.name,
            "message": message[:500],
        })

    def recent(self) -> list[dict]:
        return list(reversed(self.records))  # newest first


RECENT_LOGS = RecentLogHandler()


def install_recent_log_handler():
    root = logging.getLogger()
    if RECENT_LOGS not in root.handlers:
        root.addHandler(RECENT_LOGS)


class _Cached:
    """Calls `fn` at most once per `ttl` seconds; a failure is cached too (as None)."""

    def __init__(self, fn, ttl: float, clock=time.monotonic):
        self.fn, self.ttl, self.clock = fn, ttl, clock
        self.lock = threading.Lock()
        self.value, self.at = None, None

    def __call__(self):
        with self.lock:
            now = self.clock()
            if self.at is None or now - self.at >= self.ttl:
                try:
                    self.value = self.fn()
                except Exception as e:
                    log.debug("Dashboard lookup failed: %s", e)
                    self.value = None
                self.at = now
            return self.value


def dir_size(path: str) -> int:
    total = 0
    for root, _dirs, files in os.walk(path):
        for name in files:
            try:
                total += os.path.getsize(os.path.join(root, name))
            except OSError:
                pass
    return total


def backups_in(data_dir: str) -> list[dict]:
    """`*.bak` copies the stores leave before a migration (ADR 0016), newest first."""
    found = []
    for path in glob.glob(os.path.join(data_dir, "*.bak")):
        try:
            st = os.stat(path)
        except OSError:
            continue
        found.append({
            "file": os.path.basename(path),
            "size_bytes": st.st_size,
            "modified": datetime.fromtimestamp(st.st_mtime, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        })
    return sorted(found, key=lambda b: b["modified"], reverse=True)


def problems_of(data: dict) -> list[dict]:
    """What needs attention, worst first: "error" (red) or "warning" (amber)."""
    out = []
    immich = data["immich"]["state"]
    if immich != "ok":
        out.append({"level": "error", "text": f"Immich: {immich}"})
    for name, store in data["stores"].items():
        if store.get("migration_error"):
            out.append({"level": "error", "text": f"{name}: {store['migration_error']}"})
    frame_error = data["frame"]["slideshow"].get("last_error") or data["frame"].get("last_error")
    if frame_error:
        out.append({"level": "warning", "text": f"Frame: {frame_error}"})
    ai = data.get("photo_ai")
    if ai and ai.get("last_error"):
        out.append({"level": "warning", "text": f"Photo AI: {ai['last_error']}"})
    disk = data["server"].get("disk")
    if disk and disk["total_bytes"] and disk["free_bytes"] / disk["total_bytes"] < 0.05:
        out.append({"level": "warning", "text": "Less than 5% disk space left on /data"})
    errors = sum(1 for r in data["recent_logs"] if r["level"] in ("ERROR", "CRITICAL"))
    if errors:
        out.append({"level": "warning", "text": f"{errors} error(s) in the recent log"})
    return sorted(out, key=lambda p: p["level"] != "error")


def register_dashboard(app: Flask, *, controller, slideshow, settings_json, immich, immich_health,
                       stores: dict, photo_ai=None, photo_ai_worker=None, browser_frame: bool = False,
                       data_dir: str | None = None, now=time.time, clock=time.monotonic):
    """`stores`: {name: SqliteStore}. `data_dir` defaults to the folder of the first store."""
    install_recent_log_handler()
    started_at = now()
    if data_dir is None:
        first = next(iter(stores.values()), None)
        data_dir = os.path.dirname(os.path.abspath(first.db_path)) if first is not None else os.getcwd()
    immich_version = _Cached(lambda: immich.server_version(), SLOW_TTL, clock)
    immich_stats = _Cached(lambda: immich.asset_statistics(), SLOW_TTL, clock)
    data_size = _Cached(lambda: dir_size(data_dir), SLOW_TTL, clock)

    def no_store(resp):
        resp.headers["Cache-Control"] = "private, no-store"
        return resp

    @app.route(DASHBOARD_PAGE_PATH)
    def dashboard_page():
        resp = send_file(PAGE_FILE, mimetype="text/html", max_age=0)
        resp.headers["X-Robots-Tag"] = "noindex"
        resp.headers["Referrer-Policy"] = "no-referrer"
        return no_store(resp)

    def server_section() -> dict:
        try:
            usage = shutil.disk_usage(data_dir)
            disk = {"total_bytes": usage.total, "free_bytes": usage.free}
        except OSError:
            disk = None
        t = now()
        return {
            "version": os.environ.get("PICFORT_BUILD_VERSION", "dev"),
            "revision": os.environ.get("PICFORT_BUILD_REVISION", ""),
            "started_at": datetime.fromtimestamp(started_at, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "uptime_seconds": int(t - started_at),
            "now": datetime.fromtimestamp(t, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "python": platform.python_version(),
            "disk": disk,
            "data_bytes": data_size(),
            "api_key": bool(browser_frame),
        }

    def immich_section() -> dict:
        started = clock()
        state = immich_health()
        return {
            "state": state,
            "check_ms": int((clock() - started) * 1000),
            "version": immich_version() if state == "ok" else None,
            "library": immich_stats() if state == "ok" else None,
        }

    def frame_section() -> dict:
        with controller.lock:
            body = {
                "busy": controller.busy,
                "current_asset": controller.current_asset,
                "last_error": controller.last_error,
                "width": controller.config.width,
                "height": controller.config.height,
                "hardware": bool(controller.config.driver),
            }
        body["browser_frame"] = bool(browser_frame)
        body["slideshow"] = settings_json(slideshow)
        return body

    def photo_ai_section(library: dict | None) -> dict | None:
        if photo_ai is None:
            return None
        cfg = photo_ai_worker.config if photo_ai_worker is not None else None
        body = {
            "enabled": bool(cfg and cfg.enabled),
            "model": cfg.ollama_model if cfg else "",
            "batch_size": cfg.batch_size if cfg else None,
            "interval_minutes": cfg.interval_minutes if cfg else None,
            "active_hours": list(cfg.active_hours) if cfg and cfg.active_hours else None,
            "tag_immich": bool(cfg and cfg.tag_immich),
            "running": bool(photo_ai_worker and photo_ai_worker.running),
            "last_error": photo_ai_worker.last_error if photo_ai_worker else None,
        }
        try:
            body.update(photo_ai.dashboard_stats())
        except Exception as e:
            body["stats_error"] = str(e)
        images = (library or {}).get("images")
        body["coverage"] = round(body.get("analyzed", 0) / images, 4) if images else None
        # Whose libraries are analyzed (T-104): names and counts only, never keys.
        try:
            counts = photo_ai.owned_counts()
            members = photo_ai.library_keys()
            member_ids = {m["user_id"] for m in members}
            libraries = [{
                "name": "Server key (IMMICH_API_KEY)",
                "analyzed": sum(n for owner, n in counts.items() if owner not in member_ids),
                "last_error": photo_ai_worker.last_error if photo_ai_worker else None,
            }]
            libraries += [
                {"name": m["user_name"] or "Member", "analyzed": counts.get(m["user_id"], 0),
                 "last_error": m["last_error"] or None}
                for m in members
            ]
            body["libraries"] = libraries
        except Exception as e:
            body["libraries_error"] = str(e)
        return body

    @app.route(DASHBOARD_PAGE_PATH + "/data")
    def dashboard_data():
        immich_info = immich_section()
        data = {
            "server": server_section(),
            "immich": immich_info,
            "frame": frame_section(),
            "photo_ai": photo_ai_section(immich_info["library"]),
            "stores": {name: store.summary() for name, store in stores.items()},
            "backups": backups_in(data_dir),
            "recent_logs": RECENT_LOGS.recent(),
        }
        data["problems"] = problems_of(data)
        return no_store(jsonify(data))
