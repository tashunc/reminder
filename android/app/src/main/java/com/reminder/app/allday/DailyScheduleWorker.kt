package com.reminder.app.allday

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reminder.app.alarm.AlarmScheduler
import com.reminder.app.auth.AuthManager
import com.reminder.app.auth.DriveAuthorizationResult
import com.reminder.app.data.TaskRepository

/**
 * Once-a-day fallback for `AllDayPromptScheduler.scheduleTodayIfNeeded`,
 * covering days the app is never opened. Like `BootReceiver`, this only
 * works if the Drive scope can be silently re-authorized — no UI is
 * available from a background worker. See docs/all-day-notifications.md.
 */
class DailyScheduleWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val authManager = AuthManager(applicationContext)
        val authResult = runCatching { authManager.requestDriveAuthorization() }.getOrNull()
        if (authResult !is DriveAuthorizationResult.Granted) return Result.retry()

        val repository = TaskRepository(
            authManager,
            alarmScheduler = AlarmScheduler(applicationContext),
            allDayPromptScheduler = AllDayPromptScheduler(applicationContext),
        )
        // loadTasks() -> refetch() reconciles all-day prompt scheduling for
        // whatever it loads, same as every other repository consumer.
        val loaded = runCatching { repository.loadTasks() }
        return if (loaded.isSuccess) Result.success() else Result.retry()
    }
}
