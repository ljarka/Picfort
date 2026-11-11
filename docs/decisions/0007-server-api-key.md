# ADR 0007 — Optional API key for a Picfort server reachable from outside

**Status:** accepted  
**Date:** 2026-11-11  
**Amends:** [ADR 0004](0004-network-auth.md) precondition 1 (no public tunnel)

## Context

ADR 0004 keeps the frame endpoints open because the server lives on the home network. Some
people want the app to reach the Picfort server away from home through a tunnel such as
Cloudflare Tunnel, without a VPN on every phone. Then anyone who finds the hostname can control
the frame and, with any token Immich accepts, use the Family Wall.

## Decision

- `PICFORT_API_KEY` on the server, empty by default. When set, **every** request needs
  `X-Picfort-Key: <key>`, frame control and `/wall` alike; otherwise 401 with
  `WWW-Authenticate: Picfort-Key`. The comparison is constant-time.
- The `/wall` endpoints still check the Immich token as well (ADR 0006): the key says "this is
  one of our apps", the token says which family member it is.
- With a key set, every response is `Cache-Control: private`, so a proxy never keeps a wall
  photo (served with a one-year max-age) for other callers.
- The app has an optional "API key" field next to the server address. The key is stored
  encrypted and sent only to the saved Picfort server (same scheme, host, port and path prefix),
  never to Immich. A 401 with `Picfort-Key` is reported as a key problem, not as a rejected
  Immich login.

## Consequences

- Without the key, nothing changes: ADR 0004 still applies on the home network.
- With the key, the server can be published through a tunnel that terminates TLS (`https://`
  is required by the app outside private networks anyway). A single shared key: changing it
  means entering the new one on every phone.
- Anyone holding the key can control the frame; the Family Wall still needs a family member's
  Immich login.
