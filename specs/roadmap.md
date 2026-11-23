# Picfort — Roadmap

> Part of the project constitution: [mission](mission.md) (why) · [tech stack](tech-stack.md) (how) ·
> **roadmap** (what next). This file looks **ahead only**; what is done and each task's status
> live in [docs/STATUS.md](../docs/STATUS.md).

## How to read it

- **One step = one task** (`T-xxx`): one pull request, about one agent session, reviewed by the owner.
- Steps are in order; take the first one that is not done. Each has a goal and a *done when* line.
- A step marked *(to write)* has no task brief yet: writing it in `docs/tasks/` is the first part of
  the work, and Android screens start with a mockup the owner approves.
- Already shipped: wall, shopping, receipts & spending, events & birthdays, school timetables,
  school journal, timeline, backup, browser frame (see STATUS.md).

## 1. Easy self-hosting

So that another family can install Picfort in under an hour, without help.

| Step | Goal | Done when… |
|---|---|---|
| **T-085** Install guide | One page from "I have Immich" to "the app is connected": server, API key, APK, first run. | Someone follows it on a clean machine in under an hour; README links to it. |
| **Release notes & upgrades** *(to write)* | Each release says what changed and how to update safely. | Releases carry notes; the update path (`docker compose pull`) is tested with a migration in CI. |

## 2. Frame review

| Step | Goal | Done when… |
|---|---|---|
| **Re-check frame tasks** *(to write)* | T-004, T-010 and T-011 were written for the old frame server, before the browser frame (T-072). | Each is rewritten for today's frame or closed with the reason in STATUS.md. |

## 3. Content discovery (toward replacing the Immich app)

New screens open from the **Photos tab** (entry points at its top), not a sixth tab.

| Step | Goal | Done when… |
|---|---|---|
| **T-007** Memories ("On this day") | Photos from this day in past years. | Reachable from Photos, works offline from the cache, Paparazzi snapshots in place. |
| **T-008** People | Browse photos by face, from Immich's people. | A person's photos open from Photos; hidden people stay hidden. |
| **T-009** Map | Photos by place. | A map of photo locations opens from Photos and leads to the photos. |

## 4. Backup gaps

| Step | Goal | Done when… |
|---|---|---|
| **T-005** Backup album filter | Back up only chosen phone albums. | The choice is in backup settings and respected by the worker. |

## 5. Frame enhancements

Only after step 2; the order and content follow its outcome.

| Step | Goal | Done when… |
|---|---|---|
| **T-004** Phone-only photos to the frame | Send a photo that is not in Immich yet. | As rewritten in step 2. |
| **T-010** Live frame status | See what the frame shows. | As rewritten in step 2. |
| **T-011** Several frames | More than one frame per family. | As rewritten in step 2. |

## Later, not planned yet

- Self-hosted push (UnifiedPush/ntfy), only if polling delays start to hurt — needs an ADR first
  ([tech stack](tech-stack.md#notifications)).
