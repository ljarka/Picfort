"""The frame in a web browser (T-072): a tablet shows what the frame shows, in full colour.

    GET /frame              -> the page (frame_page.html). It has no secrets: it asks for the
                               Picfort key once and keeps it in that browser.
    GET /frame/state        -> {"version", "shown", "fit"}; the version changes with every photo
                               the frame shows, so the page polls this and fetches only on a change.
    GET /frame/photo?w=&h=  -> that photo as a JPEG, scaled for a w x h screen (device pixels),
                               not cropped: the page crops ("cover") or pads ("contain") like the frame.

Only with PICFORT_API_KEY set (the page is meant for the internet, behind the key); otherwise
404, so family photos are never shown to anyone who finds the address.
"""

import logging
import os
import threading
from collections import OrderedDict
from io import BytesIO

from flask import Flask, jsonify, request, send_file
from PIL import Image, ImageOps

import frame

FRAME_PAGE_PATH = "/frame"
PAGE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "frame_page.html")
MIN_SIDE, MAX_SIDE, DEFAULT_W, DEFAULT_H = 64, 4096, 1920, 1080
STEP = 128  # sizes are rounded up to this, so tablets of one kind share the cache
CACHE_SIZE = 6

log = logging.getLogger("browser_frame")


def screen_size(w, h) -> tuple:
    def side(value, default):
        try:
            n = int(value)
        except (TypeError, ValueError):
            n = default
        n = max(MIN_SIDE, min(MAX_SIDE, n))
        return min(MAX_SIDE, -(-n // STEP) * STEP)

    return side(w, DEFAULT_W), side(h, DEFAULT_H)


def scale_for_screen(img: Image.Image, size: tuple, fit: str) -> Image.Image:
    """Upright RGB, scaled so it covers (fit "cover") or fits in (otherwise) [size]; never enlarged."""
    img = ImageOps.exif_transpose(img)
    if img.mode != "RGB":
        rgba = img.convert("RGBA")
        bg = Image.new("RGB", rgba.size, "black")
        bg.paste(rgba, mask=rgba.getchannel("A"))
        img = bg
    w, h = size
    ratio = (max if fit == "cover" else min)(w / img.width, h / img.height)
    if ratio < 1:
        img = img.resize((max(1, round(img.width * ratio)), max(1, round(img.height * ratio))),
                         Image.Resampling.LANCZOS)
    return img


def register_browser_frame(app: Flask, controller, enabled: bool, fetch=None):
    fetch = fetch or (lambda asset_id: frame.fetch_image(controller.config, asset_id))
    cache = OrderedDict()  # (version, w, h) -> JPEG bytes
    cache_lock = threading.Lock()

    def no_store(resp):
        resp.headers["Cache-Control"] = "private, no-store"
        return resp

    def off():
        return jsonify(status="error", message="The browser frame needs PICFORT_API_KEY on the server"), 404

    @app.route(FRAME_PAGE_PATH)
    def frame_page():
        if not enabled:
            return off()
        resp = send_file(PAGE_FILE, mimetype="text/html", max_age=0)
        resp.headers["X-Robots-Tag"] = "noindex"
        resp.headers["Referrer-Policy"] = "no-referrer"
        return no_store(resp)

    @app.route(FRAME_PAGE_PATH + "/state")
    def frame_state():
        if not enabled:
            return off()
        with controller.lock:
            version, asset = controller.version, controller.current_asset
        return no_store(jsonify(version=version, shown=asset is not None, fit=controller.config.fit))

    @app.route(FRAME_PAGE_PATH + "/photo")
    def frame_photo():
        if not enabled:
            return off()
        size = screen_size(request.args.get("w"), request.args.get("h"))
        with controller.lock:
            version, asset, local = controller.version, controller.current_asset, controller.local_photo
        if asset is None:
            return jsonify(status="error", message="Nothing has been displayed yet"), 404
        key = (version, *size)
        with cache_lock:
            data = cache.get(key)
            if data is not None:
                cache.move_to_end(key)
        if data is None:
            try:
                if asset == "local":
                    if local is None:
                        return jsonify(status="error", message="The photo from the phone is gone"), 404
                    img = Image.open(BytesIO(local))
                    img.load()
                else:
                    img = fetch(asset)
                out = BytesIO()
                scale_for_screen(img, size, controller.config.fit).save(out, "JPEG", quality=90, optimize=True)
                data = out.getvalue()
            except frame.FrameError as e:
                return jsonify(status="error", message=str(e)), e.status
            except Exception as e:
                log.warning("Browser frame photo failed: %s", e)
                return jsonify(status="error", message=f"Could not prepare the photo: {e}"), 500
            with cache_lock:
                cache[key] = data
                while len(cache) > CACHE_SIZE:
                    cache.popitem(last=False)
        resp = send_file(BytesIO(data), mimetype="image/jpeg", max_age=0)
        resp.headers["X-Frame-Version"] = str(version)
        return no_store(resp)
