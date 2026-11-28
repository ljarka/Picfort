"""Optional Ollama Vision-LLM photo insights, Immich ai/* tagging, and cleanup store (ADR 0019, T-089)."""

import base64
import json
import logging
import os
import threading
import time
from contextlib import closing
from dataclasses import dataclass
from datetime import date, datetime

import requests

from store import SqliteStore, utc_now

log = logging.getLogger("photo_ai")

ALBUM_NAME_SCHEMA = {
    "type": "object",
    "properties": {
        "title": {"type": "string"},
        "worth_album": {"type": "boolean"},
    },
    "required": ["title", "worth_album"],
}

ALBUM_MIN_PHOTOS = 12
ALBUM_MAX_GAP_DAYS = 3


def _parse_iso_day(value: str | None) -> date | None:
    if not value or len(value) < 10:
        return None
    try:
        return date.fromisoformat(value[:10])
    except ValueError:
        return None


def _format_date_range(start: date, end: date) -> str:
    if start == end:
        return start.strftime("%d %b %Y").lstrip("0")
    if start.year == end.year and start.month == end.month:
        return f"{start.day}–{end.day} {end.strftime('%b %Y')}"
    if start.year == end.year:
        return f"{start.day} {start.strftime('%b')} – {end.day} {end.strftime('%b %Y')}"
    return f"{start.strftime('%d %b %Y').lstrip('0')} – {end.strftime('%d %b %Y').lstrip('0')}"


def cluster_album_candidates(
    items: list[dict],
    min_photos: int = ALBUM_MIN_PHOTOS,
    max_gap_days: int = ALBUM_MAX_GAP_DAYS,
) -> list[dict]:
    """Group unassigned camera photos by location + date proximity into album candidates (T-098)."""
    parsed = []
    for a in items:
        aid = a.get("id")
        if not aid:
            continue
        name = (a.get("originalFileName") or "").lower()
        if "screenshot" in name:
            continue
        exif = a.get("exifInfo") or {}
        if not (exif.get("make") or exif.get("model")):
            continue
        day = _parse_iso_day(a.get("localDateTime") or a.get("fileCreatedAt"))
        if day is None:
            continue
        city = (exif.get("city") or "").strip()
        country = (exif.get("country") or "").strip()
        parsed.append((day, aid, city, country))

    parsed.sort(key=lambda x: (x[0], x[1]))
    clusters: list[dict] = []
    cur: dict | None = None

    def flush():
        if cur and len(cur["asset_ids"]) >= min_photos:
            start, end = cur["start"], cur["end"]
            city, country = cur["city"], cur["country"]
            slug = (city or "day").lower().replace(" ", "-")
            key = f"{start.isoformat()}..{end.isoformat()}|{slug}"
            date_label = _format_date_range(start, end)
            fallback_title = f"{city} — {start.strftime('%b %Y')}" if city else f"Photos · {date_label}"
            clusters.append({
                "cluster_key": key,
                "fallback_title": fallback_title,
                "subtitle": f"{len(cur['asset_ids'])} photos · {date_label}",
                "asset_ids": list(cur["asset_ids"]),
                "start_date": start.isoformat(),
                "end_date": end.isoformat(),
                "city": city,
                "country": country,
            })

    for day, aid, city, country in parsed:
        same_cluster = False
        if cur is not None:
            gap = (day - cur["end"]).days
            if city and cur["city"] and city.lower() == cur["city"].lower() and gap <= max_gap_days:
                same_cluster = True
            elif not city and not cur["city"] and gap == 0:
                same_cluster = True
        if same_cluster:
            cur["end"] = day
            cur["asset_ids"].append(aid)
        else:
            flush()
            cur = {"start": day, "end": day, "city": city, "country": country, "asset_ids": [aid]}
    flush()
    clusters.sort(key=lambda c: c["end_date"], reverse=True)
    return clusters

CATEGORIES = ("photo", "screenshot", "document", "receipt", "meme", "accidental", "kids_art")
# Categories that are camera photos for the "hide screenshots & documents" filters (T-099);
# the others (screenshot, document, receipt, meme) are what those filters hide.
AI_PHOTO_CATEGORIES = ("photo", "accidental", "kids_art")
ISSUES = ("blurry", "dark", "overexposed", "closed_eyes", "cut_off", "cluttered", "accidental")

CLEANUP_TAGS = (
    "ai/cleanup/low-quality",
    "ai/cleanup/blurry",
    "ai/cleanup/accidental",
    "ai/cleanup/closed-eyes",
)
TYPE_TAG_MAP = {
    "screenshot": "ai/type/screenshot",
    "document": "ai/type/document",
    "receipt": "ai/type/receipt",
    "meme": "ai/type/meme",
    "kids_art": "ai/type/kids-art",
}
NATURE_TAG = "ai/type/nature"
SENSITIVE_TAG = "ai/privacy/sensitive"
FRAME_WORTHY_TAG = "ai/best/frame-worthy"
# "Best" (frame-worthy) needs the top quality score: at >= 4 the model marked ~60% of photos
# (owner review 2026-11-28, T-103).
BEST_MIN_QUALITY = 5
ALL_AI_TAGS = list(CLEANUP_TAGS) + list(TYPE_TAG_MAP.values()) + [NATURE_TAG, SENSITIVE_TAG, FRAME_WORTHY_TAG]

