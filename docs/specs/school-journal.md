# School journal — product & technical spec

**Status:** draft for review (2026-11-17)  
**Decision:** [ADR 0017](../decisions/0017-school-journal-removable-module.md)  
**Tasks:** T-076 … T-082 (Phase 9 in [STATUS](../STATUS.md))  
**Server:** [school-server](https://github.com/ljarka/school-server), API `/api/v1` (contract:
`docs/api/openapi.yaml` in that repo; integration guide `docs/api/README.md`).

## 1. Goal

Show the children's school e-journal data (grades and messages, with what is new) inside Picfort, from
the family's own school-server. The integration lives in **one module** (`:feature:school-journal`)
that can be deleted without touching any other module except a few lines in `:app`.

Non-goals: sending messages, attachments download (the server only gives names), linking
journal children to Family members, anything the server doesn't expose (attendance, homework,
timetable — school-server "Planned").

## 2. Entry point & badge

- A **school building icon** (own vector drawable in the module) in the top app bar of
  **Family Wall** and **School**, placed before the Settings icon.
- **Red dot badge** (Material 3 `BadgedBox` + small `Badge`, `colorScheme.error`) when there
  is something new (§6). No number.
- Always visible, also when not configured (no badge then) — the screen it opens is where you
  configure it.
- Content description: "School journal" / "School journal, new items".
- Tap → `Screen.SchoolJournal`, pushed onto the current tab's stack (like `EventDetail`); Back
  returns to Wall/School.

## 3. Configuration (screen shown when not configured)

Fields: **Server address** (e.g. `192.168.1.10:5100` or `https://school.example.com`;
no scheme → `defaultSchemeFor()`: http on the local network, https elsewhere) and
**API key** (password field with show/hide, paste-friendly). Button **Connect**.

Connect does:
1. `GET /health` → not `{"status":"ok"}` or unreachable → "Can't reach the server at …".
2. `GET /api/v1/status` with `X-School-Key` →
   - `401` → "Wrong API key"; `429 TOO_MANY_WRONG_KEYS` → "Too many wrong keys, try again in N min" (`Retry-After`);
   - `CleartextNotAllowedException` → "Plain http only works on the home network; use https".
3. Success → save address + key (encrypted, `SecurePreferencesStore`), start the current
   user's "new" state (§4.6: everything already there counts as read / seen), open the content.

Later: overflow menu → **Server settings** (same form, prefilled, shows server version and
last sync) with **Disconnect** (confirm) that clears address, key, cursors, cached data.

Also shown (prefilled, with message "The API key is no longer valid") when any call returns
`401` with `WWW-Authenticate: School-Key`.

## 4. Content screen

```
┌ ← School journal                 ⟳  ⋮ ┐   ⟳ = Sync now; ⋮ = Mark all read, Show removed, Server settings
│ Last successful sync: 14:32 · next 14:47 │ status line → status sheet
│ [All] [Ola] [Kuba]                    │   child chips (only with ≥ 2 children)
│ (   Messages •   |   Grades •   )     │   two tabs; dot = new in that tab
│ …tab content…                         │
└───────────────────────────────────────┘
```

### 4.1 Status line, banners & status sheet (`GET /status`, `GET /health`)

- Status line, **always visible** under the app bar on every tab (owner request 2026-11-17):
  "Last successful sync: {lastSuccessfulSyncAt} ({relative}) · next {nextSyncAt}", e.g.
  "Last successful sync: today 14:32 (12 min ago) · next 14:47". Date shown when not today
  ("Mon 16 Nov, 14:32"); device time format; the relative part refreshes every minute.
  `null` → "No successful sync yet". When the last run failed (`lastSync.status` `error` /
  `auth_failed`), add "· last check failed" in the error color. Shown from the cached copy when
  offline. Older than 1 h → the whole line in the warning color (no separate banner).
  Tap → status sheet. Also shown in Server settings.
- Banners (one, highest wins):
  1. `auth == auth_failed` → "The server can't log in to the e-journal. Fix its login and restart it." (error color)
  2. `503 NO_DATA_YET` → "First check of the e-journal is still running."
  3. Unreachable → "Offline. Data from 14:32" (cached copy, read only).
