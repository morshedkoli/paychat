package com.paychat.paychat.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.data.settings.ThemeChoice
import com.paychat.paychat.feature.lock.canLock
import com.paychat.paychat.ui.components.Avatar
import com.paychat.paychat.ui.components.shareStatement

/**
 * Everything about the account and the app that the user can change.
 *
 * The phone number is shown but not editable: it identifies the account, and
 * changing it would strand every thread and balance recorded against it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // A phone with no PIN, pattern or fingerprint cannot ask for one, so the
    // switch says why instead of producing a lock nobody can get past.
    val lockAvailable = remember { canLock(context) }

    var editingName by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::setPhoto) }

    LaunchedEffect(state.statement) {
        state.statement?.let { uri ->
            shareStatement(context, uri, "PayChat statement")
            viewModel.statementShared()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
        ) {
            ProfileHeader(
                name = state.name,
                phone = state.phone,
                photoUrl = state.photoUrl,
                uploading = state.uploadingPhoto,
                onChangePhoto = {
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onEditName = { editingName = true },
            )

            HorizontalDivider()

            SectionHeading("Appearance")
            ThemeRow(current = state.theme, onChoose = viewModel::setTheme)

            HorizontalDivider()

            SectionHeading("Privacy")
            ListItem(
                headlineContent = { Text("App lock") },
                supportingContent = {
                    Text(
                        if (lockAvailable) {
                            "Ask for your screen lock when PayChat comes back to the front."
                        } else {
                            "Set a screen lock on this phone first."
                        }
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.appLockEnabled,
                        onCheckedChange = viewModel::setAppLockEnabled,
                        enabled = lockAvailable,
                    )
                },
            )

            HorizontalDivider()

            SectionHeading("Your money")
            ListItem(
                modifier = Modifier.clickable(
                    enabled = !state.exporting,
                    onClick = viewModel::exportEverything,
                ),
                headlineContent = { Text("Export all statements") },
                supportingContent = {
                    Text("One PDF covering every conversation with a closing balance.")
                },
                trailingContent = {
                    if (state.exporting) CircularProgressIndicator(Modifier.size(20.dp))
                },
            )

            HorizontalDivider()

            ListItem(
                modifier = Modifier.clickable { confirmSignOut = true },
                headlineContent = {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                },
                supportingContent = {
                    Text("This device stops receiving notifications for the account.")
                },
            )
        }
    }

    if (editingName) {
        NameDialog(
            initial = state.name,
            onDismiss = { editingName = false },
            onSave = { name ->
                viewModel.setName(name)
                editingName = false
            },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "Your chats and balances stay on the server. You will need your " +
                        "password to sign back in."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        onSignOut()
                    }
                ) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Stay") }
            },
        )
    }
}

@Composable
private fun ProfileHeader(
    name: String,
    phone: String,
    photoUrl: String?,
    uploading: Boolean,
    onChangePhoto: () -> Unit,
    onEditName: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(
                name = name,
                key = phone,
                photoUrl = photoUrl,
                size = 64.dp,
                modifier = Modifier.clickable(enabled = !uploading, onClick = onChangePhoto),
            )
            if (uploading) CircularProgressIndicator(Modifier.size(28.dp))
        }

        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                name.ifBlank { "Your name" },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                phone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Tap your picture to change it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = onEditName) {
            Icon(Icons.Default.Edit, contentDescription = "Change your name")
        }
    }
}

@Composable
private fun ThemeRow(current: ThemeChoice, onChoose: (ThemeChoice) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeChoice.entries.forEach { choice ->
            FilterChip(
                selected = choice == current,
                onClick = { onChoose(choice) },
                label = { Text(choice.label()) },
            )
        }
    }
}

private fun ThemeChoice.label(): String = when (this) {
    ThemeChoice.SYSTEM -> "System"
    ThemeChoice.LIGHT -> "Light"
    ThemeChoice.DARK -> "Dark"
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun NameDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
