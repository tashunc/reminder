# Android App: Task CRUD + Drive Storage

## What this covers

The first working slice of the Android app (Build Order step 2 in
`CLAUDE.md`): sign in, and create/complete/delete tasks, persisted to the
user's Drive App Data folder. No alarms, widget, or Assistant integration
yet — those are separate build-order steps with their own doc files once
implemented.

## How it's implemented

- **`android/`** — standalone Gradle project (Kotlin + Jetpack Compose,
  Material 3), package `com.reminder.app`, `minSdk 26`.
- **Auth**: `auth/AuthManager.kt` wraps Google Identity Services'
  Authorization API (`Identity.getAuthorizationClient`) to request the
  `drive.appdata` OAuth scope. This doubles as sign-in for now — there's no
  separate identity/profile step, so the app doesn't yet show *which*
  account is signed in (follow-up, see `docs/drive-storage.md`).
  - `requestDriveAuthorization()` returns either `Granted(accessToken)` or
    `ConsentRequired(pendingIntent)`.
  - `MainActivity` launches the consent `pendingIntent` via
    `ActivityResultContracts.StartIntentSenderForResult` when required, and
    finishes the flow with `onAuthorizationConsentResult(intent)`.
- **Storage**: `data/DriveTaskStore.kt` talks to the Drive REST API v3
  directly (OkHttp + kotlinx.serialization) — find/create/read/update
  `tasks.json` in `appDataFolder`. Implements the optimistic-concurrency
  check described in `docs/sync.md` (compares `headRevisionId` before
  writing, throws `DriveConflictException` on mismatch).
- **Repository**: `data/TaskRepository.kt` holds the in-memory task list,
  exposes `loadTasks()` / `addTask()` / `setCompleted()` / `deleteTask()`,
  and implements the retry-once-on-conflict policy from `docs/sync.md`.
- **UI**: `ui/tasklist/TaskListScreen.kt` (list grouped by day, checkbox to
  complete, delete button, FAB to add) + `ui/tasklist/AddTaskDialog.kt`
  (title/description/day/time as text fields, recurrence as filter chips)
  + `ui/tasklist/TaskListViewModel.kt` (state holder, one `StateFlow` of
  `TaskListUiState`). `SignInScreen.kt` is shown until Drive access is
  granted.
- **Data model**: `data/Task.kt` mirrors `docs/data-model.md` exactly.

## Known gaps in this pass

- Day/time entry is plain text fields (`YYYY-MM-DD` / `HH:mm`), not a real
  date/time picker — fine for exercising the CRUD + Drive round-trip, needs
  replacing before this is user-facing.
- No pull-to-refresh / periodic background refresh yet (`docs/sync.md`
  describes the intended model; only "on sign-in" refresh exists so far).
- No offline handling — a failed Drive call surfaces as a snackbar error,
  nothing is queued for retry.
- Sign-in doesn't display the account email/name (see `docs/drive-storage.md`).

## Platform limitations

- Not buildable/testable in this environment — no Android SDK, Gradle, or
  JDK available here. The project has not been compiled; treat it as
  reviewed-by-inspection, not verified-by-build.
- Won't run end-to-end anywhere until the Google Cloud Console OAuth setup
  in `docs/drive-storage.md` is completed and a real Android OAuth client
  (package name `com.reminder.app` + your signing cert's SHA-1) exists.

## How to test manually

1. Complete the Google Cloud Console setup in `docs/drive-storage.md`
   (enable Drive API, OAuth consent screen, Android OAuth client with this
   project's `applicationId` and your debug keystore's SHA-1).
2. Open `android/` in Android Studio, let it sync Gradle (it will generate
   the wrapper if missing — this repo doesn't commit `gradlew`/`gradle-wrapper.jar`).
3. Run on a device/emulator with Google Play Services.
4. Tap **Sign in**, grant Drive access when prompted.
5. Tap **+**, add a task with a day (and optionally time/description/repeat).
6. Confirm it appears in the list grouped under that day.
7. Check the box to complete it, then delete it; confirm both actions
   persist (kill and reopen the app — the task list should reflect Drive's
   current `tasks.json`, not stale local state).