- Status sheet: auth state; last sync (started, finished, `status`, `errorCode`,
  `itemsFetched`, `changesDetected`); last successful sync; next sync; counts (grades,
  messages); server version; server address.

### 4.2 Children (`GET /children`)

- Loaded on open. ≥ 2 children → chips **All** + one per child (`name`). Selection kept in
  preferences; passed as `child=<id>` to subjects, messages and changes.
- With **All**, every row shows the child's first name (from `childId`).
- `404 CHILD_NOT_FOUND` → reload children, fall back to All.

### 4.3 Messages tab (`GET /messages`, `GET /messages/{id}`)

- List newest first, paged (`limit=50`, `cursor` → `nextCursor`, infinite scroll).
  Row: sender, subject, date, paperclip if `hasAttachments`; unread = bold + dot.
- Filter chip **Unread only** (from the phone's read state, §4.6). Overflow **Show removed** (`includeDeleted=true`):
  removed rows struck through with "Removed from the e-journal".
- Long-press / swipe: **Mark as read / unread**.
- Overflow **Mark all as read** (for the selected child, or all) → snackbar "Marked N messages".
- Tap → message screen (`Screen.SchoolJournalMessage(id)`): subject, sender, sent at, child, body from
  `bodyHtml` via `AnnotatedString.fromHtml` (links open in the browser; fallback `bodyText`),
  attachment names with a note "Open in the e-journal to download". Opening marks it read;
  action **Mark as unread**. `404` → back to the list, row removed.
- Read / unread is **per Immich user, kept on the phone** (§4.6); one parent reading a message
  does not clear it for the other.

### 4.4 Grades tab (`GET /grades/subjects`)

- Semester chips: **Semester 1 · Semester 2 · Whole year** (`semester` param or none);
  default = current semester (the one of the newest grade).
- One view, **by subject**: card per subject: name, weighted `average` ("—" when null), row of
  grade chips (`value`, colored by `numericValue` 1–6, neutral when null; small weight number),
  oldest → newest. Non-regular kinds (`proposed_semester`, `semester`, `proposed_final`, `final`)
  shown as labelled values on the card ("Proposed: 4", "Final: 5"). Subjects with a new grade first.
- Grades with a change event after the seen cursor carry a **New** marker (dot on the chip);
  grade ids come from `GET /changes?after=<gradesSeenCursor>&type=grade`.
- No separate "Recent" list and no filters (owner decision 2026-11-17, simplification).
- Tap grade → sheet: value, numeric value, weight, category, kind, counts to average, teacher,
  comment, issued on, first seen, last changed, removed at.

### 4.5 Sync now (`POST /sync`)

- ⟳ → `202` or `409 SYNC_IN_PROGRESS` → progress indicator, poll `/status` every 5 s (max
  2 min) until `lastSync.status != running`, then reload the current tab.
- `409 AUTH_FAILED` → auth banner. `429 SYNC_TOO_SOON` → snackbar "Checked recently, try again
  in N min" (`Retry-After`).
- Pull-to-refresh only reloads from the server (no `/sync`).

### 4.6 Read and seen state (per Immich user, on the phone)

Owner decision 2026-11-17: each Immich user has their own "read" and "seen", stored on the phone
inside the module. The server's shared read state is **not used** (ADR 0017).

- Key: Immich user id from `AccessTokenStore.userId` (`:core:network`) + the journal server
  address. Another user logging in on the same phone gets their own state.
- File `filesDir/school-journal/state-<hash(address)>-<userId>.json`:
  `ReadState(watermark: Instant, readIds: Set<String>, unreadIds: Set<String>, gradesSeenCursor: Long,
  notifyCursor: Long)`.
- `isRead(m) = m.id in readIds || (m.sentAt <= watermark && m.id !in unreadIds)`.
- Start (first connect, or first time this user opens it): `watermark = now`,
  `gradesSeenCursor = status.lastEventId` — history is neither unread nor new.
- Mark read: `readIds += id`, `unreadIds -= id`. Mark unread: `readIds -= id`; if
  `sentAt <= watermark` then `unreadIds += id`.
- Mark all read: All children → `watermark = now`, both sets cleared. One child → that child's
  unread ids go to `readIds`, its ids leave `unreadIds`.
- Unread messages = `GET /messages?since=<watermark>` (paged, `child`) minus `readIds`, plus
  `GET /messages/{id}` for each id in `unreadIds` (a `404` drops the id).
- Housekeeping: when `watermark` moves, ids older than it leave `readIds`. Sets stay small.
- Not used from the server: `PUT|DELETE /messages/{id}/read`, `POST /messages/read-all`,
  `unread=true`, `read`/`readAt`, `counts.unreadMessages`. Not shown to the user; listed in
  `feature/school-journal/AGENTS.md` as deliberate.
- Lost on app reinstall / data clear (then it starts again: everything read) and not shared
  between two devices of the same person — accepted.

### 4.7 Notifications (owner decision 2026-11-17)

- Background job in the module (WorkManager, every 15 min, network required) reads
  `GET /changes?after=<notifyCursor>`; `notifyCursor` is per Immich user (in the §4.6 state file),
  started at `status.lastEventId` on connect — no flood of history.
- Notifies: **new grade** ("New grade · Ola" / "Mathematics 5 · Test"), **changed grade value**
  ("Grade changed · Ola" / "Mathematics 4 → 5"), **new message** ("Message · Ola" /
  "{sender}: {subject}") unless already read on this phone. Not: removals, other changes.
- Channel "School journal"; grouped per child; more than 3 for one child in a run → one summary.
  Quiet hours: Android's Do Not Disturb / channel settings (no own setting).
- Tap → opens the message, or the Grades tab. Reading a message in the app removes its notification.
- Server settings: switch **Notifications** (on by default); permission `POST_NOTIFICATIONS`
  asked on Connect (Android 13+).

### 4.8 Server endpoints in use

| Endpoint | Used for |
|---|---|
| `GET /health` | Connect |
| `GET /api/v1/status` | Status line & sheet, Connect, badge, Sync now |
| `GET /api/v1/children` | Child chips, names |
| `GET /api/v1/grades/subjects` | Grades tab |
| `GET /api/v1/messages`, `GET /api/v1/messages/{id}` | Messages tab, message screen, unread |
| `GET /api/v1/changes` | Badge, New markers, notifications |
| `POST /api/v1/sync` | Sync now |
| `GET /api/v1/grades` | Not used: the subjects endpoint already holds every grade |
| `PUT\|DELETE …/read`, `POST …/read-all` | Not used: read state per Immich user on the phone (§4.6) |

## 5. Errors

One `SchoolJournalError` mapping from the `{"error":{"code","message"}}` body; switch on `code`,
never show `message` to the user (logs only).

| HTTP / code | User sees |
|---|---|
| unreachable / timeout | Offline banner with cached data |
| 400 `BAD_REQUEST` | "Something went wrong" (bug; logged) |
| 401 `UNAUTHORIZED` | Configuration screen "The API key is no longer valid" |
| 404 `NOT_FOUND` | Item removed locally |
| 404 `CHILD_NOT_FOUND` | Children reloaded, All selected |
| 409 `SYNC_IN_PROGRESS` / `AUTH_FAILED` | See §4.5 / auth banner |
| 429 `TOO_MANY_WRONG_KEYS` / `SYNC_TOO_SOON` | "Try again in N min" |
| 503 `NO_DATA_YET` / `STORAGE_UNAVAILABLE` | Banner "First check still running" / "Server storage problem" |
| unknown code | Generic error, logged |

Unknown JSON fields and enum values are ignored (`ignoreUnknownKeys`, enums as strings with an
`Unknown` fallback) — the server's compatibility promise.

## 6. "Something new" (badge) rules

All per current Immich user (§4.6).

- `hasNew = hasUnreadMessages || hasUnseenGradeChanges`.
- `hasUnreadMessages` = `unreadIds` not empty, or `GET /messages?since=<watermark>&limit=100`
  has an id not in `readIds`.
- `hasUnseenGradeChanges` = `GET /changes?after=<gradesSeenCursor>&type=grade&limit=1` returns an
  item (asked only when `status.lastEventId > gradesSeenCursor`).
- `gradesSeenCursor`: advanced to the newest known event id when the Grades tab has been shown.
- Messages clear the badge by being read on this phone.
- Tab dots: Messages = unread messages; Grades = unseen grade changes.
- Refresh: while Wall or School is on screen (`STARTED`): immediately and every 5 min
  (`pollEvery`); also when returning from the journal screen. No network when not configured.
  Errors keep the last known value (offline → no badge change).

## 7. Architecture (see ADR 0017)

- Module `:feature:school-journal`, namespace `com.github.ljarka.picfort.feature.schooljournal`, package
  `com.github.ljarka.picfort.ui.schooljournal`. Depends on `:core:model` (`pollEvery`), `:core:ui`
  (theme, components), `:core:network` (`SecurePreferencesStore`, `CleartextPolicyInterceptor`,
  `defaultSchemeFor`, `@AppScope`). **No changes to any `:core:*` module.**
- Own `OkHttpClient` (never the app's: that one adds Immich/Picfort credentials) with
  `CleartextPolicyInterceptor` and an interceptor adding `X-School-Key`. kotlinx.serialization.
- Files: `SchoolJournalApi` (every endpoint), `SchoolJournalDtos`, `SchoolJournalError`, `SchoolJournalConfigStore`,
  `SchoolJournalRepository` (data + cache), `SchoolJournalBadgeRepository` (singleton `StateFlow<Boolean>`),
  `SchoolJournalTopBarAction` (+ `SchoolJournalBadgeViewModel`), `SchoolJournalScreen` / `SchoolJournalViewModel`,
  `SchoolJournalSetup`, `MessageScreen`, `GradesTab`, `MessagesTab`, `SchoolJournalNotifyWorker`.
- Offline copy: `filesDir/school-journal/cache.json` (status, children, last subjects per child +
  semester, first messages page), tied to the server address; deleted on Disconnect. No Room
  (`:core:database` untouched).
- Prefs (encrypted): `schoolJournal.url`, `schoolJournal.key`, `schoolJournal.child`. Read / seen
  state per Immich user in `filesDir/school-journal/state-*.json` (§4.6); Disconnect deletes the folder.
- Uses `AccessTokenStore.userId` (`:core:network`) for the Immich user id.
- Public contract (only these are used by `:app`):
  - `SchoolJournalTopBarAction(onClick: () -> Unit)`
  - `SchoolJournalScreen(onBack: () -> Unit, onOpenMessage: (String) -> Unit)`
  - `SchoolJournalMessageScreen(messageId: String, onBack: () -> Unit)`
  - `SchoolJournalNotifyScheduler.start()` (from `PicfortApplication.onCreate`) and
    `EXTRA_OPEN_SCHOOL_JOURNAL` (read by `MainActivity` to open the screen from a notification)
- Wall and School get a generic slot `topBarActions: @Composable RowScope.() -> Unit = {}` —
  they never know what is in it. `:app` passes `{ SchoolJournalTopBarAction { navigate(Screen.SchoolJournal) } }`.

## 8. Testing

- Unit: DTO parsing from contract fixtures (copied examples of every schema, incl. unknown
  fields/enums), error mapping, read state (§4.6: start, read/unread around the watermark, read
  all per child, two Immich users on one phone), badge rules, New markers, notification filter, cursor handling,
  config validation — with a fake OkHttp `Interceptor`/`MockWebServer`-like transport.
- Paparazzi: setup (empty, errors), content (both tabs, All vs one child, status line fresh /
  stale / failed, banners, offline),
  message screen, grade sheet, top bar icon with/without badge, dark mode.
- Device (Maestro): against a **fake school-server** (`maestro/scripts/fake-school-server.py`,
  stdlib only, `:5101`, neutral sample data) — never the family's real
  school-server, never commit screenshots of real data.

## 9. Removal

See ADR 0017 "How to remove". Target: delete the folder + ≤ 10 lines in `:app` + settings/build
files; `./scripts/check-modular-boundaries.sh` and all tests stay green.
