package com.paychat.koli.feature.contacts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.koli.ui.components.FormError
import com.paychat.koli.ui.components.NameField
import com.paychat.koli.ui.components.PhoneField
import com.paychat.koli.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    onOpenThread: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: AddContactViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.openThreadId) {
        state.openThreadId?.let {
            onOpenThread(it)
            viewModel.onThreadOpened()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add a contact") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "You can record money with someone who does not use PayChat yet. " +
                    "When they join with this number, they will see the history.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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
                imeAction = ImeAction.Done,
            )

            when (state.peerIsRegistered) {
                true -> Hint(
                    "This number is on PayChat. Money you say you sent will need " +
                        "them to accept it."
                )
                false -> Hint(
                    "This number is not on PayChat. Your records apply straight " +
                        "away, and they can review them when they join."
                )
                null -> Unit
            }

            FormError(state.error)

            PrimaryButton(
                text = "Save and open chat",
                onClick = viewModel::submit,
                loading = state.submitting,
            )
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
