# Home Screen Widget (Glance)

## What this covers

Build Order step 5: an Android home screen widget, built with
[Glance](https://developer.android.com/jetpack/androidx/releases/glance),
showing tasks grouped by day with task name + time (if set).

## How it's implemented

- **`widget/TaskWidget.kt`** — the `GlanceAppWidget`. Renders straight from
  a JSON snapshot stored in its own Glance state (`PreferencesGlanceStateDefinition`,
  key `KEY_TASKS_JSON`) — **it never talks to Drive or `TaskRepository`
  directly**. Filters out completed tasks, sorts by day then time, groups
  under a bold day header, and caps the list at `MAX_WIDGET_ROWS` (12) so a
  long list can't blow past what a widget can reasonably render. Tapping
  anywhere on the widget launches `MainActivity` (`actionStartActivity`).
- **`widget/TaskWidgetReceiver.kt`** — the thin `GlanceAppWidgetReceiver`
  the manifest points at; just wires `TaskWidget()` up as the provider.
- **`widget/TaskWidgetUpdater.kt`** — the write side. Serializes the given
  task list to JSON, writes it into every placed widget instance's Glance
  state (`GlanceAppWidgetManager.getGlanceIds`), then calls
  `TaskWidget().updateAll()` to force a redraw. No-ops if no widget is
  currently placed on any home screen.
- **`data/TaskRepository.kt`** — takes an optional `widgetUpdater:
  TaskWidgetUpdater?` constructor param. `updateTasksAndReconcile()` (the
  shared tail of both `refetch()` and `applyWrite()`) calls
  `widgetUpdater?.update(newTasks)` after updating in-memory `tasks` and
  reconciling alarms/all-day prompts — so the widget refreshes on every
  Drive fetch or write, from every entry point (app foreground, alarm/all-day
  reconciliation, `BootReceiver`, `DailyScheduleWorker`), the same way
  scheduling reconciliation already does. This is also why
  `updateTasksAndReconcile` had to become a `suspend fun`.
- **`res/xml/task_widget_info.xml`** — `AppWidgetProviderInfo`: 3x2 min
  cell size, resizable both directions, home-screen category only.
- **`res/layout/glance_widget_loading.xml`** — the mandatory classic
  `RemoteViews` `initialLayout` Glance widgets still need for the brief
  window before Compose content takes over; kept as an empty `FrameLayout`
  since `TaskWidget` renders almost immediately from cached state.
- **`AndroidManifest.xml`** — registers `TaskWidgetReceiver`, not exported,
  listening for `APPWIDGET_UPDATE`, pointing at `task_widget_info.xml`.

## Why a Glance-state snapshot instead of live Drive reads

Glance widget content is rendered by the system (via `RemoteViews`) on its
own update cycle, not something we can await inside — it's not a place to
kick off a network fetch. Piggybacking on `TaskRepository`'s existing
fetch/write cycle keeps this consistent with alarms and all-day prompts:
one code path (`updateTasksAndReconcile`) is the single point where "the
task list changed" fans out to every consumer (alarms, all-day prompts,
widget), instead of the widget doing its own auth + Drive round trip.

## Known limitations

- **Widget staleness matches app staleness** (`docs/sync.md`): the widget
  only refreshes when *this device's* app process fetches or writes —
  app open, pull-to-refresh, the periodic all-day worker, or boot
  rescheduling. A task added from another client (web, Assistant, Bixby,
  or a second Android device) won't show up on the widget until one of
  those triggers this device's `TaskRepository` again. No dedicated
  periodic widget-refresh job exists yet.
- **No per-task actions from the widget** (no complete/dismiss button) —
  tapping anywhere opens `MainActivity`. Out of scope for this pass.
- **`MAX_WIDGET_ROWS` (12) is hardcoded**, no "show more" affordance if a
  day list is longer.
- Not build-verified in this environment — no Android SDK available (see
  `docs/android-app.md`).

## Manual test

1. Complete `docs/drive-storage.md` setup, sign in, add a few tasks across
   different days, some with a time set and some without.
2. Long-press the home screen, add the "Reminder" widget, resize it in
   both directions to confirm `resizeMode` works.
3. Confirm it shows tasks grouped by day, sorted with timed tasks visible
   alongside all-day ones, and that completed tasks are excluded.
4. Add/complete/delete a task in the app; confirm the widget updates
   without needing to remove and re-add it.
5. Force-stop the app, reboot the device (or trigger `BootReceiver`
   manually), and confirm the widget still shows the last-known task list
   from before reboot (Glance state survives process death) and refreshes
   once `BootReceiver`'s reconciliation runs.
6. Remove the widget from the home screen, then add/complete a task;
   confirm no crash occurs (`TaskWidgetUpdater.update()` no-ops when
   `getGlanceIds()` is empty).
