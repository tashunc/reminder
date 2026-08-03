# Reminder App

## Project Overview
Cross-platform reminder/task app with two native clients sharing one backend:
- **Android app**: Kotlin + Jetpack Compose
- **Web app**: Next.js (React)
- **Backend/storage**: no server — tasks are stored as a JSON file (`tasks.json`) in each user's Google Drive **App Data folder** (hidden, app-private storage inside their own Drive), accessed directly via the Drive REST API v3
- **Auth**: Google Sign-In (OAuth 2.0) on both platforms, requesting the `drive.appdata` scope — the signed-in Google account *is* the account, no separate user database
- **Sync**: no real-time push (Drive has no equivalent of Firestore listeners). Clients fetch the latest `tasks.json` on app open, pull-to-refresh, and periodically in the background. Any client with write access updates the same file, so changes propagate on next fetch — not instantly.

## Core Functionality
- **Add task**: title, description (optional), assigned day, optional specific time, recurrence (optional)
- **Time-based reminder**: if a specific time is set, Android schedules an exact alarm (AlarmManager + full-screen intent, like a native alarm clock)
- **All-day reminder**: if no specific time is set, Android fires 2–3 randomized full-screen notification prompts per day (count configurable in settings), via WorkManager
- **Android home screen widget**: built with Glance, shows tasks grouped by day, displaying task name + time (if set)
- **Voice assistant task creation**:
  - Google Assistant via App Actions (built-in intent, e.g. `CREATE_TASK`)
  - Bixby via a separate Bixby Capsule (Bixby Studio project, calls the same backend API) — its own workstream, not shared code with the Android app
- **Web app**: task CRUD, calendar/day view, Google Sign-In, browser notifications while a tab is open (explicitly NOT a reliable delivery channel — framed as a management dashboard, not the alarm system)
- **Sync**: any task created on Android, web, Assistant, or Bixby writes to the same `tasks.json` in Drive App Data and appears on all clients tied to that account on next fetch

## Data Model (`tasks.json` in Drive App Data folder)
See `docs/data-model.md` for the authoritative schema.

## Build Order
1. Google Cloud project setup: enable Drive API, configure OAuth consent screen, create OAuth client IDs (Android + Web) — **requires manual steps in Google Cloud Console under the user's account**; document exact steps in `docs/drive-storage.md`
2. Android: Google Sign-In (`drive.appdata` scope) + Drive-backed `TaskRepository` (read/write `tasks.json`) + task CRUD UI (Compose)
3. Android: AlarmManager exact-alarm scheduling + full-screen alarm activity
4. Android: WorkManager randomized notification scheduling for all-day tasks
5. Android: Glance home screen widget
6. Android: App Actions integration for Google Assistant
7. Web: Next.js app with Google Sign-In + task CRUD + calendar view, Drive-backed via Next.js API routes (keep Drive access token server-side, not exposed to the browser)
8. Bixby Capsule (separate project) calling the same Drive-backed API
9. Polish: settings screen (notification count per day, quiet hours, etc.)

## Documentation Requirement — Follow for Every Change
This is a standing rule for the whole project, not a one-time task:
- Maintain a `/docs` folder with one markdown file per major feature area (e.g. `docs/alarms.md`, `docs/widget.md`, `docs/sync.md`, `docs/assistant-integration.md`, `docs/data-model.md`).
- Every time you add, change, or remove a functionality, update the corresponding doc file in the same commit/change — describe what the feature does, how it's implemented (files touched, key classes/functions), any platform limitations, and how to test it manually.
- If a change introduces a new feature area, create its doc file at the same time.
- Keep a top-level `docs/CHANGELOG.md` and append a dated entry for every functional change, in plain language (what changed, why, what a user would notice).
- Before marking any task "done," confirm the relevant doc file(s) and changelog entry are updated — treat this as part of the definition of done, not an optional cleanup step.
- Keep this `CLAUDE.md` file itself updated if the architecture, tech stack, or build order changes.

## Constraints to Respect
- Android exact alarms require `SCHEDULE_EXACT_ALARM` permission handling for Android 12+ — implement the permission request/fallback flow.
- Full-screen intents require `USE_FULL_SCREEN_INTENT` permission and correct notification channel importance.
- Web cannot replicate native alarm behavior — do not attempt to fake reliability there; document this limitation clearly in `docs/web-limitations.md`.
- Bixby Capsule code lives in its own directory/project structure, not mixed into the Android module.
- No backend server/database of our own: all persistence is the user's own Drive App Data folder via the Drive REST API, authenticated with their Google Sign-In OAuth token (`drive.appdata` scope). Never store the Drive access/refresh token outside secure platform storage (Android: EncryptedSharedPreferences/Keystore-backed; Web: server-side session, never localStorage).
- No real-time sync: document the "fetch on open / pull-to-refresh / periodic background poll" model clearly in `docs/sync.md`, including the risk of last-write-wins conflicts when two clients edit `tasks.json` concurrently (mitigate with Drive's file `version`/ETag on write, retry on conflict).
- Google Cloud Console / OAuth client creation cannot be done by Claude — those steps require the user's own Google account. Always leave a clear manual checklist in `docs/drive-storage.md` for these steps rather than attempting to automate them.
