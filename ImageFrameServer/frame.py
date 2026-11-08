"""Fetch a photo from Immich, render it for the frame and push it to the display.

Can be used as a library (see app.py) or from the command line:

    python3 frame.py <assetId>
"""

import importlib
import logging
import os
import sys
from dataclasses import dataclass
from io import BytesIO

import requests
from PIL import Image, ImageEnhance, ImageOps

try:  # Optional: lets Pillow open HEIC/HEIF originals from iPhones.
    from pillow_heif import register_heif_opener

    register_heif_opener()
except ImportError:
    pass

log = logging.getLogger("frame")

# E Ink Spectra 6 colours, in the order: black, white, yellow, red, blue, green.
# IDEAL is what the Waveshare driver's getbuffer() maps to. MEASURED is roughly
# what the inks look like on the panel. Dithering against MEASURED gives much
# better photos than dithering against the pure colours.
# MEASURED is an approximation; tune it with FRAME_PALETTE.
SPECTRA6_IDEAL = [(0, 0, 0), (255, 255, 255), (255, 255, 0), (255, 0, 0), (0, 0, 255), (0, 255, 0)]
SPECTRA6_MEASURED = [(25, 30, 33), (232, 232, 224), (226, 206, 36), (160, 32, 32), (34, 72, 156), (50, 106, 60)]


@dataclass(frozen=True)
class Config:
    immich_url: str
    immich_api_key: str
    # Native panel resolution, in the orientation the driver expects its buffer.
    width: int = 1200
    height: int = 1600
    # "auto": rotate so each photo fills the panel in its natural orientation
    #         (portrait photos as-is, landscape photos rotated 90°).
    # "portrait" / "landscape": the frame is mounted one way; photos are fitted to it.
    orientation: str = "auto"
    # "cover": fill the panel, cropping the edges (centered).
    # "contain": show the whole photo, padding with the background colour.
    fit: str = "cover"
    background: str = "white"
    # Python module of the display driver, e.g. "epd13in3E" (Waveshare 13.3" E6).
    # Empty = no display attached; images are only written to preview_path.
    driver: str = ""
    # Directory added to sys.path so the driver (and its epdconfig) can be imported,
    # e.g. ".../13.3inch_e-Paper_E/RaspberryPi/python/lib".
    driver_path: str = ""
    # "spectra6": Floyd-Steinberg dither to the 6 inks. "none": send full colour to the driver.
    dither: str = "spectra6"
    palette: tuple = tuple(SPECTRA6_MEASURED)
    # E-paper looks washed out; a little extra saturation/contrast helps photos.
    saturation: float = 1.3
    contrast: float = 1.1
    preview_path: str = "last_frame.png"
    request_timeout: float = 30.0

    @staticmethod
    def from_env() -> "Config":
        def env(name, default=""):
            return os.environ.get(name, default).strip()

        cfg = Config(
            immich_url=env("IMMICH_URL").rstrip("/"),
            immich_api_key=env("IMMICH_API_KEY"),
            width=int(env("FRAME_WIDTH", "1200")),
            height=int(env("FRAME_HEIGHT", "1600")),
            orientation=env("FRAME_ORIENTATION", "auto").lower(),
            fit=env("FRAME_FIT", "cover").lower(),
            background=env("FRAME_BACKGROUND", "white"),
            driver=env("FRAME_DRIVER"),
            driver_path=env("FRAME_DRIVER_PATH"),
            dither=env("FRAME_DITHER", "spectra6").lower(),
            palette=_parse_palette(env("FRAME_PALETTE")) or tuple(SPECTRA6_MEASURED),
            saturation=float(env("FRAME_SATURATION", "1.3")),
            contrast=float(env("FRAME_CONTRAST", "1.1")),
            preview_path=env("FRAME_PREVIEW_PATH", "last_frame.png"),
        )
        if cfg.orientation not in ("auto", "portrait", "landscape"):
            raise ValueError("FRAME_ORIENTATION must be auto, portrait or landscape")
        if cfg.fit not in ("cover", "contain"):
            raise ValueError("FRAME_FIT must be cover or contain")
        if cfg.dither not in ("spectra6", "none"):
            raise ValueError("FRAME_DITHER must be spectra6 or none")
        return cfg


def _parse_palette(value: str):
    """'#191e21,#e8e8e0,…' (6 colours: black, white, yellow, red, blue, green)."""
    if not value:
        return None
    colours = [c.strip().lstrip("#") for c in value.split(",")]
    if len(colours) != 6 or any(len(c) != 6 for c in colours):
        raise ValueError("FRAME_PALETTE needs 6 hex colours: black,white,yellow,red,blue,green")
    return tuple(tuple(int(c[i : i + 2], 16) for i in (0, 2, 4)) for c in colours)


class FrameError(Exception):
    """An error with an HTTP status code suitable for returning to the client."""

    def __init__(self, message: str, status: int = 500):
        super().__init__(message)
        self.status = status


# --------------------------------------------------------------------------- fetch


