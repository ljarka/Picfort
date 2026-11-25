## How to update

**Server:** `docker compose pull && docker compose up -d`

Schema changes are applied automatically; the previous database file is saved as `.bak` before the migration runs. Check `docker compose logs` if something looks wrong.

**Android app:** Download `picfort-<version>.apk` from this release and install it on each phone over the existing version — Android upgrades in-place, no data is lost.

> Need help? See [step 9 of the install guide](https://github.com/ljarka/Picfort/blob/main/INSTALL.md#9-keep-it-running).
