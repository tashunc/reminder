package com.reminder.app.allday

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.reminder.app.data.Task
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.random.Random

private const val PREFS_NAME = "all_day_prompts"
private const val KEY_LAST_SCHEDULED_DATE = "last_scheduled_date"
private const val ACTIVE_WINDOW_START_HOUR = 9
private const val ACTIVE_WINDOW_END_HOUR = 21

/** Hard cap on prompts/day, used to bound how many unique work names [cancelToday] has to clean up. */
const val MAX_PROMPTS_PER_DAY = 5

/** "2-3 randomized ... prompts per day" per CLAUDE.md; not yet wired to a settings screen. */
const val DEFAULT_PROMPTS_PER_DAY = 3

const val WORK_DATA_TASK_ID = "task_id"
const val WORK_DATA_TASK_TITLE = "task_title"
const val WORK_DATA_TASK_DESCRIPTION = "task_description"

/**
 * Schedules randomized full-screen prompts, spread across a fixed
 * 09:00-21:00 active window, for each all-day task (`Task.time == null`)
 * due today — via WorkManager one-off work with a randomized initial
 * delay. See docs/all-day-notifications.md.
 */
class AllDayPromptScheduler(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Picks this run's random prompt times and enqueues them, but only
     * once per calendar day — safe to call from both `onResume()` and the
     * daily fallback worker; whichever runs first for a given day wins.
     */
    fun scheduleTodayIfNeeded(tasks: List<Task>, promptsPerDay: Int = DEFAULT_PROMPTS_PER_DAY) {
        val today = LocalDate.now().toString()
        if (prefs.getString(KEY_LAST_SCHEDULED_DATE, null) == today) return
        prefs.edit().putString(KEY_LAST_SCHEDULED_DATE, today).apply()

        tasks.filter { it.time == null && !it.completed && it.day == today }
            .forEach { task -> scheduleTask(task, today, promptsPerDay) }
    }

    /** Cancels any of today's remaining prompts for [taskId] (task completed, deleted, or changed). */
    fun cancelToday(taskId: String) {
        val today = LocalDate.now().toString()
        repeat(MAX_PROMPTS_PER_DAY) { index ->
            workManager.cancelUniqueWork(uniqueWorkName(taskId, today, index))
        }
    }

    private fun scheduleTask(task: Task, today: String, promptsPerDay: Int) {
        val count = promptsPerDay.coerceIn(1, MAX_PROMPTS_PER_DAY)
        randomFutureDelaysMillis(count).forEachIndexed { index, delayMillis ->
            val data = workDataOf(
                WORK_DATA_TASK_ID to task.id,
                WORK_DATA_TASK_TITLE to task.title,
                WORK_DATA_TASK_DESCRIPTION to task.description,
            )
            val request = OneTimeWorkRequestBuilder<AllDayPromptWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()
            workManager.enqueueUniqueWork(uniqueWorkName(task.id, today, index), ExistingWorkPolicy.REPLACE, request)
        }
    }

    /**
     * Splits what's left of today's active window into [count] equal
     * buckets and picks one random moment per bucket, so prompts land
     * spread through the day instead of clustering. Simple, not perfectly
     * uniform once little of the window remains.
     */
    private fun randomFutureDelaysMillis(count: Int): List<Long> {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault())
        val windowStart = startOfDay.with(LocalTime.of(ACTIVE_WINDOW_START_HOUR, 0))
        val windowEnd = startOfDay.with(LocalTime.of(ACTIVE_WINDOW_END_HOUR, 0))
        val nowMillis = System.currentTimeMillis()
        val rangeStartMillis = maxOf(windowStart.toInstant().toEpochMilli(), nowMillis)
        val rangeEndMillis = windowEnd.toInstant().toEpochMilli()
        if (rangeEndMillis <= rangeStartMillis) return emptyList()

        val bucketSize = (rangeEndMillis - rangeStartMillis) / count
        return (0 until count).map { i ->
            val bucketStart = rangeStartMillis + i * bucketSize
            val bucketEnd = if (i == count - 1) rangeEndMillis else bucketStart + bucketSize
            val triggerAtMillis = Random.nextLong(bucketStart, bucketEnd.coerceAtLeast(bucketStart + 1))
            triggerAtMillis - nowMillis
        }
    }

    private fun uniqueWorkName(taskId: String, day: String, index: Int) = "all-day-prompt-$taskId-$day-$index"
}