def fetch_image(cfg: Config, asset_id: str) -> Image.Image:
    """Download the asset's original; fall back to Immich's preview if Pillow can't decode it."""
    if not cfg.immich_url or not cfg.immich_api_key:
        raise FrameError("IMMICH_URL and IMMICH_API_KEY must be set on the frame server", 500)

    headers = {"x-api-key": cfg.immich_api_key}
    base = f"{cfg.immich_url}/api/assets/{asset_id}"
    last_error = None

    for url in (f"{base}/original", f"{base}/thumbnail?size=preview"):
        try:
            response = requests.get(url, headers=headers, timeout=cfg.request_timeout)
        except requests.RequestException as e:
            raise FrameError(f"Could not reach Immich: {e}", 502) from e

        if response.status_code in (401, 403):
            raise FrameError("Immich rejected the API key", 502)
        if response.status_code == 404:
            raise FrameError("Asset not found on Immich", 404)
        if response.status_code != 200:
            raise FrameError(f"Immich returned HTTP {response.status_code}", 502)

        try:
            img = Image.open(BytesIO(response.content))
            img.load()
            return img
        except Exception as e:  # unsupported format (e.g. HEIC without pillow-heif, video)
            log.warning("Could not decode %s: %s", url, e)
            last_error = e

    raise FrameError(f"Unsupported image format: {last_error}", 415)


# -------------------------------------------------------------------------- render


def render(cfg: Config, img: Image.Image) -> Image.Image:
    """Return an RGB image of exactly cfg.width x cfg.height, ready for the driver."""
    img = ImageOps.exif_transpose(img)  # respect camera orientation
    if img.mode != "RGB":
        # Flatten transparency onto the background instead of black.
        rgba = img.convert("RGBA")
        bg = Image.new("RGB", rgba.size, cfg.background)
        bg.paste(rgba, mask=rgba.getchannel("A"))
        img = bg

    panel_portrait = cfg.height >= cfg.width
    photo_portrait = img.height >= img.width

    if cfg.orientation == "auto":
        rotate = panel_portrait != photo_portrait
    else:
        mounted_portrait = cfg.orientation == "portrait"
        rotate = panel_portrait != mounted_portrait

    # Canvas as the viewer sees it; rotated into the panel's buffer at the end.
    target = (cfg.height, cfg.width) if rotate else (cfg.width, cfg.height)

    if cfg.fit == "cover":
        canvas = ImageOps.fit(img, target, Image.Resampling.LANCZOS, centering=(0.5, 0.5))
    else:
        canvas = ImageOps.pad(img, target, Image.Resampling.LANCZOS, color=cfg.background)

    if rotate:
        canvas = canvas.rotate(-90, expand=True)
    return canvas


# -------------------------------------------------------------------------- dither


def _palette_image(colours) -> Image.Image:
    flat = [v for c in colours for v in c]
    # Pad with the first colour: padding with (0,0,0) would add an extra, purer black.
    flat += list(colours[0]) * (256 - len(colours))
    pal = Image.new("P", (1, 1))
    pal.putpalette(flat)
    return pal


def dither(cfg: Config, img: Image.Image):
    """Return (preview, panel) images.

    preview: what the panel should roughly look like (measured ink colours).
    panel:   the same pixels in the driver's pure colours, so its own quantize()
             is an exact 1:1 mapping and doesn't dither a second time.
    """
    if cfg.dither == "none":
        return img, img

    if cfg.saturation != 1.0:
        img = ImageEnhance.Color(img).enhance(cfg.saturation)
    if cfg.contrast != 1.0:
        img = ImageEnhance.Contrast(img).enhance(cfg.contrast)

    indexed = img.quantize(palette=_palette_image(cfg.palette), dither=Image.Dither.FLOYDSTEINBERG)
    preview = indexed.copy()
    preview.putpalette(_palette_image(cfg.palette).getpalette())
    panel = indexed.copy()
    panel.putpalette(_palette_image(SPECTRA6_IDEAL).getpalette())
    return preview.convert("RGB"), panel.convert("RGB")


# ------------------------------------------------------------------------- display


def _load_driver(cfg: Config):
    if cfg.driver_path and cfg.driver_path not in sys.path:
        sys.path.insert(0, cfg.driver_path)
    return importlib.import_module(cfg.driver)


def display(cfg: Config, frame: Image.Image) -> None:
    """Dither, save a preview and, if a driver is configured, push the image to the panel.

    The driver is expected to follow the Waveshare e-Paper API:
    EPD().Init() (or .init()), .getbuffer(img), .display(buf), .sleep().
    """
    preview, panel = dither(cfg, frame)
    preview.save(cfg.preview_path)
    log.info("Saved preview to %s", cfg.preview_path)

    if not cfg.driver:
        log.info("FRAME_DRIVER not set; skipping hardware display")
        return

    epd = _load_driver(cfg).EPD()
    init = getattr(epd, "Init", None) or epd.init  # 13.3" (E) uses Init()
    try:
        init()
        epd.display(epd.getbuffer(panel))
    finally:
        epd.sleep()  # e-paper panels must be put to sleep to avoid damage


def prepare(cfg: Config, asset_id: str) -> Image.Image:
    return render(cfg, fetch_image(cfg, asset_id))


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    if len(sys.argv) != 2:
        sys.exit("usage: frame.py <assetId>")
    config = Config.from_env()
    try:
        display(config, prepare(config, sys.argv[1]))
    except FrameError as e:
        sys.exit(f"error: {e}")
    print("done")
