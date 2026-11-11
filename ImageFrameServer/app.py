"""HTTP API used by the Picfort app to control the frame.

    GET  /status                -> {"status": "ok", "busy": bool, "currentAsset": …, "slideshow": {…}}
    GET  /run?assetId=<uuid>    -> 202 {"status": "accepted"} once the photo is fetched and rendered;
                                   the (slow) e-paper refresh continues in the background.
    GET  /current               -> PNG preview of the last rendered frame
    GET  /slideshow             -> slideshow settings + next change time
    PUT  /slideshow             -> update settings (JSON body, same shape as GET)
    POST /next                  -> show the next slideshow photo now
    GET  /albums                -> [{"id", "name"}] for choosing a slideshow album

Family Wall (see wall.py). Every call needs `Authorization: Bearer <Immich token>` (auth.py);
the author is the Immich user of that token.
    GET    /wall?limit=20       -> posts, pinned first, then newest first
    POST   /wall                -> 201 new post (multipart: text?, photo?)
    DELETE /wall/<id>           -> 204 (own posts only)
    PUT    /wall/<id>/pin       -> the post with pinned toggled (own posts only)
    GET    /wall/photo/<file>   -> the photo attached to a post
"""

import logging
import math
import os
import re
import sys
import threading
import time

from flask import Flask, g, jsonify, request, send_file
from werkzeug.exceptions import RequestEntityTooLarge

import frame
from auth import Authenticator, AuthError, AuthUnavailable, immich_lookup
from immich import ImmichClient
from slideshow import INTERVALS, MODES, Picker, SettingsStore, Slideshow, SlideshowSettings
from wall import DEFAULT_LIMIT, MAX_LIMIT, MAX_PHOTO_BYTES, WallError, WallStore

PHOTO_MIME_TYPES = {"jpg": "image/jpeg", "png": "image/png", "heic": "image/heic"}
UUID_RE = re.compile(r"^[0-9a-fA-F-]{36}$")


class Rejected(Exception):
    def __init__(self, message, status, **extra):
        super().__init__(message)
        self.status = status
        self.extra = extra


class FrameController:
    """Owns the panel: one refresh at a time, and not more often than min_interval."""

    def __init__(self, config, min_interval, clock=time.monotonic, display=None, prepare=None):
        self.config = config
        self.min_interval = min_interval
        self.clock = clock
        self._display = display or (lambda img: frame.display(config, img))
        self._prepare = prepare or (lambda asset_id: frame.prepare(config, asset_id))
        self.lock = threading.Lock()
        self.busy = False
        self.last_run = -float("inf")
        self.current_asset = None
        self.last_error = None
        self.on_shown = lambda: None  # set by the slideshow

    def show(self, asset_id: str):
        """Fetch + render now (raises Rejected / frame.FrameError), refresh the panel in the background."""
        with self.lock:
            if self.busy:
                raise Rejected("The frame is still refreshing", 409)
            wait = self.min_interval - (self.clock() - self.last_run)
            if wait > 0:
                raise Rejected("Too soon, please wait", 429, retry_after=math.ceil(wait))
            self.busy = True
        try:
            img = self._prepare(asset_id)
        except Exception:
            with self.lock:
                self.busy = False
            raise
        with self.lock:
            self.last_run = self.clock()
        threading.Thread(target=self._refresh, args=(img, asset_id), daemon=True).start()
        self.on_shown()

    def _refresh(self, img, asset_id):
        try:
            self._display(img)
            with self.lock:
                self.current_asset, self.last_error = asset_id, None
            log.info("Displayed %s", asset_id)
        except Exception as e:
            log.exception("Display failed")
            with self.lock:
                self.last_error = str(e)
        finally:
            with self.lock:
                self.busy = False

    def try_show(self, asset_id: str) -> bool:
        """For the slideshow: True if accepted, False if busy/too soon (errors propagate)."""
        try:
            self.show(asset_id)
            return True
        except Rejected:
            return False


def error(message, status, **extra):
    resp = jsonify(status="error", message=message, **extra)
    if "retry_after" in extra:
        resp.headers["Retry-After"] = str(extra["retry_after"])
    return resp, status


def settings_json(slideshow: Slideshow):
    s = slideshow.settings
    return {
        "mode": s.mode,
        "album_id": s.album_id,
        "interval_minutes": s.interval_minutes,
        "quiet_start": s.quiet_start,
        "quiet_end": s.quiet_end,
        "next_change_at": slideshow.next_change_at(),
        "last_error": slideshow.last_error,
        "modes": list(MODES),
        "intervals": list(INTERVALS),
    }


