# T-084 — Family Wall: reactions and a notification for the first one

**Status:** done  
**Component:** Picfort server (`wall.py`, `app.py`, schema v2), Android (`:feature:wall`, `:app` one line)  
**Depends on:** T-001, T-002. ADR [0018](../decisions/0018-wall-reaction-notifications.md).
Owner request 2026-11-18: react to wall messages like in Slack (heart, thumbs up), and the author
gets a notification at the first reaction.

## Summary

Anyone can react to any wall post (text or photo) with one of six quick reactions:
❤️ 👍 😂 😮 😢 🎉. Each person can add each emoji once; tapping it again takes it back. The author
gets **one** notification per post, for its first reaction by someone else.

## Design

### Server (`wall.db` schema 1 → 2, migration per ADR 0016)
- Table `reactions (post_id → posts ON DELETE CASCADE, user_id, user_name, emoji, created)`,
  primary key `(post_id, user_id, emoji)`; posts get `first_reaction_at / _by / _emoji`.
- Every post in `GET /wall` (and every post answer) has `reactions`:
  `[{emoji, count, user_ids, names}]` in the fixed emoji order, people by time.
- `PUT /wall/<id>/reactions {"emoji"}` adds mine (idempotent), `DELETE /wall/<id>/reactions?emoji=`
  removes mine; both answer the post. Unknown emoji → 400, unknown post → 404.
- The first reaction by someone other than the author sets `first_reaction_*` once; it is never
  reset (removing and re-adding does not notify again; the author's own reactions don't count).
- `GET /wall/first-reactions?since=<ISO>` → `{items: [{post_id, text, has_photo, at, by, emoji}]}`:
  the caller's posts whose first reaction came after `since`, oldest first, at most 50.

### Android
- Text card (124 dp high): the text, then a line "❤️ 2  👍 1". Photo tile: the same in a dark pill
  top left. Opening a message (dialog) or a photo (viewer) shows the reaction bar: all six emojis
  as chips with counts, mine highlighted, and "❤️ Tata, Ola · 👍 Babcia" below.
- A tap toggles mine at once (`withReactionToggled`) and then shows the server's answer; on error
  the post goes back and a snackbar says "Couldn't react".
- `WallReactionNotifyWorker` (`wall-reactions-notify`, periodic 15 min, network) reads
  `first-reactions?since=<cursor>`; the cursor is per Immich user in `SharedPreferences`
  (`wall_reactions`), started at "now" on the first run, moved to the newest `at` after a run.
  Notification "Tata reacted ❤️" / the post text (or "Your photo"), channel "Family Wall reactions",
  tap opens the app. Errors: no notification, try next time.
- `PicfortApplication.onCreate` calls `wallReactionNotifyScheduler.start()`.

## Tests

- Server (`test_wall_reactions`): grouping and order, idempotent add, removal only mine, bad emoji
  and post, first reaction once and only by someone else, only my posts, cascade on delete,
  v1 → v2 migration keeps posts.
- Android: `WallReactionsTest` (parsing, toggling, summary, notification text, cursor format),
  snapshots `wall`, `wall_reaction_bar`.
- Device: `maestro/wall-reactions.yaml` (react, count on the card, take back). Notification checked
  on the test server with a reaction inserted as another user (2026-11-18).

## Done when

- Family members react to wall posts, everyone sees who reacted, and the author hears about the
  first reaction within ~15 minutes without opening the app.
