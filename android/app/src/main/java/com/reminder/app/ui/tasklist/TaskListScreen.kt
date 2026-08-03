package com.reminder.app.ui.tasklist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reminder.app.data.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(viewModel: TaskListViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Reminder") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add task")
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when {
                uiState.loading && uiState.tasks.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.tasks.isEmpty() -> {
                    Text(
                        "No tasks yet. Tap + to add one.",
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    val grouped = uiState.tasks.groupBy { it.day }.toSortedMap()
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        grouped.forEach { (day, tasksForDay) ->
                            item(key = "header-$day") { DayHeader(day) }
                            items(tasksForDay.sortedBy { it.time ?: "" }, key = { it.id }) { task ->
                                TaskRow(
                                    task = task,
                                    onToggleCompleted = { viewModel.toggleCompleted(task.id, !task.completed) },
                                    onDelete = { viewModel.deleteTask(task.id) },
                                )
                            }
                        }
                    }
                }
            }

            uiState.error?.let { message ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                ) { Text(message) }
            }
        }
    }

    if (showAddDialog) {
        AddTaskDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, description, day, time, recurrence ->
                viewModel.addTask(title, description, day, time, recurrence)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun DayHeader(day: String) {
    Text(
        text = day,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun TaskRow(task: Task, onToggleCompleted: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(task.title) },
        supportingContent = {
            val timeLabel = task.time ?: "All day"
            Text(if (task.description.isNullOrBlank()) timeLabel else "$timeLabel — ${task.description}")
        },
        leadingContent = {
            Checkbox(checked = task.completed, onCheckedChange = { onToggleCompleted() })
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete task")
            }
        },
    )
}
