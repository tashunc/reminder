package com.reminder.app.allday

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.reminder.app.alarm.AlarmActivity
import com.reminder.app.alarm.EXTRA_TASK_DESCRIPTION
import com.reminder.app.alarm.EXTRA_TASK_ID
import com.reminder.app.alarm.EXTRA_TASK_TITLE

const val ALL_DAY_PROMPT_CHANNEL_ID = "all_day_prompts"

/**
 * Posts one randomized check-in prompt for an all-day task. Reuses
 * `AlarmActivity` for the full-screen UI (title/description/Dismiss) —
 * it's identical to the time-based alarm UI in shape, only the copy and
 * notification channel differ. See docs/all-day-notifications.md.
 */
class AllDayPromptNotifier(private val context: Context) {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALL_DAY_PROMPT_CHANNEL_ID,
                "All-day task prompts",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Randomized check-ins for tasks without a specific time"
            }
            notificationManager().createNotificationChannel(channel)
        }
    }

    fun notify(taskId: String, title: String, description: String?) {
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TITLE, title)
            putExtra(EXTRA_TASK_DESCRIPTION, description)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val manager = notificationManager()
        val canFullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || manager.canUseFullScreenIntent()

        val notification = NotificationCompat.Builder(context, ALL_DAY_PROMPT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(description ?: "Still on your list for today")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPendingIntent)
            .apply { if (canFullScreen) setFullScreenIntent(fullScreenPendingIntent, true) }
            .build()

        manager.notify(taskId.hashCode(), notification)
    }

    private fun notificationManager() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
