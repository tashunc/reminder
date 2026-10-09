package com.reminder.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.reminder.app.data.Task
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Pushes the current task list into every placed widget instance's Glance state. */
class TaskWidgetUpdater(private val context: Context) {

    suspend fun update(tasks: List<Task>) {
        val serialized = Json.encodeToString(ListSerializer(Task.serializer()), tasks)
        val glanceIds = GlanceAppWidgetManager(context).getGlanceIds(TaskWidget::class.java)
        if (glanceIds.isEmpty()) return // no widget placed -- nothing to update

        glanceIds.forEach { glanceId ->
            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply { this[KEY_TASKS_JSON] = serialized }
            }
        }
        TaskWidget().updateAll(context)
    }
}
