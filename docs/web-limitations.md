# Web App Limitations

The web app is a **management dashboard**, not a reminder delivery system.
Do not attempt to make it behave like the Android alarm/notification system
— it structurally can't, for reasons below. These limits should stay
visible in the product (e.g. copy in the UI), not just in this doc.

## No reliable notification delivery

- Browser notifications only fire while a tab is open (or, with a service
  worker, while the browser process is running) — closing the browser or
  restarting the machine silently drops any scheduled reminder.
- There is no equivalent of Android's exact-alarm + full-screen-intent
  wake-the-device behavior on the web. A `setTimeout`-based or
  `Notification`-API reminder is best-effort only.
- We will still show browser notifications when a tab is open, as a
  convenience, but the UI must not imply this is a dependable alarm.

## No background sync when the tab/browser is closed

Unlike Android's WorkManager, there's no background execution once the
browser is closed. The web app only reflects Drive state while it's open
(see `docs/sync.md`).

## Drive token handling

The web client never holds a raw Drive access/refresh token in the browser.
Tokens are kept server-side (Next.js API route + session cookie); the
browser only ever talks to our own `/api/tasks` route. This avoids
exposing a token with Drive scope to XSS or browser extensions.

## What the web app is for

- Reviewing/editing the full task list and calendar/day view.
- Creating and editing tasks with a keyboard, in bulk, more comfortably than
  on a phone.
- Sign-in with the same Google account keeps it in sync (on refresh) with
  Android, Assistant, and Bixby-created tasks.

It is explicitly **not** the thing that wakes the user up at the right
time — that's Android's job.
