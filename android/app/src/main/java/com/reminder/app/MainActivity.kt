package com.reminder.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.reminder.app.alarm.AlarmScheduler
import com.reminder.app.allday.AllDayPromptScheduler
import com.reminder.app.auth.AuthManager
import com.reminder.app.auth.DriveAuthorizationResult
import com.reminder.app.data.TaskRepository
import com.reminder.app.ui.tasklist.TaskListScreen
import com.reminder.app.ui.tasklist.TaskListViewModel
import com.reminder.app.ui.theme.ReminderAppTheme
import com.reminder.app.widget.TaskWidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val authManager by lazy { AuthManager(applicationContext) }
    private val alarmScheduler by lazy { AlarmScheduler(applicationContext) }
    private val allDayPromptScheduler by lazy { AllDayPromptScheduler(applicationContext) }
    private val widgetUpdater by lazy { TaskWidgetUpdater(applicationContext) }
    private val repository by lazy {
        TaskRepository(
            authManager,
            alarmScheduler = alarmScheduler,
            allDayPromptScheduler = allDayPromptScheduler,
            widgetUpdater = widgetUpdater,
        )
    }
    private val viewModel: TaskListViewModel by viewModels {
        TaskListViewModel.factory(repository, alarmScheduler)
    }

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        val result = authManager.onAuthorizationConsentResult(activityResult.data)
        if (result is DriveAuthorizationResult.Granted) {
            viewModel.onSignedIn()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Denying just means alarm notifications won't show — nothing else to do here. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationPermission()

        setContent {
            ReminderAppTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                if (uiState.signedIn) {
                    TaskListScreen(
                        viewModel = viewModel,
                        onRequestExactAlarmPermission = ::requestExactAlarmPermission,
                    )
                } else {
                    SignInScreen(onSignInClick = ::requestDriveAccess)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Also re-checks exact-alarm permission state, which can only change
        // while this Activity isn't in the foreground (Settings redirect).
        if (viewModel.uiState.value.signedIn) {
            viewModel.refresh()
        }
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun requestDriveAccess() {
        lifecycleScope.launch {
            when (val result = authManager.requestDriveAuthorization()) {
                is DriveAuthorizationResult.Granted -> viewModel.onSignedIn()
                is DriveAuthorizationResult.ConsentRequired -> {
                    val request = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                    consentLauncher.launch(request)
                }
            }
        }
    }
}
