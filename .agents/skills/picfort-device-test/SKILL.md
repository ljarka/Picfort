---
name: picfort-device-test
description: Check a Picfort change on an Android emulator or device with Maestro, on the test account and the test server only. Use when asked to run, smoke-test, click through or verify the app on a device, to write or fix a flow in maestro/, or to set up test data for a device check.
---

# Picfort device test (Maestro)

## Never touch family data

- Only the **test account** and the **test server** `picfort_test` on port **5051**
  (from the emulator: `10.0.2.2:5051`). Never the family's Picfort server (:5050) or real accounts.
- Credentials live in `~/.config/picfort/test.env` (outside the repo). Load them with
  `set -a; source ~/.config/picfort/test.env; set +a` and pass the variables straight into
  commands. Never print them or copy them into chat, files or commits.

## Before you start

1. Test server up: `curl -s -m 3 -o /dev/null http://localhost:5051/ || docker start picfort_test`
2. Debug app installed: `./gradlew :app:installDebug`
3. Maestro installed: `brew install mobile-dev-inc/tap/maestro`

## Running flows

```bash
./scripts/maestro-smoke.sh                                   # all flows, first device
DEVICE=emulator-5556 ./scripts/maestro-smoke.sh maestro/school.yaml   # one flow
SKIP_SERVER_CHECK=1 ./scripts/maestro-smoke.sh maestro/school-offline.yaml  # needs the server down
```

The script loads `test.env`, exports `MAESTRO_*` and checks the test server. Don't pass
credentials on the command line.

## Exploring the screen interactively

If the Maestro MCP tools (`mcp__maestro__*`) are available, use them:
`list_devices` → `inspect_screen` → `run` with inline YAML. Don't click with
`adb input tap` or `uiautomator dump` (guessed coordinates, sleeps). adb is fine for non-UI work:
install, logcat, `settings`, screen recording. If the MCP tools are missing, say so and ask for
them to be connected instead of silently using adb.

## Writing a flow

- One file per feature in `maestro/`, starting with a comment: what it checks, task numbers,
  "Test server only" if it changes data.
- `appId: com.github.ljarka.immich.android2`; start with `- runFlow: subflows/open-app.yaml`
  (`subflows/login.yaml` for a fresh install).
- Set up data with `maestro/scripts/test-data.js` (env such as `SCHOOL`, `EVENTS`, `PEOPLE`,
  `ALBUM`; see the comment at the top of the file). Add new switches there, not in the flow.
  The script talks only to `MAESTRO_PICFORT_SERVER_HOST` (localhost test server).
- Select by text, wait with `extendedWaitUntil` instead of fixed sleeps.
- Add the run command to the touched module's `AGENTS.md` under "Verification".

## Public repo

The repo is public. **Never commit Maestro or emulator screenshots**: they show the test
account's real user names. Mockups come from Paparazzi sample data only. Use placeholders
(`photos.example.com`, `test@example.com`) for hosts and accounts in docs and commits.