# `scene` is FIRST so the vision model describes the image before committing to category/quality.
PHOTO_AI_SCHEMA = {
    "type": "object",
    "properties": {
        "scene": {"type": "string"},
        "category": {"type": "string", "enum": list(CATEGORIES)},
        "is_nature": {"type": "boolean"},
        "is_sensitive": {"type": "boolean"},
        "quality": {"type": "integer", "minimum": 1, "maximum": 5},
        "issues": {"type": "array", "items": {"type": "string", "enum": list(ISSUES)}},
        "frame_worthy": {"type": "boolean"},
        "cleanup_candidate": {"type": "boolean"},
        "cleanup_reason": {"type": "string"},
    },
    "required": [
        "scene", "category", "is_nature", "is_sensitive", "quality", "issues",
        "frame_worthy", "cleanup_candidate", "cleanup_reason",
    ],
}

PHOTO_AI_PROMPT = (
    "Analyze this image for a family photo library. Return strict JSON matching the schema in exact key order. "
    "1) scene: short 3-6 word description of what is in the image (no personal names). "
    "2) category: 'photo' for normal camera photos, 'screenshot' for phone/PC screenshots, "
    "'document' for pages/notes/signs, 'receipt' for store receipts/invoices, 'meme' for graphics/memes, "
    "'kids_art' for children's drawings/crafts, 'accidental' ONLY for unintentional pocket/floor/completely blurred shots. "
    "3) is_nature: true for landscapes, mountains, forests, sea, sky, sunsets, flowers or wildlife. "
    "4) is_sensitive: true if the image shows sensitive personal data (ID card, passport, PESEL, bank/card numbers, "
    "home address, medical records, passwords) or nudity / intimate private moments that should stay private. "
    "5) quality: 1=unusable, 2=poor/blurry, 3=ordinary, 4=good, 5=great sharp photo. "
    "6) issues: technical flaws present (empty array if none). "
    "7) frame_worthy: true only for exceptional photos worth hanging on the family's wall: sharp, well composed, "
    "with a strong moment, face or view. Be selective: most good photos are NOT frame-worthy, about 1 in 10 is. "
    "8) cleanup_candidate: true for blurry, accidental, unusable shots, screenshots, receipts or temporary documents "
    "(never mark kids_art as cleanup_candidate). "
    "9) cleanup_reason: empty string if cleanup_candidate is false, else short reason (<=6 words)."
)


def tags_for_insight(insight: dict) -> list[str]:
    """Hierarchical Immich `ai/*` tags derived from a structured insight. Ordinary photos get []."""
    tags: list[str] = []
    category = insight.get("category", "photo")
    quality = int(insight.get("quality", 3))
    issues = set(insight.get("issues") or ())
    is_sensitive = bool(insight.get("is_sensitive"))
    if quality <= 2:
        tags.append("ai/cleanup/low-quality")
    if "blurry" in issues:
        tags.append("ai/cleanup/blurry")
    if category == "accidental" or "accidental" in issues:
        tags.append("ai/cleanup/accidental")
    if "closed_eyes" in issues:
        tags.append("ai/cleanup/closed-eyes")
    if category in TYPE_TAG_MAP:
        tags.append(TYPE_TAG_MAP[category])
    if insight.get("is_nature"):
        tags.append(NATURE_TAG)
    if is_sensitive:
        tags.append(SENSITIVE_TAG)
    if insight.get("frame_worthy") and not is_sensitive and category == "photo" and quality >= BEST_MIN_QUALITY:
        tags.append(FRAME_WORTHY_TAG)
    return tags


# An asset whose analysis failed this many times is skipped for good (no poison pill, T-093).
MAX_ATTEMPTS = 3
# Pages of 100 the worker reads per batch while looking for unanalyzed photos (T-093).
MAX_SCAN_PAGES = 20


def parse_active_hours(spec: str) -> tuple[int, int] | None:
    """Parse '23-7' into (23, 7); None (always active) if empty or invalid."""
    spec = (spec or "").strip()
    if not spec:
        return None
    parts = spec.split("-", 1)
    try:
        start, end = int(parts[0]), int(parts[1])
    except (IndexError, ValueError):
        log.warning("Ignoring invalid OLLAMA_ACTIVE_HOURS=%r (expected e.g. 23-7)", spec)
        return None
    if not (0 <= start <= 23 and 0 <= end <= 23):
        log.warning("Ignoring invalid OLLAMA_ACTIVE_HOURS=%r (hours must be 0-23)", spec)
        return None
    return start, end


def _env_int(name: str, default: int) -> int:
    """Positive int from the environment; a bad value logs and falls back instead of crashing startup."""
    raw = os.environ.get(name, "").strip()
    if not raw:
        return default
    try:
        return max(1, int(raw))
    except ValueError:
        log.warning("Ignoring invalid %s=%r, using %s", name, raw, default)
        return default


def in_active_hours(window: tuple[int, int] | None, now: datetime) -> bool:
    if window is None or window[0] == window[1]:
        return True
    start, end = window
    h = now.hour
    if start < end:
        return start <= h < end
    return h >= start or h < end  # wraps midnight, e.g. 23 -> 7


