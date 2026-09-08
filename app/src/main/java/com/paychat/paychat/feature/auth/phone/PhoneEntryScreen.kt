package com.paychat.paychat.feature.auth.phone

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.feature.auth.AuthBackdrop
import com.paychat.paychat.feature.auth.AuthHero
import com.paychat.paychat.feature.auth.AuthCard
import com.paychat.paychat.feature.auth.StaggeredEntrance
import com.paychat.paychat.ui.components.PhoneNumberField
import com.paychat.paychat.ui.theme.Ink20
import com.paychat.paychat.ui.theme.Ink90
import com.paychat.paychat.ui.theme.Teal90
import com.paychat.paychat.ui.components.PrimaryButton

/**
 * The one front door of the signed out flow.
 *
 * The number is the identity in PayChat, so it is the only thing asked for
 * here; whether an account exists decides what comes next, rather than the
 * user having to know which of two screens they belong on.
 */
@Composable
fun PhoneEntryScreen(
    onKnownNumber: (phoneE164: String) -> Unit,
    onNewNumber: (phoneE164: String) -> Unit,
    viewModel: PhoneEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.knownNumber) {
        state.knownNumber?.let {
            onKnownNumber(it)
            viewModel.onNavigated()
        }
    }

    state.offerRegistration?.let { e164 ->
        val display = state.offerRegistrationDisplay ?: e164
        AlertDialog(
            onDismissRequest = viewModel::onRegistrationDeclined,
            // Ink, not the theme's white: the dialog interrupts a dark flow,
            // and on a light-mode phone the default lands as a white slab in
            // the middle of it.
            containerColor = Ink20,
            titleContentColor = Color.White,
            textContentColor = Ink90.copy(alpha = 0.75f),
            title = { Text("Create an account?") },
            text = {
                Text(
                    "No PayChat account uses $display yet. Create one for this number?",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onNewNumber(e164)
                    viewModel.onNavigated()
                }) { Text("Create account", color = Teal90) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onRegistrationDeclined) {
                    Text("Cancel", color = Ink90.copy(alpha = 0.7f))
                }
            },
        )
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
                        headline = "Chat and settle up",
                        subtitle = "Enter your phone number to get started.",
                    )
                }

                StaggeredEntrance(delayMillis = 120) {
                    AuthCard {
                        PhoneNumberField(
                            region = state.region,
                            national = state.phone,
                            onRegionChange = viewModel::onRegionChange,
                            onNationalChange = viewModel::onPhoneChange,
                            error = state.phoneError,
                            enabled = !state.checking,
                            imeAction = ImeAction.Done,
                        )
                    }
                }

                StaggeredEntrance(delayMillis = 200) {
                    PrimaryButton(
                        text = "Continue",
                        onClick = viewModel::submit,
                        loading = state.checking,
                    )
                }

                // A plain gap, not weight: a weighted spacer would let the
                // keyboard squeeze the button above it into a sliver.
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
