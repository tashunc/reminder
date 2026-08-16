package com.reminder.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reminder.app.allday.AllDayPromptScheduler
import com.reminder.app.auth.AuthManager
import com.reminder.app.auth.DriveAuthorizationResult
import com.reminder.app.data.TaskRepository
import com.reminder.app.widget.TaskWidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Exact alarms set via AlarmManager do not survive a reboot, so every
 * future time-based task's alarm must be rescheduled here. This only works
 * if the previously granted Drive scope can be **silently** re-authorized —
 * no UI can be shown from a boot broadcast. If that fails (e.g. the grant
 * was revoked), alarms for this account are not rescheduled until the user
 * reopens the app. See docs/alarms.md.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(15_000) { rescheduleAll(appContext) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun rescheduleAll(context: Context) {
        val authManager = AuthManager(context)
        val authResult = runCatching { authManager.requestDriveAuthorization() }.getOrNull()
        if (authResult !is DriveAuthorizationResult.Granted) return

        // TaskRepository.loadTasks() reconciles alarms, all-day prompts, and
        // the widget for every task it loads (see
        // TaskRepository.reconcileSchedules), so a plain load is enough to
        // restore everything that's due in the future.
        val repository = TaskRepository(
            authManager,
            alarmScheduler = AlarmScheduler(context),
            allDayPromptScheduler = AllDayPromptScheduler(context),
            widgetUpdater = TaskWidgetUpdater(context),
        )
        runCatching { repository.loadTasks() }
    }
}
