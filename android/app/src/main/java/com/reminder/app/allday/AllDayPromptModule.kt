package com.reminder.app.allday

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val DAILY_WORK_NAME = "daily-all-day-prompt-schedule"
private const val DAILY_RUN_HOUR = 0
private const val DAILY_RUN_MINUTE = 10

/**
 * Registers [DailyScheduleWorker] to run once a day, timed for shortly
 * after midnight local time. Call once at process start (see
 * `ReminderApplication.onCreate`) — `enqueueUniquePeriodicWork` with `KEEP`
 * makes repeated calls harmless.
 */
fun schedulePeriodicAllDayPromptWork(context: Context) {
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    var nextRun = now.with(LocalTime.of(DAILY_RUN_HOUR, DAILY_RUN_MINUTE))
    if (!nextRun.isAfter(now)) nextRun = nextRun.plusDays(1)
    val initialDelayMillis = Duration.between(now, nextRun).toMillis()

    val request = PeriodicWorkRequestBuilder<DailyScheduleWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()

    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(DAILY_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
}
