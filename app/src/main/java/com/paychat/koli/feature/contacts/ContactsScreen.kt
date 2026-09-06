package com.paychat.koli.feature.contacts

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.koli.ui.components.Avatar
import com.paychat.koli.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onOpenThread: (String) -> Unit,
    onAddContact: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(state.openThreadId) {
        state.openThreadId?.let {
            onOpenThread(it)
            viewModel.onThreadOpened()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Contacts") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add a contact by hand")
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {

            if (state.syncing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = { Text("Search name or number") },
                singleLine = true,
            )

            if (!state.permissionGranted) {
                ContactsPermissionPrompt(
                    onGrant = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                )
            }

            LazyColumn(Modifier.fillMaxSize()) {
                if (state.addedByHand.isNotEmpty()) {
                    item { SectionHeader("Added by you") }
                    items(state.addedByHand, key = { it.contactId }) { contact ->
                        ContactRow(
                            name = contact.name,
                            subtitle = contact.phone,
                            avatarKey = contact.phone,
                            onClick = { onOpenThread(contact.threadId) },
                        )
                    }
                }

                if (state.onPayChat.isNotEmpty()) {
                    item { SectionHeader("On PayChat") }
                    items(state.onPayChat, key = { it.phone }) { contact ->
                        ContactRow(
                            name = contact.displayName,
                            subtitle = contact.phone,
                            avatarKey = contact.phone,
                            onClick = {
                                viewModel.openConversation(contact.phone, contact.displayName)
                            },
                        )
                    }
                }

                if (state.notOnPayChat.isNotEmpty()) {
                    item { SectionHeader("Not on PayChat yet") }
                    items(state.notOnPayChat, key = { it.phone }) { contact ->
                        ContactRow(
                            name = contact.displayName,
                            subtitle = "${contact.phone} · records kept until they join",
                            avatarKey = contact.phone,
                            onClick = {
                                viewModel.openConversation(contact.phone, contact.displayName)
                            },
                        )
                    }
                }

                if (state.isEmpty && state.permissionGranted && !state.syncing) {
                    item {
                        Text(
                            "No contacts yet. Add someone by hand with the button below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactsPermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Find people you know", style = MaterialTheme.typography.titleMedium)
        Text(
            "PayChat checks which of your contacts already have an account. " +
                "Only the phone numbers are checked, and your contacts are never uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton(text = "Allow contacts", onClick = onGrant)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ContactRow(
    name: String,
    subtitle: String,
    avatarKey: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Avatar(name = name, key = avatarKey) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
