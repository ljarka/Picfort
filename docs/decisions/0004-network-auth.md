# ADR 0004 — No authentication on ImageFrameServer (trusted local network)

**Status:** accepted  
**Date:** 2026-11-08

## Context

ImageFrameServer exposes a REST API consumed by the Android app. The question is
whether this API needs authentication.

Arguments for auth:
- Prevents guests on the Wi-Fi from posting to the Family Wall
- Prevents accidental access from other devices

Arguments against auth:
- ImageFrameServer is on a home LAN, behind a router NAT; it is not exposed to the internet
- README already notes that plain `http://` is only allowed to private IPs and `.local`/`.lan`
- Family members should be able to post without a login flow
- Added complexity for a family tool used by non-technical members

## Decision

No authentication on ImageFrameServer endpoints.

**Preconditions that must hold:**
1. ImageFrameServer must NOT be exposed to the internet (no port forwarding, no public tunnel).
2. If Tailscale or similar VPN is used, only trusted devices should be in the network.
3. The README must include a warning not to expose ImageFrameServer publicly.

## Consequences

- Any device on the home network can post to the Wall, delete posts, and control the frame.
- If the user later wants auth (e.g. guest Wi-Fi risk), it can be added as an opt-in
  `WALL_SECRET` env variable that gates write operations only.
- The Immich token stored in the app is unrelated and is not used by ImageFrameServer.
