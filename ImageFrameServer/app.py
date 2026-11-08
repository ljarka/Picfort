"""HTTP API used by the Picfort app to put photos on the frame.

    GET /status              -> {"status": "ok", "busy": bool, ...}
    GET /run?assetId=<uuid>  -> 202 {"status": "accepted"} once the photo is fetched and
                                rendered; the (slow) e-paper refresh continues in the background.
    GET /current             -> PNG of the last rendered frame
"""

import logging
import os
import re
import threading
import time

from flask import Flask, jsonify, request, send_file

import frame

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("server")

config = frame.Config.from_env()
MIN_INTERVAL = float(os.environ.get("FRAME_MIN_INTERVAL", "30"))
UUID_RE = re.compile(r"^[0-9a-fA-F-]{36}$")

app = Flask(__name__)

_lock = threading.Lock()  # guards the fields below
_busy = False
_last_run = 0.0
_current_asset = None
_last_error = None


def error(message, status, **extra):
    return jsonify(status="error", message=message, **extra), status


def _show(frame_img, asset_id):
    global _busy, _current_asset, _last_error
    try:
        frame.display(config, frame_img)
        with _lock:
            _current_asset, _last_error = asset_id, None
        log.info("Displayed %s", asset_id)
    except Exception as e:
        log.exception("Display failed")
        with _lock:
            _last_error = str(e)
    finally:
        with _lock:
            _busy = False


@app.route("/run")
def run():
    global _busy, _last_run
    asset_id = request.args.get("assetId", "").strip()
    if not UUID_RE.match(asset_id):
        return error("assetId must be an Immich asset id", 400)

    with _lock:
        if _busy:
            return error("The frame is still refreshing", 409)
        wait = MIN_INTERVAL - (time.monotonic() - _last_run)
        if wait > 0:
            resp = error("Too soon, please wait", 429, retry_after=int(wait) + 1)
            resp[0].headers["Retry-After"] = str(int(wait) + 1)
            return resp
        _busy = True

    try:
        frame_img = frame.prepare(config, asset_id)
    except frame.FrameError as e:
        with _lock:
            _busy = False
        log.warning("Rejected %s: %s", asset_id, e)
        return error(str(e), e.status)
    except Exception as e:
        with _lock:
            _busy = False
        log.exception("Rendering failed")
        return error(f"Rendering failed: {e}", 500)

    with _lock:
        _last_run = time.monotonic()
    threading.Thread(target=_show, args=(frame_img, asset_id), daemon=True).start()
    return jsonify(status="accepted", assetId=asset_id), 202


@app.route("/status")
def status():
    with _lock:
        return jsonify(
            status="ok",
            busy=_busy,
            currentAsset=_current_asset,
            lastError=_last_error,
            width=config.width,
            height=config.height,
            hardware=bool(config.driver),
        )


@app.route("/current")
def current():
    if not os.path.exists(config.preview_path):
        return error("Nothing has been displayed yet", 404)
    return send_file(os.path.abspath(config.preview_path), mimetype="image/png", max_age=0)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "5000")), threaded=True)
