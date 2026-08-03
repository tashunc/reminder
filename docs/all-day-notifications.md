# All-Day Reminders: Randomized WorkManager Prompts

## What this covers

Build Order step 4: for a task with no `time` set (see `docs/data-model.md`),
Android fires a handful of randomized full-screen check-in prompts spread
across the day, instead of a single exact alarm. Time-based tasks are a
separate feature (`docs/alarms.md`) — a task is either timed or all-day,
never both.

## How it's implemented

- **`allday/AllDayPromptScheduler.kt`** — for tasks due *today* with
  `time == null` and `completed == false`, splits the 09:00–21:00 active
  window (hardcoded for now — see Known limitations) into
  `DEFAULT_PROMPTS_PER_DAY` (3) equal buckets and picks one random moment
  per bucket, then enqueues a `OneTimeWorkRequest<AllDayPromptWorker>` per
  moment via `WorkManager.enqueueUniqueWork(..., ExistingWorkPolicy.REPLACE)`.
  `scheduleTodayIfNeeded()` only actually does this once per calendar day
  (tracked in `SharedPreferences`), so it's safe to call on every app
  resume as well as from the daily fallback worker below.
- **`allday/AllDayPromptWorker.kt`** — the `CoroutineWorker` that actually
  fires: reads the task id/title/description out of its input `Data` and
  posts a notification via `AllDayPromptNotifier`. Needs no network access
  itself — all context was baked in at scheduling time.
- **`allday/AllDayPromptNotifier.kt`** — creates the `all_day_prompts`
  notification channel (`IMPORTANCE_HIGH`) and posts a full-screen-intent
  notification. **Reuses `AlarmActivity`** (from `docs/alarms.md`) for the
  actual full-screen UI — the two features show an identical
  title/description/Dismiss screen, so there's no separate all-day
  activity class.
- **`allday/DailyScheduleWorker.kt`** + **`allday/AllDayPromptModule.kt`**
  — a periodic WorkManager job (`schedulePeriodicAllDayPromptWork()`,
  registered from `ReminderApplication.onCreate()`) that runs once daily,
  shortly after midnight, as a fallback for days the app is never opened.
  It just fetches tasks via `TaskRepository`, which reconciles all-day
  prompt scheduling as a side effect of loading (see below) — the worker
  itself has no scheduling logic of its own.
- **`data/TaskRepository.kt`** — `reconcileSchedules()` runs after every
  Drive fetch/write: it diffs the previous vs. new task list to cancel
  today's remaining prompts for any task that stopped qualifying
  (completed, deleted, given a time, or moved off today), then calls
  `AllDayPromptScheduler.scheduleTodayIfNeeded()` for whatever's current
  (a no-op after the first call each day).

## Why WorkManager instead of AlarmManager here

All-day prompts don't need to-the-second precision the way an alarm does,
and WorkManager's queue survives a reboot automatically (unlike
`AlarmManager`, which needed the custom `BootReceiver` in `docs/alarms.md`)
— one less thing to get wrong.

## Known limitations

- **Active window (09:00–21:00) and prompt count (3) are hardcoded.**
  Build Order step 9 ("Polish: settings screen... notification count per
  day, quiet hours") will make both configurable; `DEFAULT_PROMPTS_PER_DAY`
  in `AllDayPromptScheduler.kt` is the one constant to change meanwhile.
- **The daily fallback worker has the same silent-reauth dependency as
  `BootReceiver`** (`docs/alarms.md`) — if the cached Drive grant can't be
  silently refreshed from a background worker, that day's prompts for
  tasks added/changed without the app being opened won't be scheduled
  until the user opens the app.
- **No "mark done" action from the prompt itself** — Dismiss just closes
  the prompt; the task stays incomplete and will get prompted again later
  that day (or the next, if still incomplete and re-assigned to that day).
  Marking complete requires opening the app. Deliberately out of scope for
  this pass — not requested by the spec, and adding it means giving
  `AlarmActivity` its own sign-in/consent flow.
- Not build-verified in this environment — no Android SDK available (see
  `docs/android-app.md`).

## Manual test

1. Complete `docs/drive-storage.md` setup, sign in.
2. Add a task for today with **no** time set.
3. Confirm (via `adb shell dumpsys jobscheduler` or WorkManager's
   in-app inspector/logs) that 3 `AllDayPromptWorker` jobs get enqueued
   with distinct initial delays spread across the remaining part of
   09:00–21:00.
4. Wait for (or fast-forward, e.g. via `adb shell am broadcast` tricks /
   shortening the window in code for testing) one to fire; confirm the
   full-screen prompt appears with the task's title/description.
5. Mark the task complete; confirm its remaining prompts for today get
   cancelled (no further notifications for it).
6. Add a second all-day task for today, then force-close and reopen the
   app; confirm it does **not** get a fresh set of random times (the
   once-per-day guard holds) — its originally scheduled prompts should
   still be the ones that fire.
