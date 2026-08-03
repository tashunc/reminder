package com.reminder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.reminder.app.auth.AuthManager
import com.reminder.app.auth.DriveAuthorizationResult
import com.reminder.app.data.TaskRepository
import com.reminder.app.ui.tasklist.TaskListScreen
import com.reminder.app.ui.tasklist.TaskListViewModel
import com.reminder.app.ui.theme.ReminderAppTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val authManager by lazy { AuthManager(applicationContext) }
    private val repository by lazy { TaskRepository(authManager) }
    private val viewModel: TaskListViewModel by viewModels { TaskListViewModel.factory(repository) }

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { activityResult ->
        val result = authManager.onAuthorizationConsentResult(activityResult.data)
        if (result is DriveAuthorizationResult.Granted) {
            viewModel.onSignedIn()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ReminderAppTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                if (uiState.signedIn) {
                    TaskListScreen(viewModel = viewModel)
                } else {
                    SignInScreen(onSignInClick = ::requestDriveAccess)
                }
            }
        }
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
