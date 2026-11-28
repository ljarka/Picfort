"""Optional Ollama Vision-LLM photo insights, Immich ai/* tagging, and cleanup store (ADR 0019, T-089)."""

import base64
import json
import logging
import os
import threading
import time
from contextlib import closing
from dataclasses import dataclass
from datetime import datetime

import requests

from store import SqliteStore, utc_now

log = logging.getLogger("photo_ai")

CATEGORIES = ("photo", "screenshot", "document", "receipt", "meme", "accidental", "kids_art")
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
FRAME_WORTHY_TAG = "ai/best/frame-worthy"
ALL_AI_TAGS = list(CLEANUP_TAGS) + list(TYPE_TAG_MAP.values()) + [NATURE_TAG, FRAME_WORTHY_TAG]

# `scene` is FIRST so the vision model describes the image before committing to category/quality.
PHOTO_AI_SCHEMA = {
    "type": "object",
    "properties": {
        "scene": {"type": "string"},
        "category": {"type": "string", "enum": list(CATEGORIES)},
        "is_nature": {"type": "boolean"},
        "quality": {"type": "integer", "minimum": 1, "maximum": 5},
        "issues": {"type": "array", "items": {"type": "string", "enum": list(ISSUES)}},
        "frame_worthy": {"type": "boolean"},
        "cleanup_candidate": {"type": "boolean"},
        "cleanup_reason": {"type": "string"},
    },
    "required": [
        "scene", "category", "is_nature", "quality", "issues",
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
    "4) quality: 1=unusable, 2=poor/blurry, 3=ordinary, 4=good, 5=great sharp photo. "
    "5) issues: technical flaws present (empty array if none). "
    "6) frame_worthy: true only for pleasant, sharp photos suitable for a wall picture frame. "
    "7) cleanup_candidate: true for blurry, accidental, unusable shots, screenshots, receipts or temporary documents "
    "(never mark kids_art as cleanup_candidate). "
    "8) cleanup_reason: empty string if cleanup_candidate is false, else short reason (<=6 words)."
)


def tags_for_insight(insight: dict) -> list[str]:
    """Hierarchical Immich `ai/*` tags derived from a structured insight. Ordinary photos get []."""
    tags: list[str] = []
    category = insight.get("category", "photo")
    quality = int(insight.get("quality", 3))
    issues = set(insight.get("issues") or ())
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
    if insight.get("frame_worthy") and category == "photo" and quality >= 4:
        tags.append(FRAME_WORTHY_TAG)
    return tags


def parse_active_hours(spec: str) -> tuple[int, int] | None:
    """Parse '23-7' into (23, 7), or None if empty."""
    spec = (spec or "").strip()
    if not spec:
        return None
    parts = spec.split("-", 1)
    if len(parts) != 2:
        return None
    start, end = int(parts[0]), int(parts[1])
    if not (0 <= start <= 23 and 0 <= end <= 23):
        return None
    return start, end


def in_active_hours(window: tuple[int, int] | None, now: datetime) -> bool:
    if window is None or window[0] == window[1]:
        return True
    start, end = window
    h = now.hour
    if start < end:
        return start <= h < end
    return h >= start or h < end  # wraps midnight, e.g. 23 -> 7


class PhotoAiStore(SqliteStore):
    SCHEMA_VERSION = 1
    NAME = "photo_ai"
    SCHEMA = """
    CREATE TABLE photo_insights (
        asset_id          TEXT PRIMARY KEY,
        model             TEXT NOT NULL,
        scene             TEXT NOT NULL DEFAULT '',
        category          TEXT NOT NULL DEFAULT 'photo',
        is_nature         INTEGER NOT NULL DEFAULT 0,
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
    """

    def save(self, asset_id: str, model: str, insight: dict, tags_applied: list[str] | None = None):
        now = self.clock()
        issues = [i for i in (insight.get("issues") or []) if i in ISSUES]
        category = insight.get("category", "photo")
        if category not in CATEGORIES:
            category = "photo"
        quality = max(1, min(5, int(insight.get("quality", 3))))
        cleanup = bool(insight.get("cleanup_candidate")) and category != "kids_art"
        with closing(self.connect()) as conn, conn:
            conn.execute(
                """
                INSERT INTO photo_insights (
                    asset_id, model, scene, category, is_nature, quality, issues,
                    frame_worthy, cleanup_candidate, cleanup_reason, dismissed, tags_applied, analyzed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                ON CONFLICT(asset_id) DO UPDATE SET
                    model = excluded.model,
                    scene = excluded.scene,
                    category = excluded.category,
                    is_nature = excluded.is_nature,
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
                    quality,
                    json.dumps(issues),
                    1 if insight.get("frame_worthy") else 0,
                    1 if cleanup else 0,
                    str(insight.get("cleanup_reason") or "")[:120] if cleanup else "",
                    json.dumps(list(tags_applied or ())),
                    now,
                ),
            )

    def existing_ids(self, asset_ids: list[str]) -> set[str]:
        if not asset_ids:
            return set()
        placeholders = ",".join("?" for _ in asset_ids)
        with closing(self.connect()) as conn:
            rows = conn.execute(
                f"SELECT asset_id FROM photo_insights WHERE asset_id IN ({placeholders})",
                asset_ids,
            ).fetchall()
        return {r["asset_id"] for r in rows}

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
        """Asset ids the frame slideshow should skip when filter_photos is enabled."""
        with closing(self.connect()) as conn:
            rows = conn.execute(
                "SELECT asset_id FROM photo_insights WHERE category != 'photo' OR cleanup_candidate = 1 OR quality <= 2"
            ).fetchall()
        return {r["asset_id"] for r in rows}


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

    def apply(self, asset_id: str, tag_names: list[str]) -> list[str]:
        if not tag_names:
            return []
        tag_map = self._ensure_tags()
        if not tag_map:
            return []
        ids = [tag_map[name] for name in tag_names if name in tag_map]
        if not ids:
            return []
        try:
            self.immich.tag_assets(ids, [asset_id])
            return [name for name in tag_names if name in tag_map]
        except requests.RequestException as e:
            log.warning("Immich tagging failed for %s: %s", asset_id, e)
            return []

    def remove_cleanup_tags(self, asset_id: str, applied: list[str] | None = None) -> None:
        tag_map = self._ensure_tags()
        if not tag_map:
            return
        targets = [t for t in (applied or CLEANUP_TAGS) if t in CLEANUP_TAGS and t in tag_map]
        for name in targets:
            try:
                self.immich.untag_asset(tag_map[name], asset_id)
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
            batch_size=max(1, int(os.environ.get("OLLAMA_BATCH_SIZE", "10"))),
            interval_minutes=max(1, int(os.environ.get("OLLAMA_INTERVAL_MINUTES", "30"))),
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
        """Analyze up to config.batch_size new images. Returns count analyzed."""
        if not self.config.enabled:
            return 0
        if not in_active_hours(self.config.active_hours, self.now()):
            return 0
        self.running = True
        analyzed = 0
        try:
            todo = self._pick_unanalyzed(self.config.batch_size)
            for asset_id in todo:
                if self._stop.is_set():
                    break
                preview = self.immich.preview_bytes(asset_id)
                insight = self.analyzer.analyze(preview)
                tags = tags_for_insight(insight)
                applied = self.tagger.apply(asset_id, tags)
                self.store.save(asset_id, self.config.ollama_model, insight, applied)
                analyzed += 1
            self.last_error = None
        except Exception as e:
            log.warning("Photo AI batch failed: %s", e)
            self.last_error = str(e)
        finally:
            self.running = False
        return analyzed

    def _pick_unanalyzed(self, limit: int) -> list[str]:
        chosen: list[str] = []
        page = 1
        while len(chosen) < limit and page <= 20:
            items, has_next = self.immich.recent_images(page=page, size=100)
            if not items:
                break
            ids = [a["id"] for a in items if "id" in a]
            seen = self.store.existing_ids(ids)
            for aid in ids:
                if aid not in seen:
                    chosen.append(aid)
                    if len(chosen) >= limit:
                        break
            if not has_next:
                break
            page += 1
        return chosen

    def start(self):
        if not self.config.enabled:
            return
        log.info(
            "Photo AI worker enabled: model=%s batch=%s every %s min (Immich tagging=%s)",
            self.config.ollama_model, self.config.batch_size,
            self.config.interval_minutes, self.config.tag_immich,
        )

        def loop():
            while not self._stop.is_set():
                self.run_batch()
                self._stop.wait(timeout=self.config.interval_minutes * 60)

        threading.Thread(target=loop, daemon=True, name="photo-ai").start()
