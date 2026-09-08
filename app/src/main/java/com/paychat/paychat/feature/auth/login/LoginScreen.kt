package com.paychat.paychat.feature.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.core.validation.PasswordError
import com.paychat.paychat.core.validation.Validators
import com.paychat.paychat.feature.auth.AuthBackdrop
import com.paychat.paychat.feature.auth.AuthHero
import com.paychat.paychat.feature.auth.FooterLink
import com.paychat.paychat.feature.auth.LightCardScheme
import com.paychat.paychat.feature.auth.StaggeredEntrance
import com.paychat.paychat.feature.auth.message
import com.paychat.paychat.ui.components.FormError
import com.paychat.paychat.ui.components.PasswordField
import com.paychat.paychat.ui.components.PrimaryButton

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onChangeNumber: () -> Unit,
    onResetRequested: (phoneE164: String) -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showReset by remember { mutableStateOf(false) }

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    if (showReset) {
        ResetPasswordDialog(
            onDismiss = { showReset = false },
            onConfirm = { newPassword ->
                showReset = false
                onResetRequested(viewModel.startPasswordReset(newPassword))
            },
        )
    }

    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent) { inner ->
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
                        headline = "Welcome back",
                        subtitle = "Sign in to keep chatting and settling up.",
                    )
                }

                StaggeredEntrance(delayMillis = 120) {
                    LightCardScheme {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        state.phoneDisplay,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(
                                        onClick = onChangeNumber,
                                        enabled = !state.submitting,
                                    ) { Text("Change") }
                                }
                                PasswordField(
                                    value = state.password,
                                    onValueChange = viewModel::onPasswordChange,
                                    enabled = !state.submitting,
                                    imeAction = ImeAction.Done,
                                )

                                FormError(state.error)

                                TextButton(
                                    onClick = { showReset = true },
                                    modifier = Modifier.align(Alignment.End),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                                ) {
                                    Text("Forgot password?", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                StaggeredEntrance(delayMillis = 200) {
                    PrimaryButton(
                        text = "Log in",
                        onClick = viewModel::submit,
                        loading = state.submitting,
                    )
                }

                // A plain gap, not weight: a weighted spacer would let the
                // keyboard squeeze the button above it into a sliver.
                Spacer(Modifier.height(24.dp))

                StaggeredEntrance(delayMillis = 280, modifier = Modifier.fillMaxWidth()) {
                    FooterLink(
                        lead = "Not your number?",
                        action = "Use another",
                        onClick = onChangeNumber,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * Collects the new password before the OTP step, so that verifying the number
 * and setting the password are one uninterrupted flow.
 */
@Composable
private fun ResetPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<PasswordError?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "We will text a code to your number, then set this password.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "New password",
                    error = error?.message(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val problem = Validators.validatePassword(password)
                if (problem != null) error = problem else onConfirm(password)
            }) { Text("Send code") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
