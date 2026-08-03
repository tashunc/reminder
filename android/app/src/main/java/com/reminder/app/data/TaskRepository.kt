package com.reminder.app.data

import com.reminder.app.auth.AuthManager
import java.time.Instant
import java.util.UUID

/**
 * Read-modify-write task store backed by Drive App Data (docs/drive-storage.md).
 * On write conflicts, follows the retry policy in docs/sync.md: re-fetch,
 * re-apply the change on top of the fresh data, retry once.
 */
class TaskRepository(
    private val authManager: AuthManager,
    private val store: DriveTaskStore = DriveTaskStore(),
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
    }

    private suspend fun refetch() {
        val token = authManager.getDriveAccessTokenOrThrow()
        val remote = store.fetch(token)
        fileId = remote.fileId
        revisionId = remote.headRevisionId
        tasks = remote.taskFile.tasks
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
        tasks = newTasks
    }
}
