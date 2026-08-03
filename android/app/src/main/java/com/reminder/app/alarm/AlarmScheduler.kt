package com.reminder.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.reminder.app.data.Task
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

const val EXTRA_TASK_ID = "task_id"
const val EXTRA_TASK_TITLE = "task_title"
const val EXTRA_TASK_DESCRIPTION = "task_description"

/**
 * Schedules/cancels exact alarms for tasks that have [Task.time] set.
 * All-day tasks (time == null) are out of scope here — those get randomized
 * WorkManager prompts instead (a separate build-order step).
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Android 12+ (S) requires this special permission for exact alarms. */
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Schedules [task]'s alarm if it has a future time and the exact-alarm
     * permission is granted. Returns false (and schedules nothing) otherwise
     * — callers should not treat that as an error, just a state to reflect
     * in the UI (see docs/alarms.md).
     */
    fun schedule(task: Task): Boolean {
        val time = task.time ?: return false
        if (!canScheduleExactAlarms()) return false

        val triggerAtMillis = triggerAtMillis(task.day, time) ?: return false
        if (triggerAtMillis <= System.currentTimeMillis()) return false

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntentFor(task.id, task.title, task.description))
        return true
    }

    fun cancel(taskId: String) {
        val pendingIntent = pendingIntentFor(taskId, title = "", description = null)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun pendingIntentFor(taskId: String, title: String, description: String?): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_DESCRIPTION, description)
        }
        // Request code is derived from taskId so schedule()/cancel() address the same
        // alarm; a hash collision between two task IDs would misfire one for the
        // other, which is an accepted low-probability limitation (see docs/alarms.md).
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun triggerAtMillis(day: String, time: String): Long? = runCatching {
        val date = LocalDate.parse(day)
        val localTime = LocalTime.parse(time)
        date.atTime(localTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}
