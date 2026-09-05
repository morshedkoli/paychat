package com.paychat.koli.feature.auth.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.koli.core.validation.PasswordError
import com.paychat.koli.core.validation.Validators
import com.paychat.koli.feature.auth.message
import com.paychat.koli.ui.components.FormError
import com.paychat.koli.ui.components.PasswordField
import com.paychat.koli.ui.components.PhoneField
import com.paychat.koli.ui.components.PrimaryButton

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onRegisterInstead: () -> Unit,
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
                val phone = viewModel.startPasswordReset(newPassword)
                showReset = false
                if (phone != null) onResetRequested(phone)
            },
        )
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
            Text("Welcome back", style = MaterialTheme.typography.headlineMedium)

            Spacer(Modifier.height(12.dp))

            PhoneField(
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                error = state.phoneError,
                enabled = !state.submitting,
            )
            PasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                enabled = !state.submitting,
                imeAction = ImeAction.Done,
            )

            FormError(state.error)

            PrimaryButton(
                text = "Log in",
                onClick = viewModel::submit,
                loading = state.submitting,
            )

            TextButton(
                onClick = { showReset = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Forgot password")
            }

            TextButton(
                onClick = onRegisterInstead,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Create a new account")
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
