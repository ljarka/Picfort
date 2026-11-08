# Architecture Overview

Picfort is a privacy-first family photo ecosystem built entirely on self-hosted infrastructure.
No Google Photos, no iCloud, no Amazon — everything runs at home.

## Components

```
┌─────────────────────────────────────────────────────────────────┐
│  Home network                                                   │
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────────┐  │
│  │  Android app │    │    Immich    │    │ ImageFrameServer │  │
│  │   (Picfort)  │◄──►│  (NAS/VPS)  │    │  (Raspberry Pi)  │  │
│  └──────┬───────┘    └──────────────┘    └────────┬─────────┘  │
│         │                                         │            │
│         └─────────────── REST ───────────────────►│            │
│                                                   │            │
│                                          ┌────────▼─────────┐  │
│                                          │  Display device  │  │
│                                          │  (tablet / e-ink)│  │
│                                          └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

### Android app (`app/`)
- Browse photos: unified timeline (Immich + phone MediaStore)
- Backup phone photos to Immich via WorkManager
- Control the picture frame (send photo, configure slideshow)
- Family Wall: post and read messages/photos shared with family

Tech stack: Kotlin, Jetpack Compose, Room, Hilt, Retrofit, WorkManager, Media3

### Immich
External self-hosted photo server. Picfort uses its REST API for:
- Timeline, albums, search, people, memories
- Asset management (share, delete, download)
- Backup upload

Picfort does not modify Immich. It is a pure API consumer.

### ImageFrameServer (`ImageFrameServer/`)
Python Flask service running on Raspberry Pi (or any Linux host). Responsibilities:
- Receive a photo from the app and display it on the connected screen
- Run an autonomous slideshow (album, memories, random modes)
- Serve the Family Wall: store and serve posts (text + photos)
- Expose a REST API consumed by the Android app

Tech stack: Python, Flask, SQLite, Pillow, Waveshare e-paper driver (optional)

### Display device
One of:
- Waveshare e-paper panel connected to the Raspberry Pi via SPI
- Android tablet in kiosk/frame mode running Picfort directly
- Any HDMI display connected to the Raspberry Pi

## Data flows

### Photo display (manual)
```
App ──POST /run?assetId=X──► ImageFrameServer ──► fetch from Immich ──► render ──► display
```

### Family Wall post
```
App ──POST /wall──► ImageFrameServer (SQLite) ──► stored
Display ──GET /wall (poll 60s)──► render posts
```

### Backup
```
Phone MediaStore ──► WorkManager ──► Immich REST API
```

## Key design principles

1. **Local network only** — ImageFrameServer and Picfort communicate only inside the home network.
2. **No auth on ImageFrameServer** — trusted home network; no credentials needed for family use.
3. **Immich is the source of truth** — Picfort never duplicates photo storage; Family Wall photos are the only Picfort-owned data.
4. **Polling over WebSocket** — 60-second polling for the wall is sufficient for home use and avoids persistent connections.
