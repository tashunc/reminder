package com.reminder.app.allday

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Fires a single randomized all-day-task prompt scheduled by [AllDayPromptScheduler]. */
class AllDayPromptWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(WORK_DATA_TASK_ID) ?: return Result.failure()
        val title = inputData.getString(WORK_DATA_TASK_TITLE).orEmpty()
        val description = inputData.getString(WORK_DATA_TASK_DESCRIPTION)
        AllDayPromptNotifier(applicationContext).notify(taskId, title, description)
        return Result.success()
    }
}
