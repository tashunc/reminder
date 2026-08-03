package com.reminder.app.alarm

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reminder.app.ui.theme.ReminderAppTheme

/**
 * Full-screen alarm UI shown when a time-based task's exact alarm fires —
 * shows over the lock screen and turns the screen on, like a native alarm
 * clock. See docs/alarms.md.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val title = intent.getStringExtra(EXTRA_TASK_TITLE).orEmpty()
        val description = intent.getStringExtra(EXTRA_TASK_DESCRIPTION)
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)

        setContent {
            ReminderAppTheme {
                AlarmScreen(
                    title = title,
                    description = description,
                    onDismiss = { dismiss(taskId) },
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
    }

    private fun dismiss(taskId: String?) {
        if (taskId != null) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(taskId.hashCode())
        }
        finish()
    }
}

@Composable
private fun AlarmScreen(title: String, description: String?, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            description?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(32.dp))
            Button(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
