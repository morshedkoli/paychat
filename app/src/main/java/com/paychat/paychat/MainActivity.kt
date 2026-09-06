package com.paychat.paychat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.paychat.paychat.data.notifications.PayChatMessagingService
import com.paychat.paychat.data.settings.ThemeChoice
import com.paychat.paychat.feature.settings.AppShellViewModel
import com.paychat.paychat.ui.nav.PayChatNavHost
import com.paychat.paychat.ui.theme.PayChatTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * The conversation a tapped notification asked for, if any. Held as a flow
     * because the intent can also arrive while the activity is already up.
     */
    private val openThread = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readThreadFrom(intent)

        setContent {
            val shell: AppShellViewModel = hiltViewModel()
            val theme by shell.theme.collectAsStateWithLifecycle()

            PayChatTheme(darkTheme = theme.isDark()) {
                RequestNotificationPermission()
                val threadId by openThread.collectAsState()
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PayChatNavHost(
                        navController = rememberNavController(),
                        openThreadId = threadId,
                        onThreadOpened = { openThread.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The notification reuses the existing task, so this is the usual path
        // for a tap while the app is already running.
        setIntent(intent)
        readThreadFrom(intent)
    }

    private fun readThreadFrom(intent: Intent?) {
        intent?.getStringExtra(PayChatMessagingService.EXTRA_THREAD_ID)
            ?.let { openThread.value = it }
    }
}

/**
 * Asks for the notification permission once, on the versions that have one.
 *
 * Nothing depends on the answer: a refusal only means the tray stays quiet,
 * and the messages themselves still arrive.
 */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** Whether this choice means dark colours right now. */
@Composable
private fun ThemeChoice.isDark(): Boolean = when (this) {
    ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    ThemeChoice.LIGHT -> false
    ThemeChoice.DARK -> true
}
