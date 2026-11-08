# Picfort

Picfort is an Android client for a self-hosted [Immich](https://immich.app) photo server. It shows your Immich library and the photos on your phone in one timeline. It can also send a photo to a picture frame on your home network.

> **Status:** early, personal project.

## Features

- **One timeline.** Photos from Immich and from the phone's MediaStore (the last 24 months) are merged and grouped by month, newest first. A cloud icon shows where each image comes from.
- **Aspect-ratio grid.** A 4-column grid where wide photos take more columns. The number of rows per month is cached so the scrollbar is right before the images load.
- **Fast scroll.** A draggable scrollbar that shows the month you're scrolling to.
- **Offline-first cache.** Months and assets are stored in Room. Cached data is shown at once and refreshed in the background. Pull down to refresh.
- **Viewer.** A swipeable full-screen pager with pinch-to-zoom and a shared-element transition from the grid.
- **Backup.** Uploads the phone's photos and videos to Immich in the background (WorkManager). You can limit it to Wi-Fi or to while charging. Files Immich already has are detected by SHA-1 checksum and not uploaded again, and phone copies of backed-up photos are hidden so nothing shows twice.
- **Video.** Phone and Immich videos play in the viewer (Media3), with a duration badge in the grid.
- **Details.** Date, camera, lens, exposure, size, place, people and a map link for each item.
- **Selection actions.** Long-press to select items, then share, download to the phone, send to the frame, or delete. Deleted items go to Immich's or the phone's trash.
- **Settings.** Backup, the frame address, and your account.
- **Send to frame.** Puts the photo you're viewing on an e-paper picture frame (see below).

## Repository layout

```
app/                     Android app (Kotlin, Jetpack Compose)
  src/main/java/com/github/ljarka/picfort/
    db/                  Room database: month buckets and assets
    local/               MediaStore queries for photos on the phone
    network/             OkHttp/Retrofit setup and auth interceptor
    ui/init/             Startup routing: server setup, login, timeline
    ui/server/           Server URL screen and storage
    ui/login/            Login screen, token storage (encrypted)
    ui/timeline/         Timeline screen, repository, grid row layout, fast scroll
    ui/image/            Full-screen viewer, zoom
    ui/frame/            Frame settings, frame HTTP client (no Immich auth)
    ui/settings/         Settings screen (backup, frame, account)
    assets/              Share, download, delete, details (Immich API + MediaStore)
    backup/              Backup engine, worker, scheduler, upload API
    ui/preferences/      DataStore / encrypted preferences
ImageFrameServer/        Python service for the picture frame
  app.py                 Flask HTTP API (/run, /status, /current)
  frame.py               Fetches from Immich, renders for the panel, drives the display
```

## Architecture

```
Compose UI ──► ViewModel (Hilt) ──► TimelineRepository ──┬─► Room (month_buckets, assets)
                                                         ├─► Immich API (Retrofit)
                                                         └─► MediaStore (LocalImagesProvider)
```

- **Navigation:** `Init` → `Timeline` → `ImageDetails(assetId, assetType, imageIndex)`, using type-safe Navigation Compose routes.
- **Buckets:** monthly counts from `/api/timeline/buckets` are added to the local MediaStore counts. Each bucket stores a running global `index`, so the pager can turn a flat position into a month and an offset.
- **Assets:** loaded per month when needed (`/api/timeline/bucket` plus MediaStore), sorted by date taken and saved to Room. The ViewModel keeps at most 5 loads running and cancels the oldest.
- **Auth:** you log in with email and password and get an access token. The token is stored in encrypted preferences and added as `Authorization: Bearer …` to every request.

## Requirements

- Android Studio (recent stable), JDK 11+
- Android 8.0+ (minSdk 26, targetSdk 35)
- An Immich server you can reach from the phone

## Building and running

```bash
./gradlew assembleDebug          # build a debug APK
./gradlew installDebug           # install on a connected device or emulator
```

Or open the project in Android Studio and run the `app` configuration.

The first time you start the app:

1. Enter your Immich server URL, for example `https://photos.example.com`.
2. Log in with your Immich email and password.
3. Allow photo access so photos on the phone appear in the timeline.

## ImageFrameServer

A small Flask service that puts Immich photos on a picture frame. It is meant to run on the device that drives the display, a Raspberry Pi with a Waveshare 13.3" e-Paper HAT+ (E). The frame downloads photos from Immich with its own API key, so the app never sends it your login token.

### Setup

```bash
cd ImageFrameServer
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env              # then fill in IMMICH_URL and IMMICH_API_KEY
set -a; . ./.env; set +a
python3 app.py                    # listens on 0.0.0.0:5000
```

Create the API key in Immich under **Account settings → API keys**. It needs to be able to read assets.

Without `FRAME_DRIVER` the server runs in **preview mode**: every frame is dithered and saved to `last_frame.png`, which you can view at `/current`. That way you can try it on any computer.

### Raspberry Pi + Waveshare 13.3" e-Paper HAT+ (E)

The frame uses Waveshare's 13.3" E Ink Spectra 6 panel (1200×1600, 6 colours, about 19 s full refresh).

1. Enable SPI: `sudo raspi-config` → *Interface Options* → *SPI* → enable. Add these lines to `/boot/firmware/config.txt` (`/boot/config.txt` on older OS versions), then reboot:
   ```
   gpio=7=op,dl
   gpio=8=op,dl
   ```
2. Install the system packages: `sudo apt install python3-pil python3-numpy python3-spidev`.
3. Download Waveshare's driver. It isn't bundled here because it ships prebuilt `DEV_Config_*.so` files:
   ```bash
   wget "https://files.waveshare.com/wiki/13.3inch%20e-Paper%20HAT%2B/13.3inch_e-Paper_E.zip"
   unzip 13.3inch_e-Paper_E.zip -d ~/13.3inch_e-Paper_E
   ```
   You can check the wiring with Waveshare's demo: `cd ~/13.3inch_e-Paper_E/RaspberryPi/python/examples && python3 epd_13in3E_test.py`.
4. In `.env`, set:
   ```
   FRAME_DRIVER=epd13in3E
   FRAME_DRIVER_PATH=/home/pi/13.3inch_e-Paper_E/RaspberryPi/python/lib
   ```
   If you use a venv, create it with `--system-site-packages` so it can use `spidev`.

**Colour.** Photos are Floyd-Steinberg dithered against an approximation of how the six inks actually look, after a small saturation and contrast boost. The result is then mapped to the driver's pure colours, so the driver doesn't dither a second time. If colours look off, adjust `FRAME_SATURATION`, `FRAME_CONTRAST` or `FRAME_PALETTE`, and compare the panel with `/current`.

| Variable | Default | Meaning |
|---|---|---|
| `IMMICH_URL` | (required) | Immich base URL, e.g. `https://photos.example.com` |
| `IMMICH_API_KEY` | (required) | Immich API key |
| `FRAME_WIDTH` / `FRAME_HEIGHT` | `1200` / `1600` | Panel resolution in the driver's buffer orientation |
| `FRAME_ORIENTATION` | `auto` | `auto` rotates each photo to fill the panel. Use `portrait` or `landscape` for a frame mounted one way. |
| `FRAME_FIT` | `cover` | `cover` fills the panel and crops the edges evenly. `contain` shows the whole photo with padding. |
| `FRAME_BACKGROUND` | `white` | Padding colour |
| `FRAME_DRIVER` | (empty) | Driver module, `epd13in3E` for the 13.3" (E). Leave empty for preview mode. |
| `FRAME_DRIVER_PATH` | (empty) | Directory containing the driver (Waveshare's `RaspberryPi/python/lib`) |
| `FRAME_DITHER` | `spectra6` | `spectra6` dithers to the six inks; `none` sends full colour to the driver |
| `FRAME_PALETTE` | built-in | Six hex colours (black, white, yellow, red, blue, green) as they look on your panel |
| `FRAME_SATURATION` / `FRAME_CONTRAST` | `1.3` / `1.1` | Adjustments applied before dithering |
| `FRAME_MIN_INTERVAL` | `30` | Minimum number of seconds between refreshes (protects the e-paper panel) |
| `PORT` | `5000` | HTTP port |

HEIC originals need `pip install pillow-heif`. Otherwise the server falls back to Immich's preview image.

### API

| Endpoint | Response |
|---|---|
| `GET /status` | `{"status":"ok","busy":false,"currentAsset":…,"lastError":…}` |
| `GET /run?assetId=<id>` | `202` once the photo is fetched and rendered; the panel then refreshes in the background. Errors: `400` bad id, `404` asset not found, `409` still refreshing, `429` too soon (with `retry_after` and a `Retry-After` header), `502` Immich problem. |
| `GET /current` | PNG of the last rendered frame |

You can also run it from the command line: `python3 frame.py <assetId>`.

### Using it from the app

Open a photo, tap **⚙** and enter the frame's address (for example `192.168.0.105:5000`). Tap **Test**, then **Save**. After that, the **Send** button puts the photo you're viewing on the frame. Only photos on the Immich server can be sent, not photos that exist only on the phone.

## Security notes

- Plain `http://` is only allowed to local network and VPN addresses (private IPs, `.local`/`.lan`, Tailscale). Use `https://` for servers on the internet.
- HTTP logging is only on in debug builds, with credentials redacted.
- The access token is stored in encrypted preferences. If the server rejects it, the app returns to the login screen.

## Known limitations

- Photos that exist only on the phone can't be sent to the frame yet.
- Backup covers every photo and video on the phone; you can't pick albums yet.
- Backup runs in the background without a notification, so a very large first backup takes several runs.
- Saving to the phone needs Android 10+, and deleting phone photos needs Android 11+.
- Months that were never opened while online show placeholders offline.

See the roadmap in the issues or project board.
