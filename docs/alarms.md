# Time-Based Reminders: Exact Alarms

## What this covers

Build Order step 3: for a task with `time` set (see `docs/data-model.md`),
Android schedules an exact alarm that rings like a native alarm clock —
takes over the screen even if the device is locked. All-day tasks
(`time == null`) are a separate feature (WorkManager-based randomized
prompts) with its own doc once implemented.

## How it's implemented

- **`alarm/AlarmScheduler.kt`** — schedules/cancels exact alarms via
  `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, ...)`, keyed by a
  `PendingIntent` derived from the task ID. Refuses to schedule (returns
  `false`) if the exact-alarm permission isn't granted, or the computed
  trigger time is already in the past.
- **`alarm/AlarmReceiver.kt`** — `BroadcastReceiver` that fires when the
  alarm goes off; hands off to `AlarmNotifier`.
- **`alarm/AlarmNotifier.kt`** — creates the `task_alarms` notification
  channel (`IMPORTANCE_HIGH`) and posts a notification with
  `setFullScreenIntent(...)` pointing at `AlarmActivity`.
- **`alarm/AlarmActivity.kt`** — full-screen UI shown when the alarm fires.
  Uses `setShowWhenLocked(true)` / `setTurnScreenOn(true)` (API 27+, with a
  `WindowManager.LayoutParams` flag fallback for API 26) to display over the
  lock screen and wake the device. Shows the task title/description and a
  Dismiss button.
- **`alarm/BootReceiver.kt`** — reschedules every future alarm after a
  device reboot (`AlarmManager` alarms don't survive one). See "Known
  limitation" below.
- **`data/TaskRepository.kt`** — reconciles alarm scheduling against the
  live task list after every fetch and write (`reconcileAlarms()`), so
  alarms stay correct even for tasks changed by another client (web,
  Assistant, Bixby) and get cancelled when a task is completed or deleted.
- **UI**: `ui/tasklist/TaskListScreen.kt` shows a banner (via
  `TaskListViewModel.uiState.exactAlarmPermissionGranted`) when the
  exact-alarm permission isn't granted, with a button that calls
  `MainActivity.requestExactAlarmPermission()`.

## Permissions

Three separate permissions are involved, each with different Android
version behavior:

1. **`SCHEDULE_EXACT_ALARM`** (manifest-declared) — on Android 12–13
   (API 31–32) this is granted automatically at install. On Android 14+
   (API 33+) the user must grant it explicitly via
   `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, which
   `MainActivity.requestExactAlarmPermission()` launches. `AlarmScheduler.canScheduleExactAlarms()`
   checks `AlarmManager.canScheduleExactAlarms()` (always true below API 31).
2. **`USE_FULL_SCREEN_INTENT`** (manifest-declared) — on Android 14+ this
   is auto-granted but user-revocable; `AlarmNotifier` checks
   `NotificationManager.canUseFullScreenIntent()` and falls back to a
   plain heads-up notification (still tappable to open `AlarmActivity`) if
   it's been revoked.
3. **`POST_NOTIFICATIONS`** (runtime, API 33+) — without this, *no*
   notification shows at all, full-screen or otherwise. `MainActivity`
   requests it once at startup via `ActivityResultContracts.RequestPermission`.

## Known limitations

- **Boot rescheduling depends on a silent Drive re-authorization.**
  `BootReceiver` can't show any UI, so it only works if
  `AuthManager.requestDriveAuthorization()` returns `Granted` without a
  consent prompt (i.e., the scope grant is still valid and Google can
  silently re-issue a token for it). If that fails for any reason, alarms
  for that account are not rescheduled until the user reopens the app
  (which reconciles them again via `TaskRepository`). This has not been
  device-tested — treat it as the most likely thing to need rework.
- **No recurrence advancement yet.** A task with `recurrence` set to
  `daily`/`weekly`/`monthly` only fires once, on its stored `day`/`time`.
  Advancing `day` to the next occurrence after it fires (or after
  dismissal) is not implemented — tracked as follow-up work, not silently
  assumed to work.
- **`PendingIntent` request codes are derived from `taskId.hashCode()`.**
  A hash collision between two task IDs (astronomically unlikely with
  UUIDv4, but not impossible) would let one task's alarm cancel/overwrite
  another's.
- Not build-verified in this environment — no Android SDK available (see
  `docs/android-app.md`).

## Manual test

1. Complete the setup in `docs/drive-storage.md`, sign in, grant the exact
   alarm permission when the in-app banner prompts for it (or via
   Settings → Apps → Reminder → Alarms & reminders on API 33+).
2. Grant the notification permission prompt on first launch.
3. Add a task with a `time` a minute or two in the future.
4. Lock the device (or just wait with the app backgrounded) and confirm
   the full-screen alarm UI appears at the scheduled time, screen on, over
   the lock screen.
5. Tap Dismiss; confirm the notification clears and the activity finishes.
6. Add another timed task, then reboot the device (or emulator); after it
   comes back up, wait for the reboot-triggered reschedule and confirm the
   alarm still fires at the original time.
7. Mark a timed task complete before it fires; confirm no alarm rings for
   it (cancelled via `reconcileAlarms()`).
8. Revoke the exact-alarm permission via Settings while the app is open;
   background and foreground the app; confirm the in-app banner appears.