class PhotoAiStore(SqliteStore):
    SCHEMA_VERSION = 4
    NAME = "photo_ai"
    MIGRATIONS = {
        1: ["ALTER TABLE photo_insights ADD COLUMN is_sensitive INTEGER NOT NULL DEFAULT 0"],
        2: [
            """CREATE TABLE photo_failures (
                asset_id   TEXT PRIMARY KEY,
                attempts   INTEGER NOT NULL DEFAULT 0,
                last_error TEXT NOT NULL DEFAULT '',
                failed_at  TEXT NOT NULL
            )""",
            "CREATE TABLE worker_state (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        ],
        3: [
            """CREATE TABLE album_suggestions (
                cluster_key    TEXT PRIMARY KEY,
                title          TEXT NOT NULL,
                subtitle       TEXT NOT NULL DEFAULT '',
                cover_asset_id TEXT NOT NULL,
                asset_ids      TEXT NOT NULL,
                start_date     TEXT NOT NULL,
                end_date       TEXT NOT NULL,
                city           TEXT NOT NULL DEFAULT '',
                country        TEXT NOT NULL DEFAULT '',
                status         TEXT NOT NULL DEFAULT 'pending',
                created_at     TEXT NOT NULL
            )""",
            "CREATE INDEX idx_album_suggestions_status ON album_suggestions(status, end_date DESC)",
        ],
    }
    SCHEMA = """
    CREATE TABLE photo_insights (
        asset_id          TEXT PRIMARY KEY,
        model             TEXT NOT NULL,
        scene             TEXT NOT NULL DEFAULT '',
        category          TEXT NOT NULL DEFAULT 'photo',
        is_nature         INTEGER NOT NULL DEFAULT 0,
        is_sensitive      INTEGER NOT NULL DEFAULT 0,
        quality           INTEGER NOT NULL DEFAULT 3,
        issues            TEXT NOT NULL DEFAULT '[]',
        frame_worthy      INTEGER NOT NULL DEFAULT 0,
        cleanup_candidate INTEGER NOT NULL DEFAULT 0,
        cleanup_reason    TEXT NOT NULL DEFAULT '',
        dismissed         INTEGER NOT NULL DEFAULT 0,
        tags_applied      TEXT NOT NULL DEFAULT '[]',
        analyzed_at       TEXT NOT NULL
    );
    CREATE INDEX idx_photo_insights_cleanup ON photo_insights(cleanup_candidate, dismissed, quality);
    -- Assets whose analysis failed (bad preview, invalid model JSON); skipped after MAX_ATTEMPTS.
    CREATE TABLE photo_failures (
        asset_id   TEXT PRIMARY KEY,
        attempts   INTEGER NOT NULL DEFAULT 0,
        last_error TEXT NOT NULL DEFAULT '',
        failed_at  TEXT NOT NULL
    );
    -- Worker bookkeeping, e.g. the library page the deep scan resumes from.
    CREATE TABLE worker_state (key TEXT PRIMARY KEY, value TEXT NOT NULL);
    -- Suggested albums built from unassigned location/date clusters (T-098).
    CREATE TABLE album_suggestions (
        cluster_key    TEXT PRIMARY KEY,
        title          TEXT NOT NULL,
        subtitle       TEXT NOT NULL DEFAULT '',
        cover_asset_id TEXT NOT NULL,
        asset_ids      TEXT NOT NULL,
        start_date     TEXT NOT NULL,
        end_date       TEXT NOT NULL,
        city           TEXT NOT NULL DEFAULT '',
        country        TEXT NOT NULL DEFAULT '',
        status         TEXT NOT NULL DEFAULT 'pending',
        created_at     TEXT NOT NULL
    );
    CREATE INDEX idx_album_suggestions_status ON album_suggestions(status, end_date DESC);
    """

    def save(self, asset_id: str, model: str, insight: dict, tags_applied: list[str] | None = None):
        now = self.clock()
        issues = [i for i in (insight.get("issues") or []) if i in ISSUES]
        category = insight.get("category", "photo")
        if category not in CATEGORIES:
            category = "photo"
        quality = max(1, min(5, int(insight.get("quality", 3))))
        is_sensitive = bool(insight.get("is_sensitive"))
        cleanup = bool(insight.get("cleanup_candidate")) and category != "kids_art"
        frame_worthy = bool(insight.get("frame_worthy")) and not is_sensitive
        with closing(self.connect()) as conn, conn:
            conn.execute(
                """
                INSERT INTO photo_insights (
                    asset_id, model, scene, category, is_nature, is_sensitive, quality, issues,
                    frame_worthy, cleanup_candidate, cleanup_reason, dismissed, tags_applied, analyzed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                ON CONFLICT(asset_id) DO UPDATE SET
                    model = excluded.model,
                    scene = excluded.scene,
                    category = excluded.category,
                    is_nature = excluded.is_nature,
                    is_sensitive = excluded.is_sensitive,
                    quality = excluded.quality,
                    issues = excluded.issues,
                    frame_worthy = excluded.frame_worthy,
                    cleanup_candidate = excluded.cleanup_candidate,
                    cleanup_reason = excluded.cleanup_reason,
                    tags_applied = excluded.tags_applied,
                    analyzed_at = excluded.analyzed_at
                """,
                (
                    asset_id,
                    model,
                    str(insight.get("scene") or "")[:200],
                    category,
                    1 if insight.get("is_nature") else 0,
                    1 if is_sensitive else 0,
                    quality,
                    json.dumps(issues),
                    1 if frame_worthy else 0,
                    1 if cleanup else 0,
                    str(insight.get("cleanup_reason") or "")[:120] if cleanup else "",
                    json.dumps(list(tags_applied or ())),
                    now,
                ),
            )
            conn.execute("DELETE FROM photo_failures WHERE asset_id = ?", (asset_id,))

    def existing_ids(self, asset_ids: list[str]) -> set[str]:
        """Ids the worker should not pick: analyzed, or failed MAX_ATTEMPTS times."""
        if not asset_ids:
            return set()
        placeholders = ",".join("?" for _ in asset_ids)
        with closing(self.connect()) as conn:
            rows = conn.execute(
                f"""
                SELECT asset_id FROM photo_insights WHERE asset_id IN ({placeholders})
                UNION
                SELECT asset_id FROM photo_failures WHERE asset_id IN ({placeholders}) AND attempts >= ?
                """,
                [*asset_ids, *asset_ids, MAX_ATTEMPTS],
            ).fetchall()
        return {r["asset_id"] for r in rows}

    def record_failure(self, asset_id: str, error: str) -> int:
        """Counts a failed analysis of one asset; returns its attempts so far."""
        with closing(self.connect()) as conn, conn:
            conn.execute(
                """
                INSERT INTO photo_failures (asset_id, attempts, last_error, failed_at) VALUES (?, 1, ?, ?)
                ON CONFLICT(asset_id) DO UPDATE SET
                    attempts = attempts + 1, last_error = excluded.last_error, failed_at = excluded.failed_at
                """,
                (asset_id, error[:500], self.clock()),
            )
            row = conn.execute("SELECT attempts FROM photo_failures WHERE asset_id = ?", (asset_id,)).fetchone()
        return row["attempts"]

    def scan_page(self) -> int:
        """Library page (newest first, 100 per page) the worker's deep scan resumes from."""
        with closing(self.connect()) as conn:
            row = conn.execute("SELECT value FROM worker_state WHERE key = 'scan_page'").fetchone()
        try:
            return max(1, int(row["value"])) if row else 1
        except ValueError:
            return 1

    def set_scan_page(self, page: int):
        with closing(self.connect()) as conn, conn:
            conn.execute(
                "INSERT INTO worker_state (key, value) VALUES ('scan_page', ?) "
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                (str(page),),
            )

    def list_cleanup(self, kind: str = "all", limit: int = 100) -> list[dict]:
        limit = max(1, min(500, int(limit)))
        where = "cleanup_candidate = 1 AND dismissed = 0"
        if kind == "low_quality":
            where += " AND (quality <= 2 OR category = 'accidental' OR issues != '[]')"
        elif kind == "documents":
            where += " AND category IN ('screenshot', 'document', 'receipt', 'meme')"
        with closing(self.connect()) as conn:
            rows = conn.execute(
                f"""
                SELECT asset_id, scene, category, is_nature, quality, issues,
                       cleanup_reason, analyzed_at
                FROM photo_insights
                WHERE {where}
                ORDER BY quality ASC, analyzed_at DESC
                LIMIT ?
                """,
                (limit,),
            ).fetchall()
        return [
            {
                "asset_id": r["asset_id"],
                "scene": r["scene"],
                "category": r["category"],
                "is_nature": bool(r["is_nature"]),
                "quality": r["quality"],
                "issues": json.loads(r["issues"] or "[]"),
                "cleanup_reason": r["cleanup_reason"],
                "analyzed_at": r["analyzed_at"],
            }
            for r in rows
        ]

    def dismiss(self, asset_id: str) -> dict | None:
        with closing(self.connect()) as conn, conn:
            row = conn.execute(
                "SELECT asset_id, tags_applied FROM photo_insights WHERE asset_id = ?",
                (asset_id,),
            ).fetchone()
            if not row:
                return None
            conn.execute("UPDATE photo_insights SET dismissed = 1 WHERE asset_id = ?", (asset_id,))
        return {"asset_id": row["asset_id"], "tags_applied": json.loads(row["tags_applied"] or "[]")}

    def delete(self, asset_id: str) -> bool:
        with closing(self.connect()) as conn, conn:
            cur = conn.execute("DELETE FROM photo_insights WHERE asset_id = ?", (asset_id,))
            return cur.rowcount > 0

    def stats(self) -> dict:
        with closing(self.connect()) as conn:
            row = conn.execute(
                """
                SELECT COUNT(*) AS analyzed,
                       SUM(CASE WHEN cleanup_candidate = 1 AND dismissed = 0 THEN 1 ELSE 0 END) AS cleanup
                FROM photo_insights
                """
            ).fetchone()
        return {"analyzed_count": row["analyzed"] or 0, "cleanup_count": row["cleanup"] or 0}

    def slideshow_excluded_ids(self) -> set[str]:
        """Asset ids the frame slideshow skips when filter_photos is on.

        Non-photos (screenshots, documents...) stay out even when kept; a photo the family kept
        ("Keep" in Clean up) comes back despite a low score.
        """
        with closing(self.connect()) as conn:
            rows = conn.execute(
                """
                SELECT asset_id FROM photo_insights
                WHERE category != 'photo' OR is_sensitive = 1
                   OR (dismissed = 0 AND (cleanup_candidate = 1 OR quality <= 2))
                """
            ).fetchall()
        return {r["asset_id"] for r in rows}

    def dashboard_stats(self) -> dict:
        """Counts for the server dashboard (T-095); no scenes or ids."""
        with closing(self.connect()) as conn:
            row = conn.execute(
                """
                SELECT COUNT(*) AS analyzed,
                       SUM(CASE WHEN cleanup_candidate = 1 AND dismissed = 0 THEN 1 ELSE 0 END) AS cleanup,
                       SUM(CASE WHEN dismissed = 1 THEN 1 ELSE 0 END) AS kept,
                       SUM(CASE WHEN frame_worthy = 1 AND is_sensitive = 0 AND category = 'photo'
                                 AND quality >= ? AND dismissed = 0 THEN 1 ELSE 0 END) AS frame_worthy,
                       SUM(is_sensitive) AS sensitive,
                       MAX(analyzed_at) AS last_analyzed_at
                FROM photo_insights
                """,
                (BEST_MIN_QUALITY,),
            ).fetchone()
            categories = {
                r["category"]: r["n"]
                for r in conn.execute(
                    "SELECT category, COUNT(*) AS n FROM photo_insights GROUP BY category ORDER BY n DESC"
                )
            }
            failures = conn.execute(
                """
                SELECT SUM(CASE WHEN attempts >= ? THEN 1 ELSE 0 END) AS skipped,
                       SUM(CASE WHEN attempts < ? THEN 1 ELSE 0 END) AS retrying
                FROM photo_failures
                """,
                (MAX_ATTEMPTS, MAX_ATTEMPTS),
            ).fetchone()
        return {
            "analyzed": row["analyzed"] or 0,
            "cleanup": row["cleanup"] or 0,
            "kept": row["kept"] or 0,
            "frame_worthy": row["frame_worthy"] or 0,
            "sensitive": row["sensitive"] or 0,
            "last_analyzed_at": row["last_analyzed_at"],
            "categories": categories,
            "failed_skipped": failures["skipped"] or 0,
            "failed_retrying": failures["retrying"] or 0,
            "scan_page": self.scan_page(),
        }

    def labels(self) -> list[tuple[str, str, bool]]:
        """(asset_id, category, is_sensitive) for every analyzed asset (T-102, T-099)."""
        with closing(self.connect()) as conn:
            rows = conn.execute("SELECT asset_id, category, is_sensitive FROM photo_insights").fetchall()
        return [(r["asset_id"], r["category"], bool(r["is_sensitive"])) for r in rows]

    def analyzed_photo_ids(self) -> set[str]:
        """Assets the AI saw as real photos: they pass the frame's screenshot/document guesses (T-099)."""
        with closing(self.connect()) as conn:
            rows = conn.execute(
                "SELECT asset_id FROM photo_insights WHERE category IN (?, ?, ?)", AI_PHOTO_CATEGORIES
            ).fetchall()
        return {r["asset_id"] for r in rows}

    def stale_best_tags(self) -> list[tuple[str, list[str]]]:
        """(asset_id, tags_applied) of assets tagged "Best" in Immich that no longer qualify (T-103)."""
        with closing(self.connect()) as conn:
            rows = conn.execute(
                """
                SELECT asset_id, tags_applied FROM photo_insights
                WHERE tags_applied LIKE ?
                  AND NOT (frame_worthy = 1 AND is_sensitive = 0 AND category = 'photo' AND quality >= ?)
                """,
                (f'%"{FRAME_WORTHY_TAG}"%', BEST_MIN_QUALITY),
            ).fetchall()
        return [(r["asset_id"], json.loads(r["tags_applied"] or "[]")) for r in rows]

    def set_tags_applied(self, asset_id: str, tags: list[str]):
        with closing(self.connect()) as conn, conn:
            conn.execute("UPDATE photo_insights SET tags_applied = ? WHERE asset_id = ?", (json.dumps(tags), asset_id))

    def sensitive_ids(self) -> set[str]:
        """Assets with private data or intimate moments: never shown on the frame, in any mode."""
        with closing(self.connect()) as conn:
            rows = conn.execute("SELECT asset_id FROM photo_insights WHERE is_sensitive = 1").fetchall()
        return {r["asset_id"] for r in rows}

    def frame_worthy_ids(self) -> list[str]:
        """AI-picked sharp, pleasant, non-sensitive photos suitable for a wall frame (T-090)."""
        with closing(self.connect()) as conn:
            rows = conn.execute(
                """
                SELECT asset_id FROM photo_insights
                WHERE frame_worthy = 1 AND is_sensitive = 0 AND category = 'photo' AND quality >= ? AND dismissed = 0
                """,
                (BEST_MIN_QUALITY,),
            ).fetchall()
        return [r["asset_id"] for r in rows]

    def known_album_keys(self) -> set[str]:
        with closing(self.connect()) as conn:
            rows = conn.execute("SELECT cluster_key FROM album_suggestions").fetchall()
        return {r["cluster_key"] for r in rows}

    def save_album_suggestion(self, cluster: dict, title: str, cover_asset_id: str):
        with closing(self.connect()) as conn, conn:
            conn.execute(
                """
                INSERT OR IGNORE INTO album_suggestions (
                    cluster_key, title, subtitle, cover_asset_id, asset_ids,
                    start_date, end_date, city, country, status, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?)
                """,
                (
                    cluster["cluster_key"],
                    title.strip()[:80] or cluster["fallback_title"],
                    cluster["subtitle"],
                    cover_asset_id,
                    json.dumps(cluster["asset_ids"]),
                    cluster["start_date"],
                    cluster["end_date"],
                    cluster.get("city", ""),
                    cluster.get("country", ""),
                    self.clock(),
                ),
            )

    def list_album_suggestions(self, limit: int = 20) -> list[dict]:
        limit = max(1, min(100, int(limit)))
        with closing(self.connect()) as conn:
            rows = conn.execute(
                """
                SELECT cluster_key, title, subtitle, cover_asset_id, asset_ids, start_date, end_date
                FROM album_suggestions
                WHERE status = 'pending'
                ORDER BY end_date DESC
                LIMIT ?
                """,
                (limit,),
            ).fetchall()
        return [
            {
                "cluster_key": r["cluster_key"],
                "title": r["title"],
                "subtitle": r["subtitle"],
                "cover_asset_id": r["cover_asset_id"],
                "asset_ids": json.loads(r["asset_ids"] or "[]"),
                "start_date": r["start_date"],
                "end_date": r["end_date"],
            }
            for r in rows
        ]

    def set_album_suggestion_status(self, cluster_key: str, status: str) -> bool:
        with closing(self.connect()) as conn, conn:
            cur = conn.execute(
                "UPDATE album_suggestions SET status = ? WHERE cluster_key = ?",
                (status, cluster_key),
            )
            return cur.rowcount > 0

    def scenes_for(self, asset_ids: list[str], limit: int = 10) -> list[str]:
        if not asset_ids:
            return []
        placeholders = ",".join("?" for _ in asset_ids)
        with closing(self.connect()) as conn:
            rows = conn.execute(
                f"SELECT scene FROM photo_insights WHERE asset_id IN ({placeholders}) AND scene != '' LIMIT ?",
                [*asset_ids, limit],
            ).fetchall()
        return [r["scene"] for r in rows]

    def best_cover(self, asset_ids: list[str]) -> str | None:
        if not asset_ids:
            return None
        placeholders = ",".join("?" for _ in asset_ids)
        with closing(self.connect()) as conn:
            row = conn.execute(
                f"""
                SELECT asset_id FROM photo_insights
                WHERE asset_id IN ({placeholders}) AND is_sensitive = 0 AND category = 'photo'
                ORDER BY frame_worthy DESC, quality DESC
                LIMIT 1
                """,
                asset_ids,
            ).fetchone()
        return row["asset_id"] if row else asset_ids[len(asset_ids) // 2]

    def last_album_scan_date(self) -> str | None:
        with closing(self.connect()) as conn:
            row = conn.execute("SELECT value FROM worker_state WHERE key = 'album_scan_date'").fetchone()
        return row["value"] if row else None

    def set_last_album_scan_date(self, day: str):
        with closing(self.connect()) as conn, conn:
            conn.execute(
                "INSERT INTO worker_state (key, value) VALUES ('album_scan_date', ?) "
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                (day,),
            )


class OllamaAnalyzer:
    """Calls local Ollama /api/chat with a vision model and strict JSON schema."""

    def __init__(self, base_url: str, model: str, timeout: float = 180.0):
        self.base_url = base_url.rstrip("/")
        self.model = model
        self.timeout = timeout

    def analyze(self, image_bytes: bytes) -> dict:
        img_b64 = base64.b64encode(image_bytes).decode("ascii")
        r = requests.post(
            f"{self.base_url}/api/chat",
            json={
                "model": self.model,
                "stream": False,
                "think": False,
                "format": PHOTO_AI_SCHEMA,
                "messages": [{"role": "user", "content": PHOTO_AI_PROMPT, "images": [img_b64]}],
            },
            timeout=self.timeout,
        )
        r.raise_for_status()
        content = r.json()["message"]["content"]
        return json.loads(content)

    def name_album(self, cluster: dict, scenes: list[str]) -> tuple[str, bool]:
        """Suggest a short, natural album title for a photo cluster (text-only, T-098)."""
        location = ", ".join(p for p in (cluster.get("city"), cluster.get("country")) if p) or "unknown location"
        scenes_text = "; ".join(scenes[:8]) if scenes else "no scene captions yet"
        prompt = (
            "Suggest a short, warm photo album title (2-5 words) for this cluster of family photos. "
            f"Location: {location}. Dates: {cluster['start_date']} to {cluster['end_date']}. "
            f"Photo count: {len(cluster['asset_ids'])}. Sample scenes: {scenes_text}. "
            "Set worth_album=true for trips, outings, holidays, birthdays or events; "
            "set worth_album=false only if it clearly looks like random mundane clutter."
        )
        r = requests.post(
            f"{self.base_url}/api/chat",
            json={
                "model": self.model,
                "stream": False,
                "think": False,
                "format": ALBUM_NAME_SCHEMA,
                "messages": [{"role": "user", "content": prompt}],
            },
            timeout=60.0,
        )
        r.raise_for_status()
        parsed = json.loads(r.json()["message"]["content"])
        return str(parsed.get("title") or "").strip(), bool(parsed.get("worth_album", True))


class ImmichTagger:
    """Mirrors `ai/*` tags to Immich; degrades gracefully if API key lacks tag.* permissions."""

    def __init__(self, immich, enabled: bool = True):
        self.immich = immich
        self.enabled = enabled
        self._tag_ids: dict[str, str] | None = None
        self._disabled_reason: str | None = None

    def _ensure_tags(self) -> dict[str, str] | None:
        if not self.enabled or self._disabled_reason:
            return None
        if self._tag_ids is not None:
            return self._tag_ids
        try:
            self._tag_ids = self.immich.upsert_tags(ALL_AI_TAGS)
            return self._tag_ids
        except requests.HTTPError as e:
            if e.response is not None and e.response.status_code in (401, 403):
                self._disabled_reason = "missing tag permissions"
                log.warning(
                    "Immich tagging disabled: IMMICH_API_KEY lacks tag.read, tag.create or tag.asset. "
                    "Insights are still saved locally in photo_ai.db."
                )
            else:
                log.warning("Immich tag setup failed: %s", e)
            return None
        except requests.RequestException as e:
            log.warning("Immich tag setup failed: %s", e)
            return None

    def _call_with_fresh_ids(self, action):
        """Runs `action(tag_map)`; if Immich rejects a cached tag id (someone deleted the tag,
        e.g. in Settings -> Timeline), re-creates the tags once and retries (T-093)."""
        tag_map = self._ensure_tags()
        if not tag_map:
            return None
        try:
            return action(tag_map)
        except requests.HTTPError as e:
            if e.response is None or e.response.status_code not in (400, 404):
                raise
            log.info("Immich rejected a cached ai/* tag id (%s): re-creating tags", e.response.status_code)
            self._tag_ids = None
            tag_map = self._ensure_tags()
            if not tag_map:
                return None
            return action(tag_map)

    def apply(self, asset_id: str, tag_names: list[str]) -> list[str]:
        if not tag_names:
            return []

        def tag(tag_map: dict[str, str]) -> list[str]:
            known = [name for name in tag_names if name in tag_map]
            if known:
                self.immich.tag_assets([tag_map[name] for name in known], [asset_id])
            return known

        try:
            return self._call_with_fresh_ids(tag) or []
        except requests.RequestException as e:
            log.warning("Immich tagging failed for %s: %s", asset_id, e)
            return []

    def remove_tag(self, asset_id: str, name: str) -> bool:
        """Takes one `ai/*` tag off an asset in Immich; True when done (or the tag doesn't exist)."""

        def untag(tag_map: dict[str, str]):
            if name in tag_map:
                self.immich.untag_asset(tag_map[name], asset_id)
            return True

        try:
            return bool(self._call_with_fresh_ids(untag))
        except requests.RequestException as e:
            log.warning("Immich untag %s failed for %s: %s", name, asset_id, e)
            return False

    def remove_cleanup_tags(self, asset_id: str, applied: list[str] | None = None) -> None:
        targets = [t for t in (applied or CLEANUP_TAGS) if t in CLEANUP_TAGS]
        for name in targets:
            def untag(tag_map: dict[str, str], name=name):
                if name in tag_map:
                    self.immich.untag_asset(tag_map[name], asset_id)

            try:
                self._call_with_fresh_ids(untag)
            except requests.RequestException as e:
                log.warning("Immich untag %s failed for %s: %s", name, asset_id, e)


@dataclass
class PhotoAiConfig:
    ollama_url: str = ""
    ollama_model: str = ""
    batch_size: int = 10
    interval_minutes: int = 30
    active_hours: tuple[int, int] | None = None
    tag_immich: bool = True

    @property
    def enabled(self) -> bool:
        return bool(self.ollama_url and self.ollama_model)

    @staticmethod
    def from_env() -> "PhotoAiConfig":
        return PhotoAiConfig(
            ollama_url=os.environ.get("OLLAMA_URL", "").strip(),
            ollama_model=os.environ.get("OLLAMA_MODEL", "").strip(),
            batch_size=_env_int("OLLAMA_BATCH_SIZE", 10),
            interval_minutes=_env_int("OLLAMA_INTERVAL_MINUTES", 30),
            active_hours=parse_active_hours(os.environ.get("OLLAMA_ACTIVE_HOURS", "")),
            tag_immich=os.environ.get("OLLAMA_TAG_IMMICH", "true").strip().lower() not in ("0", "false", "no"),
        )


class PhotoAiWorker:
    """Periodically analyzes unanalyzed Immich images via Ollama into PhotoAiStore."""

    def __init__(self, config: PhotoAiConfig, store: PhotoAiStore, immich, analyzer: OllamaAnalyzer,
                 tagger: ImmichTagger, now=datetime.now):
        self.config = config
        self.store = store
        self.immich = immich
        self.analyzer = analyzer
        self.tagger = tagger
        self.now = now
        self.running = False
        self.last_error: str | None = None
        self._stop = threading.Event()

    def run_batch(self) -> int:
        """Analyze up to config.batch_size new images. Returns count analyzed.

        A failure tied to one photo (preview missing, invalid model JSON) is recorded and the
        batch moves on; after MAX_ATTEMPTS the photo is skipped. Ollama or Immich being
        unreachable stops the batch without blaming the photo (T-093).
        """
        if not self.config.enabled:
            return 0
        if not in_active_hours(self.config.active_hours, self.now()):
            return 0
        self.running = True
        analyzed = 0
        errors: list[str] = []
        try:
            todo = self._pick_unanalyzed(self.config.batch_size)
            for asset_id in todo:
                if self._stop.is_set():
                    break
                try:
                    preview = self.immich.preview_bytes(asset_id)
                    insight = self.analyzer.analyze(preview)
                    tags = tags_for_insight(insight)
                except (requests.ConnectionError, requests.Timeout):
                    raise  # the service is down, not this photo's fault
                except Exception as e:
                    attempts = self.store.record_failure(asset_id, str(e))
                    log.warning("Photo AI analysis of %s failed (attempt %s/%s): %s",
                                asset_id, attempts, MAX_ATTEMPTS, e)
                    errors.append(f"{asset_id}: {e}")
                    continue
                applied = self.tagger.apply(asset_id, tags)
                self.store.save(asset_id, self.config.ollama_model, insight, applied)
                analyzed += 1
            today = self.now().date().isoformat()
            if self.store.last_album_scan_date() != today:
                self.refresh_album_suggestions()
                self.store.set_last_album_scan_date(today)
            self.last_error = errors[-1] if errors else None
        except Exception as e:
            log.warning("Photo AI batch failed: %s", e)
            self.last_error = str(e)
        finally:
            self.running = False
        return analyzed

    def refresh_album_suggestions(self, max_pages: int = 5) -> int:
        """Cluster unassigned Immich photos by location/date and name new clusters via Ollama (T-098)."""
        if not hasattr(self.immich, "unassigned_images"):
            return 0
        items: list[dict] = []
        for page in range(1, max_pages + 1):
            batch, has_next = self.immich.unassigned_images(page=page, size=100)
            items.extend(batch)
            if not has_next or not batch:
                break
        clusters = cluster_album_candidates(items)
        known = self.store.known_album_keys()
        added = 0
        for cluster in clusters:
            if cluster["cluster_key"] in known:
                continue
            scenes = self.store.scenes_for(cluster["asset_ids"])
            title, worth = cluster["fallback_title"], True
            if hasattr(self.analyzer, "name_album"):
                try:
                    suggested, worth = self.analyzer.name_album(cluster, scenes)
                    if suggested:
                        title = suggested
                except Exception as e:
                    log.warning("Album naming failed for %s: %s", cluster["cluster_key"], e)
            if not worth:
                continue
            cover = self.store.best_cover(cluster["asset_ids"]) or cluster["asset_ids"][0]
            self.store.save_album_suggestion(cluster, title, cover)
            added += 1
        return added

    def _pick_unanalyzed(self, limit: int) -> list[str]:
        """Newest page first (fresh uploads), then a deep scan resumed from the stored page, so
        libraries larger than MAX_SCAN_PAGES pages are covered over several batches (T-093)."""
        chosen: list[str] = []

        def take(page: int) -> bool:
            items, has_next = self.immich.recent_images(page=page, size=100)
            ids = [a["id"] for a in items if "id" in a]
            seen = self.store.existing_ids(ids)
            for aid in ids:
                if len(chosen) >= limit:
                    break
                if aid not in seen and aid not in chosen:
                    chosen.append(aid)
            return has_next and bool(items)

        has_next = take(1)
        page = max(2, self.store.scan_page())
        scanned = 1
        while has_next and len(chosen) < limit and scanned < MAX_SCAN_PAGES:
            has_next = take(page)
            scanned += 1
            if len(chosen) >= limit:
                break  # resume on this page: it may hold more unanalyzed photos
            page += 1
        # At the end of the library the next deep scan starts over from page 2.
        self.store.set_scan_page(page if has_next else 2)
        return chosen

    def reconcile_best_tags(self) -> int:
        """Takes "Best" off photos tagged under an older, looser rule (T-103). Returns how many."""
        fixed = 0
        for asset_id, applied in self.store.stale_best_tags():
            if self.tagger.remove_tag(asset_id, FRAME_WORTHY_TAG):
                self.store.set_tags_applied(asset_id, [t for t in applied if t != FRAME_WORTHY_TAG])
                fixed += 1
        if fixed:
            log.info("Photo AI: took ai/best/frame-worthy off %s photos that no longer qualify", fixed)
        return fixed

    def start(self):
        if not self.config.enabled:
            return
        log.info(
            "Photo AI worker enabled: model=%s batch=%s every %s min (Immich tagging=%s)",
            self.config.ollama_model, self.config.batch_size,
            self.config.interval_minutes, self.config.tag_immich,
        )

        def loop():
            try:
                self.reconcile_best_tags()
            except Exception as e:
                log.warning("Photo AI best-tag cleanup failed: %s", e)
            while not self._stop.is_set():
                self.run_batch()
                self._stop.wait(timeout=self.config.interval_minutes * 60)

        threading.Thread(target=loop, daemon=True, name="photo-ai").start()
