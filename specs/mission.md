# Picfort — Mission

> Part of the project constitution: **mission** (why) · [tech stack](tech-stack.md) (how) ·
> [roadmap](roadmap.md) (what next). Read all three before starting a new feature.

## What Picfort is

Picfort is a private family hub for Android, built on top of a self-hosted
[Immich](https://immich.app) photo server. It shows the Immich library and the photos on the phone
in one timeline, and adds what a family shares day to day: a Family Wall, shopping lists with
receipts and spending, events and birthdays, and school timetables. A small Picfort server next to
Immich keeps the family's data and can drive a picture frame.

## Who it is for

- **Primary users:** one family — parents first, then the children — who already run Immich at home.
- **Readers of these documents:** the owner and the AI agents that build most of Picfort, and other
  self-hosting families deciding whether to install it.

## The problem

A family that moved its photos to Immich still lives across many cloud apps: WhatsApp notes for
shopping, Google Keep, calendar invites for birthdays, a school portal, a photo frame with its own
app. Each one sends family data to a company, and none of them knows about the others. The Immich
app shows photos, but not the family around them.

## What Picfort offers

- **One app for the family's day:** wall, shopping, events, school and photos behind five tabs.
- **Private by design:** everything stays on the family's own server or on the phone.
- **Works where the family is:** offline copies for the shop and the school run; reminders planned on
  the phone.
- **Easy to run:** one Docker Compose file next to Immich, and a signed APK from GitHub releases.

## Guiding principles

1. **No cloud services.** No Firebase/FCM, no SaaS hosting, no third-party AI APIs. Smart features
   run on the phone (e.g. Gemini Nano for receipts, ADR 0009) or not at all.
2. **Immich is the photo backend.** Picfort reads and writes photos through Immich's API and never
   keeps its own photo store.
3. **Family data survives.** Every schema change ships a migration; nothing starts empty (ADR 0016).
4. **Simple to self-host.** A new feature must not need a new container or a manual setup step
   unless the roadmap says why.
5. **Small, reviewable steps.** Work moves through the [task queue](../docs/tasks/README.md), one
   task per pull request, with checks that must stay green (see [tech stack](tech-stack.md#quality-gates)).
6. **Plain language.** Screens, docs and commit messages use short, plain sentences.

## Non-goals

- **Cloud services of any kind**, including push services and hosted AI.
- **iOS or a web app.** Android only. The browser frame (`/frame`) is a display, not an app.
- **Many families on one server.** One family, one server; accounts are the Immich users.
- **Replacing Immich.** No photo editing, face recognition, ML or photo storage of our own.

## What success looks like

| Goal | We know it works when… |
|---|---|
| Daily family use | Both parents open Picfort most days for the wall, shopping or school, instead of chat notes, Keep or paper. |
| Replaces the Immich app | Picfort is the only photo app the family needs: timeline, backup, search, albums. |
| Easy self-hosting | Another family installs the server and the app from a release in under an hour, without help. |
| Agent-built, stays healthy | Most changes come from AI agents through the task queue, while CI, module boundaries and tests stay green. |
