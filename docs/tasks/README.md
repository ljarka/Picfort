# Task queue

This directory stores one implementation brief per task (`T-xxx-*.md`).

## Source of truth

- **Current order, status and priorities:** [`../STATUS.md`](../STATUS.md)
- **Architecture decisions behind tasks:** [`../decisions/`](../decisions/)

## Conventions

- File naming: `T-<number>-<slug>.md`
- Status values: `draft`, `ready`, `in-progress`, `done`
- `ready` means an agent can implement the task without additional product decisions.

When a task is completed, update its status in `docs/STATUS.md` and keep the task file as the implementation record.
