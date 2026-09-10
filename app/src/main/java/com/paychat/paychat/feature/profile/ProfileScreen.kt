package com.paychat.paychat.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.feature.settings.AccountSection
import com.paychat.paychat.feature.settings.AppearanceSection
import com.paychat.paychat.feature.settings.DangerSection
import com.paychat.paychat.feature.settings.DataSection
import com.paychat.paychat.feature.settings.DeleteAccountDialog
import com.paychat.paychat.feature.settings.NameDialog
import com.paychat.paychat.feature.settings.SettingsUiState
import com.paychat.paychat.feature.settings.SettingsViewModel
import com.paychat.paychat.feature.settings.SignOutDialog
import com.paychat.paychat.ui.components.Avatar
import com.paychat.paychat.ui.components.shareFile
import com.paychat.paychat.ui.components.shareStatement

/**
 * The profile tab: who you are on top, then everything about the account and the
 * app that you can change.
 *
 * This is a tab root, so there is no top bar and no back arrow — there is
 * nothing behind it to go back to.
 *
 * The phone number is shown but not editable: it identifies the account, and
 * changing it would strand every thread and balance recorded against it.
 */
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var editingName by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::setPhoto) }

    LaunchedEffect(state.statement) {
        state.statement?.let { uri ->
            shareStatement(context, uri, "PayChat statement")
            viewModel.statementShared()
        }
    }

    LaunchedEffect(state.dataFile) {
        state.dataFile?.let { uri ->
            shareFile(context, uri, "application/json", "PayChat data export")
            viewModel.dataFileShared()
        }
    }

    // The account is gone, so there is nothing left to show behind this.
    LaunchedEffect(state.deleted) {
        if (state.deleted) onSignOut()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
        ) {
            ProfileIdentity(
                state = state,
                onChangePhoto = {
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onEditName = { editingName = true },
            )

            AppearanceSection(state, onChooseTheme = viewModel::setTheme)

            AccountSection(state, onToggleAppLock = viewModel::setAppLockEnabled)

            DataSection(
                state,
                onExportStatements = viewModel::exportEverything,
                onExportData = viewModel::exportData,
            )

            DangerSection(
                state,
                onSignOut = { confirmSignOut = true },
                onDeleteAccount = { confirmDelete = true },
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

    if (confirmDelete) {
        DeleteAccountDialog(
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAccount()
            },
        )
    }

    if (confirmSignOut) {
        SignOutDialog(
            onDismiss = { confirmSignOut = false },
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
        )
    }
}

/**
 * The centred identity block: picture, name, number. Tapping the picture opens
 * the photo picker, exactly as the old settings header did.
 */
@Composable
private fun ProfileIdentity(
    state: SettingsUiState,
    onChangePhoto: () -> Unit,
    onEditName: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(
                name = state.name,
                key = state.phone,
                photoUrl = state.photoUrl,
                size = 96.dp,
                modifier = Modifier.clickable(
                    enabled = !state.uploadingPhoto,
                    onClick = onChangePhoto,
                ),
            )
            if (state.uploadingPhoto) CircularProgressIndicator(Modifier.size(28.dp))
        }

        Text(
            state.name.ifBlank { "Your name" },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )

        Text(
            state.phone,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(onClick = onEditName) { Text("Edit") }

        Text(
            "Tap your picture to change it.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
