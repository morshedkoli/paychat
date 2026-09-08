package com.paychat.paychat.feature.auth.otp

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.feature.auth.AuthBackdrop
import com.paychat.paychat.feature.auth.AuthCard
import com.paychat.paychat.feature.auth.AuthHero
import com.paychat.paychat.feature.auth.FooterLink
import com.paychat.paychat.feature.auth.StaggeredEntrance
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

    Scaffold(containerColor = Color.Transparent) { inner ->
        AuthBackdrop {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                StaggeredEntrance(delayMillis = 0) {
                    AuthHero(
                        headline = "Verify your number",
                        subtitle = "We sent a code to ${state.phoneDisplay}.",
                    )
                }

                StaggeredEntrance(delayMillis = 120) {
                    AuthCard {
                        OtpField(
                            value = state.code,
                            onValueChange = viewModel::onCodeChange,
                            enabled = !state.verifying,
                        )

                        FormError(state.error)

                        // Inside the card, where the countdown reads as part
                        // of the code entry rather than a stray link.
                        TextButton(
                            onClick = { activity?.let { viewModel.sendCode(it, resend = true) } },
                            enabled = state.secondsUntilResend == 0 && !state.verifying,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            Text(
                                if (state.secondsUntilResend > 0) {
                                    "Resend code in ${state.secondsUntilResend}s"
                                } else {
                                    "Resend code"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                StaggeredEntrance(delayMillis = 200) {
                    PrimaryButton(
                        text = if (state.purpose == OtpPurpose.REGISTER) {
                            "Create account"
                        } else {
                            "Set password"
                        },
                        onClick = viewModel::submitCode,
                        loading = state.sending || state.verifying,
                        enabled = state.codeSent,
                    )
                }

                // A plain gap, not weight: a weighted spacer would let the
                // keyboard squeeze the button above it into a sliver.
                Spacer(Modifier.height(24.dp))

                StaggeredEntrance(delayMillis = 280, modifier = Modifier.fillMaxWidth()) {
                    FooterLink(
                        lead = "Wrong number?",
                        action = "Change it",
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
