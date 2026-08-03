package com.reminder.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by AlarmManager when a task's exact alarm goes off; posts the full-screen alarm notification. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
        val description = intent.getStringExtra(EXTRA_TASK_DESCRIPTION)
        AlarmNotifier(context).notify(taskId, title, description)
    }
}
