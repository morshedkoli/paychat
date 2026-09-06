package com.paychat.paychat.feature.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** The authenticators PayChat accepts: a fingerprint or face, or the PIN. */
private const val ALLOWED =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Whether this device can ask for a screen lock at all.
 *
 * A phone with no PIN, pattern or fingerprint set cannot: offering the switch
 * there would produce a lock nobody could ever get past.
 */
fun canLock(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(ALLOWED) == BiometricManager.BIOMETRIC_SUCCESS

/**
 * Covers the app until the device's own screen lock is satisfied.
 *
 * PayChat never stores a PIN of its own. The prompt accepts a fingerprint or
 * face, and falls back to whatever the device already uses, so there is no
 * second secret to forget and nothing for the app to keep.
 */
@Composable
fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit) {
    // Asked for as soon as the cover appears, so the usual case is a single
    // glance at the phone rather than a button press.
    LaunchedEffect(Unit) { prompt(activity, onUnlocked) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "PayChat is locked",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Unlock with your fingerprint, face, or screen lock.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { prompt(activity, onUnlocked) },
                modifier = Modifier.padding(top = 24.dp),
            ) { Text("Unlock") }
        }
    }
}

private fun prompt(activity: FragmentActivity, onUnlocked: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }
            // A failure or a cancel leaves the cover in place. The button is
            // there to try again; there is nothing else to offer.
        },
    )

    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock PayChat")
            .setSubtitle("Your chats and balances are locked on this device.")
            .setAllowedAuthenticators(ALLOWED)
            .build()
    )
}
