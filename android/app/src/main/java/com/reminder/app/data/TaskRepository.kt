package com.reminder.app.data

import com.reminder.app.alarm.AlarmScheduler
import com.reminder.app.allday.AllDayPromptScheduler
import com.reminder.app.auth.AuthManager
import java.time.Instant
import java.util.UUID

/**
 * Read-modify-write task store backed by Drive App Data (docs/drive-storage.md).
 * On write conflicts, follows the retry policy in docs/sync.md: re-fetch,
 * re-apply the change on top of the fresh data, retry once.
 *
 * [alarmScheduler] and [allDayPromptScheduler], if provided, are reconciled
 * against the current task list after every fetch and write (see
 * [reconcileSchedules]) so exact alarms and all-day prompts always match
 * what's in Drive — including tasks changed by other clients.
 */
class TaskRepository(
    private val authManager: AuthManager,
    private val store: DriveTaskStore = DriveTaskStore(),
    private val alarmScheduler: AlarmScheduler? = null,
    private val allDayPromptScheduler: AllDayPromptScheduler? = null,
) {
    private var fileId: String? = null
    private var revisionId: String? = null
    private var tasks: List<Task> = emptyList()

    suspend fun loadTasks(): List<Task> {
        refetch()
        return tasks
    }

    suspend fun addTask(
        title: String,
        description: String?,
        day: String,
        time: String?,
        recurrence: String?,
    ): Task {
        val now = Instant.now().toString()
        val newTask = Task(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            day = day,
            time = time,
            recurrence = recurrence,
            createdAt = now,
            updatedAt = now,
        )
        mutate { it + newTask }
        return newTask
    }

    suspend fun setCompleted(taskId: String, completed: Boolean) {
        mutate { current ->
            current.map {
                if (it.id == taskId) it.copy(completed = completed, updatedAt = Instant.now().toString()) else it
            }
        }
    }

    suspend fun deleteTask(taskId: String) {
        mutate { current -> current.filterNot { it.id == taskId } }
        // The deleted task is no longer in `tasks`, so reconcileSchedules()
        // (which only walks tasks still present) can't cancel it for us —
        // do it explicitly for both schedulers (whichever applied is a no-op).
        alarmScheduler?.cancel(taskId)
        allDayPromptScheduler?.cancelToday(taskId)
    }

    private suspend fun refetch() {
        val token = authManager.getDriveAccessTokenOrThrow()
        val remote = store.fetch(token)
        fileId = remote.fileId
        revisionId = remote.headRevisionId
        updateTasksAndReconcile(remote.taskFile.tasks)
    }

    private suspend fun mutate(transform: (List<Task>) -> List<Task>) {
        val token = authManager.getDriveAccessTokenOrThrow()
        val proposed = transform(tasks)
        try {
            applyWrite(token, proposed)
        } catch (_: DriveConflictException) {
            refetch()
            val reapplied = transform(tasks)
            applyWrite(token, reapplied)
        }
    }

    private suspend fun applyWrite(token: String, newTasks: List<Task>) {
        val remote = store.write(token, fileId, revisionId, TaskFile(tasks = newTasks))
        fileId = remote.fileId
        revisionId = remote.headRevisionId
        updateTasksAndReconcile(newTasks)
    }

    private fun updateTasksAndReconcile(newTasks: List<Task>) {
        val previous = tasks
        tasks = newTasks
        reconcileSchedules(previous, newTasks)
    }

    private fun reconcileSchedules(previous: List<Task>, current: List<Task>) {
        alarmScheduler?.let { scheduler ->
            current.forEach { task ->
                if (task.time != null && !task.completed) scheduler.schedule(task) else scheduler.cancel(task.id)
            }
        }

        allDayPromptScheduler?.let { scheduler ->
            // scheduleTodayIfNeeded() only picks new random times once per
            // calendar day, so cancellation for tasks that stopped
            // qualifying (completed/deleted/converted/moved off today) has
            // to be handled separately by diffing against the prior state.
            val currentById = current.associateBy { it.id }
            previous.forEach { old ->
                if (old.time == null && !old.completed) {
                    val updated = currentById[old.id]
                    if (updated == null || updated.completed || updated.time != null || updated.day != old.day) {
                        scheduler.cancelToday(old.id)
                    }
                }
            }
            scheduler.scheduleTodayIfNeeded(current)
        }
    }
}
