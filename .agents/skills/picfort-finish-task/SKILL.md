---
name: picfort-finish-task
description: Finish a Picfort task (T-xxx) - run the Definition of Done checks, commit with the project's conventions, and record the task as done in docs/tasks and docs/STATUS.md. Use when a task's code is ready, before committing or pushing, or when asked to close, finish or mark a task done.
---

# Finish a Picfort task

## 1. Checks (all green before committing)

```bash
./scripts/check-modular-boundaries.sh
./gradlew ktlintFormat ktlintCheck
./gradlew testDebugUnitTest verifyPaparazziDebug lintDebug
```

- `lintDebug` is not in the root `AGENTS.md` checklist, but CI runs it: run it for every
  Android change.
- Server changes: `cd picfort-server && python -m unittest discover -s tests -t tests`.
- UI changed on purpose: `./gradlew :<module>:recordPaparazziDebug`, look at the new snapshots,
  commit them.
- Device check when the task's criteria ask for it: the `picfort-device-test` skill.

## 2. Docs

- `AGENTS.md` of every touched module: new files, entry points, verification commands
  (keep each under 80 lines).
- A new technical decision → a new ADR in `docs/decisions/` and a row in its `README.md` and in
  the ADR table of `docs/STATUS.md`.
- In the task file, tick the acceptance criteria that are met. Leave owner-only steps unticked
  and say so.

## 3. Privacy check (public repo)

Before committing, make sure the diff has no real Immich host, account names, family names,
credentials or emulator/Maestro screenshots. Use `photos.example.com` / `test@example.com`.
`scripts/check-no-secrets-in-diff.sh` catches only credentials, not names.

## 4. Commits

Conventional commits with the task number at the end:

```
feat(frame): the frame in a web browser, behind the server key (T-072)
fix(school): no flicker after editing with Saturday chosen (T-069)
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `ci`. Scope = module or area
(`timeline`, `wall`, `server`, `tasks`, …). Commit only when the owner asks; never push to
`main` without being asked.

## 5. Mark the task done (separate commit, after the code commit)

1. Task file: `**Status:** done (<short hash of the code commit>)`.
2. `docs/STATUS.md`: the task's row status → `` `done` ``.
3. Commit: `docs(tasks): T-xxx done (<hash>)`.

If the task cannot be fully checked (e.g. needs the owner's device), keep `in-progress` and write
what is left in the task file.

## 6. Leave the next task ready

If the work revealed follow-ups, add a `T-<next number>-<slug>.md` brief (status `draft` or
`ready`, see `docs/tasks/README.md`) and a row in `docs/STATUS.md`.
