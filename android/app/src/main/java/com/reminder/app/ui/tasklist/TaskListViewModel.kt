package com.reminder.app.ui.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.reminder.app.data.Task
import com.reminder.app.data.TaskRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TaskListUiState(
    val signedIn: Boolean = false,
    val loading: Boolean = false,
    val tasks: List<Task> = emptyList(),
    val error: String? = null,
)

class TaskListViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskListUiState())
    val uiState: StateFlow<TaskListUiState> = _uiState

    fun onSignedIn() {
        _uiState.update { it.copy(signedIn = true) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching { repository.loadTasks() }
                .onSuccess { tasks -> _uiState.update { it.copy(loading = false, tasks = tasks) } }
                .onFailure { e -> _uiState.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun addTask(title: String, description: String?, day: String, time: String?, recurrence: String?) {
        viewModelScope.launch {
            runCatching { repository.addTask(title, description, day, time, recurrence) }
                .onSuccess { refresh() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun toggleCompleted(taskId: String, completed: Boolean) {
        viewModelScope.launch {
            runCatching { repository.setCompleted(taskId, completed) }
                .onSuccess { refresh() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            runCatching { repository.deleteTask(taskId) }
                .onSuccess { refresh() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    companion object {
        fun factory(repository: TaskRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TaskListViewModel(repository) as T
        }
    }
}
