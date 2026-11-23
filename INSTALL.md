# Install Picfort

From "I have Immich" to "every phone in the family is connected", in about 45 minutes.
You set up two things: the **Picfort server** (one small Docker container next to Immich) and the
**Picfort app** on each Android phone. Nothing goes to a cloud service.

The [README](README.md) has the reference material (all settings, the picture frame, building from
source); this page is only the way through.

## 1. What you need

- **Immich** (v2 or newer) running at home, with an account for each family member who will use
  Picfort. Family members in Picfort are the Immich users of your server.
- **An always-on machine with Docker** on the home network: often the one that runs Immich.
  64-bit Intel/AMD or ARM (e.g. a Raspberry Pi with 64-bit Raspberry Pi OS).
- **Android 8.0 or newer** on each phone.

## 2. Create an Immich API key

The server uses it to show photos on a picture frame. It only reads.

1. Open Immich in the browser, logged in as the person whose photos the frame should show.
2. **Account settings → API keys → New API key.**
3. Give it only these permissions: `asset.read`, `asset.view`, `asset.download`, `album.read`.
4. Copy the key; Immich shows it only once.

## 3. Start the server

On the always-on machine:

```bash
mkdir picfort && cd picfort
wget -O docker-compose.yml https://github.com/ljarka/Picfort/releases/latest/download/docker-compose.yml
wget -O .env https://github.com/ljarka/Picfort/releases/latest/download/example.env
nano .env
```

In `.env`, fill in two lines:

```
IMMICH_URL=http://192.168.1.20:2283
IMMICH_API_KEY=<the key from step 2>
```

`IMMICH_URL` is the address **the server** uses to reach Immich:

| Where Immich runs | `IMMICH_URL` |
|---|---|
| Another machine at home | Its address, e.g. `http://192.168.1.20:2283` |
| The same machine, Linux | The machine's own home-network address, e.g. `http://192.168.1.20:2283` (not `localhost`: inside the container that is the container itself) |
| The same machine, Docker Desktop (Mac, Windows) | `http://host.docker.internal:2283` |
| Online, e.g. `https://photos.example.com` | That address |

On a Mac, port 5000 is taken by AirPlay Receiver: also set `PICFORT_PORT=5050` (and use 5050 below).

Then start it:

```bash
docker compose up -d
```

## 4. Check that it works

```bash
docker compose ps                     # picfort_server should be "Up" (or "running")
curl http://localhost:5000/status     # the answer contains "status":"ok"
```

If it isn't running, look at the log: `docker compose logs`. The line
`Set IMMICH_URL and IMMICH_API_KEY (in .env), then start the server again.` means one of them is
empty; fix `.env` and run `docker compose up -d` again.

## 5. Find the machine's address

The phones need the server machine's address on the home network, e.g. `192.168.1.20`.

- Linux / Raspberry Pi: `hostname -I` (the first one).
- Mac: **System Settings → Wi-Fi → Details → IP address**, or `ipconfig getifaddr en0`.
- Windows: `ipconfig`, "IPv4 Address".
- Or look it up in your router's list of devices. Giving the machine a fixed address in the router
  saves trouble later.

## 6. Install the app

On each phone, open [the latest release](https://github.com/ljarka/Picfort/releases/latest) in the
browser and download `picfort-<version>.apk`. Open the downloaded file; Android asks once to allow
installing apps from the browser: allow it, then **Install**.

## 7. First start

Picfort opens on one page:

1. **Immich address**: the address the phone uses for Immich, e.g. `192.168.1.20:2283` or
   `photos.example.com`. Without `http://` or `https://`, home-network addresses use `http://` and
   all others `https://`; the line under the field shows what will be used.
2. **Email** and **Password**: this person's own Immich account.
3. **Picfort server address**: `192.168.1.20:5000` (your address from step 5 and your port).
4. **Connect.** If the Picfort server doesn't answer, the page says so: fix it and try again, or
   skip it for now (Settings → Picfort server later).
5. Allow access to photos (so phone photos show in the timeline) and notifications (reminders).

You should now see the Family Wall, with the family's avatars at the top.

## 8. The rest of the family

Repeat steps 6–7 on every phone, each person with **their own** Immich account. To add an account,
an Immich admin goes to **Administration → Users → Create user**.

Names and photos in Picfort can be changed under **Settings → Family** (and test accounts hidden).

## 9. Keep it running

- **Back up the `data` folder** next to `docker-compose.yml`. It holds everything the family types in:
  the wall, shopping lists and receipts, events, school timetables. Photos stay in Immich.
- **Update the server:** `docker compose pull && docker compose up -d`. Data is migrated
  automatically; the old database file is kept as a `.bak` copy first.
- **Update the app:** download the new APK from the latest release and install it over the old one.

## 10. If something doesn't work

| What you see | What to do |
|---|---|
| The app can't reach Immich | Open the same address in the phone's browser. If that fails too, the phone isn't on the home network, or the port is wrong (Immich's default is 2283). |
| "Picfort server doesn't answer" | Phone and server on the same network? Right port? `curl http://localhost:5000/status` on the server machine works? A firewall on the machine may block the port. |
| `curl` works on the machine but not from the phone | A firewall on the machine: allow the port (e.g. `sudo ufw allow 5000/tcp`). |
| The container keeps restarting | `docker compose logs`: usually an empty or wrong `IMMICH_URL` / `IMMICH_API_KEY`. |
| Port 5000 already in use (Mac) | `PICFORT_PORT=5050` in `.env`, `docker compose up -d`, and `:5050` in the app. |
| Someone is missing from the family row | They need their own Immich user; hidden members come back under Settings → Family. |
| Android won't install the APK | Allow "Install unknown apps" for your browser in Android settings. An older Picfort signed with another key must be uninstalled first. |

## 11. Next steps (optional)

- **Back up phone photos to Immich:** Settings → Backup in the app.
- **Use it away from home:** [Reaching the server from outside](README.md#reaching-the-server-from-outside)
  (a tunnel plus `PICFORT_API_KEY`). Without it, shopping lists and timetables stay readable offline.
- **A frame in a browser** (tablet, old laptop): [Frame in a browser](README.md#frame-in-a-browser).
- **An e-paper picture frame:** [Raspberry Pi + Waveshare](README.md#raspberry-pi--waveshare-133-e-paper-hat-e).
