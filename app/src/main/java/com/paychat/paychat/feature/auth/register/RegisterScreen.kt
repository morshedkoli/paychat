package com.paychat.paychat.feature.auth.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.core.validation.Validators
import com.paychat.paychat.feature.auth.AuthBackdrop
import com.paychat.paychat.feature.auth.AuthHero
import com.paychat.paychat.feature.auth.FooterLink
import com.paychat.paychat.feature.auth.AuthCard
import com.paychat.paychat.feature.auth.StaggeredEntrance
import com.paychat.paychat.ui.components.NameField
import com.paychat.paychat.ui.components.PasswordField
import com.paychat.paychat.ui.components.PrimaryButton

@Composable
fun RegisterScreen(
    onOtpRequired: (phoneE164: String) -> Unit,
    onChangeNumber: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.proceedToOtp) {
        state.proceedToOtp?.let {
            onOtpRequired(it)
            viewModel.onNavigated()
        }
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
                        headline = "Let's get you set up",
                        subtitle = "Pick a name people will recognise, and a password for this account.",
                    )
                }

                StaggeredEntrance(delayMillis = 120) {
                    AuthCard {
                        NameField(
                            value = state.name,
                            onValueChange = viewModel::onNameChange,
                            error = state.nameError,
                            enabled = !state.submitting,
                        )
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
                            error = state.passwordError,
                            enabled = !state.submitting,
                            imeAction = ImeAction.Next,
                        )
                        PasswordField(
                            value = state.confirmPassword,
                            onValueChange = viewModel::onConfirmPasswordChange,
                            label = "Confirm password",
                            error = state.confirmPasswordError,
                            enabled = !state.submitting,
                            imeAction = ImeAction.Done,
                        )
                        Text(
                            "At least ${Validators.MIN_PASSWORD_LENGTH} characters, not digits only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                StaggeredEntrance(delayMillis = 200) {
                    PrimaryButton(
                        text = "Send code",
                        onClick = viewModel::submit,
                        loading = state.submitting,
                    )
                }

                StaggeredEntrance(delayMillis = 280, modifier = Modifier.fillMaxWidth()) {
                    FooterLink(
                        lead = "Wrong number?",
                        action = "Change it",
                        onClick = onChangeNumber,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
