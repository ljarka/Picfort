# ADR 0018 — Wall reaction notifications: the author's phone asks, no push service

**Status:** accepted  
**Date:** 2026-11-18  
**Task:** [T-084](../tasks/T-084-wall-reactions.md)

## Context

The author of a wall post should get a notification when someone first reacts to it (owner
request 2026-11-18). Picfort has no push channel: no Firebase, no cloud service (vision: everything
on the family's own server), and the server does not know the phones.

## Decision

1. The **server remembers** the first reaction by someone other than the author on the post
   (`first_reaction_at / _by / _emoji`), set once and never reset.
2. The **author's phone asks** every 15 minutes (WorkManager periodic minimum, like the school
   journal, T-082): `GET /wall/first-reactions?since=<cursor>`, cursor per Immich user on the phone,
   started at "now" so history never notifies.
3. One notification per post, ever. Later reactions are seen on the wall, not notified.

## Consequences

- Up to ~15 minutes (more in Doze) between the reaction and the notification; fine for a family wall.
- No new dependency, no data leaves the family's server.
- A phone that was off longer than its cursor sees all missed first reactions at once (≤ 50).
- Rejected: Firebase Cloud Messaging (Google account and a cloud service in the path), a
  foreground service with a long poll (battery, a permanent notification).
