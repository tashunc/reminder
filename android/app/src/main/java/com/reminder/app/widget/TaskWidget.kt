package com.reminder.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.reminder.app.MainActivity
import com.reminder.app.data.Task
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

val KEY_TASKS_JSON: Preferences.Key<String> = stringPreferencesKey("tasks_json")
private const val MAX_WIDGET_ROWS = 12

/**
 * Home screen widget showing incomplete tasks grouped by day. Renders from
 * a JSON snapshot written by [TaskWidgetUpdater] whenever the app syncs
 * with Drive — the widget itself never talks to Drive directly. See
 * docs/widget.md for why, and for the resulting staleness trade-off.
 */
class TaskWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val tasks = prefs[KEY_TASKS_JSON]
                ?.let { runCatching { Json.decodeFromString(ListSerializer(Task.serializer()), it) }.getOrNull() }
                .orEmpty()
            TaskWidgetContent(tasks)
        }
    }
}

@Composable
private fun TaskWidgetContent(tasks: List<Task>) {
    val upcoming = tasks.filter { !it.completed }.sortedWith(compareBy({ it.day }, { it.time ?: "" }))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(8.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text("Reminder", style = TextStyle(fontWeight = FontWeight.Bold))
        if (upcoming.isEmpty()) {
            Text("No upcoming tasks")
        } else {
            var lastDay: String? = null
            upcoming.take(MAX_WIDGET_ROWS).forEach { task ->
                if (task.day != lastDay) {
                    lastDay = task.day
                    Text(task.day, style = TextStyle(fontWeight = FontWeight.Bold))
                }
                val label = if (task.time != null) "${task.time}  ${task.title}" else task.title
                Text(label)
            }
        }
    }
}
