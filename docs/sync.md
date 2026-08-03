# Sync Model

## Summary

There is no real-time sync. Google Drive's REST API has no equivalent of a
Firestore snapshot listener, so cross-client updates are **pull-based**:
each client fetches the current `tasks.json` and only sees changes made by
other clients at that point.

## When clients fetch

- **Android**: on app foreground/resume, on manual pull-to-refresh, and via
  a periodic WorkManager job (interval TBD in settings, default suggestion:
  every 15–30 min) so alarms/widget/notifications reflect tasks added
  elsewhere without the user opening the app.
- **Web**: on page load, on manual refresh, and on window focus (a tab
  regaining focus re-fetches).
- **Assistant / Bixby**: these write directly to `tasks.json` via the same
  Drive API when a task is created by voice — they don't need to "sync" in
  the read sense, they just perform the same read-modify-write as any other
  client.

## Write model: read-modify-write with optimistic concurrency

Because every write replaces the whole `tasks.json` file, two clients
editing at nearly the same time can clobber each other. To guard against
silent data loss:

1. Before writing, the client holds the Drive file's current `headRevisionId`
   (captured at the most recent read).
2. On write, use the Drive API's `If-Match`/revision check
   (`files.update` supports optimistic concurrency via the file's `version`
   field — check current `version` against the one read before writing).
3. If the check fails (someone else wrote in between): **re-fetch, re-apply
   the local change on top of the fresh data, retry the write once.** Don't
   silently overwrite; don't silently drop the local change.
4. If it fails twice in a row, surface a "couldn't sync, try again" state
   rather than looping indefinitely.

## Known limitation

This is last-write-wins at the field level after the merge-retry above —
there's no per-field CRDT-style merge. For a single-user personal task app
this is an acceptable trade-off (conflicts mean the *same account* editing
from two devices within seconds of each other), but it should not be
presented to users as guaranteed lossless multi-device concurrent editing.

## Manual test

1. Sign in on two clients (e.g. Android + web) with the same account.
2. On client A, add a task. On client B, before refreshing, edit a
   *different* existing task and save.
3. Refresh both. Confirm both changes are present in `tasks.json` (no
   clobber) — this exercises the read-modify-write retry path.
4. To see a real conflict, add a delay: pause client B's Drive request
   (e.g. breakpoint) after its read but before its write; edit and save the
   same task from client A in between; let client B's write proceed and
   confirm it retries rather than overwriting A's change.
