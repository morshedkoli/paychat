package com.paychat.paychat.feature.auth.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.core.validation.Validators
import com.paychat.paychat.feature.auth.AuthHero
import com.paychat.paychat.feature.auth.FooterLink
import com.paychat.paychat.ui.components.NameField
import com.paychat.paychat.ui.components.PasswordField
import com.paychat.paychat.ui.components.PhoneField
import com.paychat.paychat.ui.components.PrimaryButton

@Composable
fun RegisterScreen(
    onOtpRequired: (phoneE164: String) -> Unit,
    onLoginInstead: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.proceedToOtp) {
        state.proceedToOtp?.let {
            onOtpRequired(it)
            viewModel.onNavigated()
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            AuthHero(
                headline = "Let's get you set up",
                subtitle = "Your phone number is how people find you and settle up with you.",
            )

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
                    NameField(
                        value = state.name,
                        onValueChange = viewModel::onNameChange,
                        error = state.nameError,
                        enabled = !state.submitting,
                    )
                    PhoneField(
                        value = state.phone,
                        onValueChange = viewModel::onPhoneChange,
                        error = state.phoneError,
                        enabled = !state.submitting,
                    )
                    PasswordField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChange,
                        error = state.passwordError,
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

            PrimaryButton(
                text = "Send code",
                onClick = viewModel::submit,
                loading = state.submitting,
            )

            FooterLink(
                lead = "Already have an account?",
                action = "Log in",
                onClick = onLoginInstead,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}