def create_app(controller: FrameController, slideshow: Slideshow, immich: ImmichClient,
               wall: WallStore = None, auth: Authenticator = None) -> Flask:
    app = Flask(__name__)
    # The photo plus a little room for the text fields; larger requests get 413.
    app.config["MAX_CONTENT_LENGTH"] = MAX_PHOTO_BYTES + 64 * 1024
    controller.on_shown = slideshow.photo_changed

    @app.route("/run")
    def run():
        asset_id = request.args.get("assetId", "").strip()
        if not UUID_RE.match(asset_id):
            return error("assetId must be an Immich asset id", 400)
        try:
            controller.show(asset_id)
        except Rejected as e:
            return error(str(e), e.status, **e.extra)
        except frame.FrameError as e:
            log.warning("Rejected %s: %s", asset_id, e)
            return error(str(e), e.status)
        except Exception as e:
            log.exception("Rendering failed")
            return error(f"Rendering failed: {e}", 500)
        return jsonify(status="accepted", assetId=asset_id), 202

    @app.route("/status")
    def status():
        with controller.lock:
            body = dict(
                status="ok",
                busy=controller.busy,
                currentAsset=controller.current_asset,
                lastError=controller.last_error,
                width=controller.config.width,
                height=controller.config.height,
                hardware=bool(controller.config.driver),
            )
        body["slideshow"] = settings_json(slideshow)
        return jsonify(body)

    @app.route("/current")
    def current():
        path = controller.config.preview_path
        if not os.path.exists(path):
            return error("Nothing has been displayed yet", 404)
        return send_file(os.path.abspath(path), mimetype="image/png", max_age=0)

    @app.route("/slideshow", methods=["GET"])
    def get_slideshow():
        return jsonify(settings_json(slideshow))

    @app.route("/slideshow", methods=["PUT"])
    def put_slideshow():
        body = request.get_json(silent=True)
        if not isinstance(body, dict):
            return error("Expected a JSON object", 400)
        try:
            new = SlideshowSettings.from_dict({**settings_json(slideshow), **body})
        except (ValueError, TypeError) as e:
            return error(str(e), 400)
        slideshow.update(new)
        return jsonify(settings_json(slideshow))

    @app.route("/next", methods=["POST"])
    def next_photo():
        if slideshow.settings.mode == "off":
            return error("The slideshow is off", 409)
        slideshow.skip()
        return jsonify(status="accepted"), 202

    @app.route("/albums")
    def albums():
        try:
            return jsonify(sorted(immich.albums(), key=lambda a: a["name"].lower()))
        except Exception as e:
            return error(f"Could not load albums from Immich: {e}", 502)

    if wall is not None:
        register_wall(app, wall, auth)
    return app


def register_wall(app: Flask, wall: WallStore, auth: Authenticator):
    @app.errorhandler(RequestEntityTooLarge)
    def too_large(_):
        return error("The photo is larger than 10 MB", 413)

    @app.before_request
    def authenticate():
        if not request.path.startswith("/wall"):
            return None
        try:
            g.user = auth.user(request.headers.get("Authorization"))
        except AuthError as e:
            resp, status = error(str(e), 401)
            resp.headers["WWW-Authenticate"] = "Bearer"
            return resp, status
        except AuthUnavailable as e:
            log.warning("Wall auth: %s", e)
            return error(str(e), 502)
        return None

    def own_post(post_id):
        """(post, None) if it exists and belongs to the caller, else (None, error response)."""
        post = wall.get(post_id)
        if post is None:
            return None, error("Post not found", 404)
        if post["author_id"] != g.user.id:
            return None, error("Only the author can change this post", 403)
        return post, None

    @app.route("/wall", methods=["GET"])
    def list_posts():
        try:
            limit = int(request.args.get("limit", DEFAULT_LIMIT))
        except ValueError:
            return error("limit must be a number", 400)
        if not 1 <= limit <= MAX_LIMIT:
            return error(f"limit must be between 1 and {MAX_LIMIT}", 400)
        return jsonify(wall.list(limit))

    @app.route("/wall", methods=["POST"])
    def create_post():
        upload = request.files.get("photo")
        photo = upload.read() if upload and upload.filename else None
        try:
            post = wall.create(g.user.id, g.user.name, request.form.get("text"), photo)
        except WallError as e:
            return error(str(e), e.status)
        log.info("Wall post %s by %s", post["id"], post["author"])
        return jsonify(post), 201

    @app.route("/wall/<int:post_id>", methods=["DELETE"])
    def delete_post(post_id):
        _, failure = own_post(post_id)
        if failure:
            return failure
        wall.delete(post_id)
        return "", 204

    @app.route("/wall/<int:post_id>/pin", methods=["PUT"])
    def pin_post(post_id):
        _, failure = own_post(post_id)
        if failure:
            return failure
        return jsonify(wall.toggle_pin(post_id))

    @app.route("/wall/photo/<filename>")
    def wall_photo(filename):
        path = wall.photo_path(filename)
        if path is None:
            return error("Photo not found", 404)
        # Names are random and never reused, so the file can be cached for good (privately: it needs a login).
        mimetype = PHOTO_MIME_TYPES[filename.rsplit(".", 1)[1]]  # Python 3.11 doesn't know .heic
        resp = send_file(os.path.abspath(path), mimetype=mimetype, max_age=365 * 24 * 3600)
        resp.cache_control.private = True
        resp.cache_control.public = False
        return resp


log = logging.getLogger("server")

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    config = frame.Config.from_env()
    if not config.immich_url or not config.immich_api_key:
        log.error("Set IMMICH_URL and IMMICH_API_KEY (in .env), then start the server again.")
        sys.exit(1)
    immich_client = ImmichClient(config.immich_url, config.immich_api_key)
    frame_controller = FrameController(config, float(os.environ.get("FRAME_MIN_INTERVAL", "30")))
    show_slideshow = Slideshow(
        SettingsStore(os.environ.get("FRAME_STATE_PATH", "slideshow.json")),
        Picker(immich_client),
        show=frame_controller.try_show,
    )
    show_slideshow.start()
    family_wall = WallStore(os.environ.get("WALL_DB_PATH", "wall.db"),
                            os.environ.get("WALL_PHOTOS_PATH", "wall_photos"))
    wall_auth = Authenticator(immich_lookup(config.immich_url))
    create_app(frame_controller, show_slideshow, immich_client, family_wall, wall_auth).run(
        host="0.0.0.0", port=int(os.environ.get("PORT", "5000")), threaded=True
    )
