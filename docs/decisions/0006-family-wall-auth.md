# ADR 0006 — Family Wall checks the caller's Immich token

**Status:** accepted  
**Date:** 2026-11-10  
**Supersedes:** [ADR 0004](0004-network-auth.md) for the `/wall` endpoints only

## Context

ADR 0004 left ImageFrameServer without authentication on a trusted home network. Once the
Family Wall's shape was settled (T-001, T-002), that no longer fit:

- The wall holds private content (family messages and photos). Any device on the Wi-Fi
  (guests, TVs, IoT) could read it.
- The server took `author_id` from the client, so anyone could post as another family member
  and delete other people's posts. "Only your own posts" existed only in the app's UI.
- Family members are already the Immich users of the server (T-002), and every phone is
  logged in to Immich.
- T-011 expects frames reached over a VPN, which widens "the local network".

Options:
1. Shared secret (`WALL_SECRET`, the follow-up named in ADR 0004): one password typed on every
   phone. It keeps strangers out, but the server still doesn't know who is writing.
2. The app sends its Immich token and the server asks Immich who it belongs to.

## Decision

Option 2. Every `/wall` request carries `Authorization: Bearer <Immich access token>`.
ImageFrameServer calls `GET <IMMICH_URL>/api/users/me` with it:

- 200 → the caller is that user. The author of a new post is this user (id and name). The
  client no longer sends author fields.
- 401/403 from Immich, or no token → `401` with `WWW-Authenticate: Bearer`.
- Immich unreachable or another error → `502`, so the app doesn't treat it as a logout.
- Only the author can delete or pin a post (`403` otherwise). Every user can read.
- Accepted lookups are cached in memory for 5 minutes, keyed by a SHA-256 of the token.
  Rejections are not cached. Tokens are never written to disk or logs.

The frame endpoints (`/run`, `/status`, `/slideshow`, `/next`, `/albums`, `/current`)
stay as ADR 0004 decided.

## Consequences

- The app now sends its Immich login token to ImageFrameServer, but only on `/wall` calls.
  Someone who controls the frame host could capture tokens. The frame host is part of the
  family's own setup, the same trust level as the Immich server. The README says so.
- Only tokens from the frame's own Immich (`IMMICH_URL`) are accepted, so the wall belongs to
  that server's users.
- Logging out of Immich (or a revoked session) locks the wall within 5 minutes.
- No extra setup for family members, and no secret to distribute.
- The tablet frame (T-003) reads the wall with the token of the account it's logged in with.
- Plain `http://` to the frame carries the token unencrypted on the LAN/VPN. That's
  acceptable for a home network or Tailscale. Use HTTPS (a reverse proxy) for anything else.
  ImageFrameServer must still not be exposed to the internet (ADR 0004 precondition 1).
