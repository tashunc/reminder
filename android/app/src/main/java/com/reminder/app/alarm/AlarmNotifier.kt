package com.reminder.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

const val ALARM_CHANNEL_ID = "task_alarms"

/**
 * Posts the alarm notification for a fired task. On a locked/idle device
 * the full-screen intent takes over the screen like a native alarm clock;
 * otherwise this shows as a heads-up notification (see docs/alarms.md for
 * when each happens and the USE_FULL_SCREEN_INTENT / POST_NOTIFICATIONS
 * permission caveats).
 */
class AlarmNotifier(private val context: Context) {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID,
                "Task alarms",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Time-based task reminders that ring like an alarm"
                setBypassDnd(true)
                enableVibration(true)
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

        val notification = NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(description ?: "Task reminder")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(fullScreenPendingIntent)
            .apply { if (canFullScreen) setFullScreenIntent(fullScreenPendingIntent, true) }
            .build()

        manager.notify(taskId.hashCode(), notification)
    }

    private fun notificationManager() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
