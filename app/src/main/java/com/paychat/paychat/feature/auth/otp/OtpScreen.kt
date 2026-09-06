package com.paychat.paychat.feature.auth.otp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.ui.components.FormError
import com.paychat.paychat.ui.components.OtpField
import com.paychat.paychat.ui.components.PrimaryButton

@Composable
fun OtpScreen(
    onVerified: () -> Unit,
    onBack: () -> Unit,
    viewModel: OtpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // One SMS per visit to this screen; resending is an explicit action.
    LaunchedEffect(Unit) {
        activity?.let { viewModel.sendCode(it) }
    }

    LaunchedEffect(state.done) {
        if (state.done) onVerified()
    }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Verify your number", style = MaterialTheme.typography.headlineMedium)
            Text(
                "We sent a code to ${state.phoneDisplay}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            OtpField(
                value = state.code,
                onValueChange = viewModel::onCodeChange,
                enabled = !state.verifying,
            )

            FormError(state.error)

            PrimaryButton(
                text = if (state.purpose == OtpPurpose.REGISTER) "Create account" else "Set password",
                onClick = viewModel::submitCode,
                loading = state.sending || state.verifying,
                enabled = state.codeSent,
            )

            TextButton(
                onClick = { activity?.let { viewModel.sendCode(it, resend = true) } },
                enabled = state.secondsUntilResend == 0 && !state.verifying,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    if (state.secondsUntilResend > 0) {
                        "Resend code in ${state.secondsUntilResend}s"
                    } else {
                        "Resend code"
                    }
                )
            }

            TextButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Change number")
            }
        }
    }
}

/**
 * Firebase phone auth needs the hosting Activity, and Compose only hands out a
 * Context, which on some setups is a wrapper rather than the Activity itself.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
