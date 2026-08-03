package com.reminder.app.ui.tasklist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reminder.app.data.Recurrence

/**
 * Day/time are plain text fields (`YYYY-MM-DD` / `HH:mm`) for this first
 * pass. A real DatePicker/TimePicker is follow-up polish, not required for
 * the CRUD-over-Drive milestone this screen exists to prove out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String?, day: String, time: String?, recurrence: String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New task") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("Day (YYYY-MM-DD)") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Time HH:mm (optional)") },
                )
                Spacer(Modifier.height(8.dp))
                Text("Repeat", style = MaterialTheme.typography.labelMedium)
                Row {
                    listOf(null, Recurrence.DAILY, Recurrence.WEEKLY, Recurrence.MONTHLY).forEach { option ->
                        FilterChip(
                            selected = recurrence == option,
                            onClick = { recurrence = option },
                            label = { Text(option?.replaceFirstChar { it.uppercase() } ?: "None") },
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        title.trim(),
                        description.trim().ifBlank { null },
                        day.trim(),
                        time.trim().ifBlank { null },
                        recurrence,
                    )
                },
                enabled = title.isNotBlank() && day.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
