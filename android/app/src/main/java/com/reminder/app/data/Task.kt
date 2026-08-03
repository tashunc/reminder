package com.reminder.app.data

import kotlinx.serialization.Serializable

/** Schema for `tasks.json` in the Drive App Data folder — see docs/data-model.md. */
@Serializable
data class Task(
    val id: String,
    val title: String,
    val description: String? = null,
    val day: String,
    val time: String? = null,
    val recurrence: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val completed: Boolean = false,
    val source: String = TaskSource.ANDROID,
)

@Serializable
data class TaskFile(
    val version: Int = 1,
    val tasks: List<Task> = emptyList(),
)

object TaskSource {
    const val ANDROID = "android"
    const val WEB = "web"
    const val ASSISTANT = "assistant"
    const val BIXBY = "bixby"
}

object Recurrence {
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
}
