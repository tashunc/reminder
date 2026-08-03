# Changelog

All notable functional changes to the project, in plain language.

## 2026-08-03

- **Project kicked off.** Defined the app: Android (Kotlin/Compose) and web
  (Next.js) clients sharing one account's task list, with Android handling
  real reminders (exact alarms, all-day prompts, home screen widget,
  Google Assistant / Bixby voice creation) and web as a management
  dashboard.
- **Storage decision: Google Drive instead of Firebase.** Tasks are stored
  as a single `tasks.json` file in each user's own Google Drive App Data
  folder (hidden, app-private storage), rather than in a Firestore database
  we'd have to run. Signing in with Google is both the login and the Drive
  access grant. Trade-off: no real-time cross-device sync — clients fetch
  the latest data on open/refresh instead of getting instant pushes. See
  `docs/drive-storage.md`, `docs/data-model.md`, `docs/sync.md`.
- **Android app scaffolded: sign-in + task list.** Added the `android/`
  Gradle project with a working (not yet build-verified — no Android SDK in
  this environment) first pass: Google sign-in via Drive OAuth consent,
  a task list grouped by day with add/complete/delete, all persisted to
  `tasks.json` in the user's Drive App Data folder. See `docs/android-app.md`.
