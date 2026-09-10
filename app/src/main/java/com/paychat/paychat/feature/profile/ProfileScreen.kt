package com.paychat.paychat.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.ui.components.Avatar
import com.paychat.paychat.ui.components.shareFile
import com.paychat.paychat.ui.components.shareStatement
import com.paychat.paychat.ui.theme.AmountStyle
import com.paychat.paychat.ui.theme.PayChatTheme

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
    viewModel: ProfileViewModel = hiltViewModel(),
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
 * The identity card: picture, name, number, and where the account stands.
 *
 * The picture carries a camera badge and the name is tappable, which replaces
 * the explanatory line and the Edit button the settings header used to need.
 */
@Composable
private fun ProfileIdentity(
    state: ProfileUiState,
    onChangePhoto: () -> Unit,
    onEditName: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
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

                if (state.uploadingPhoto) {
                    CircularProgressIndicator(
                        Modifier.size(96.dp).align(Alignment.Center),
                        strokeWidth = 3.dp,
                    )
                } else {
                    // Sits on the picture's edge, so what tapping it does needs
                    // no sentence underneath to explain it.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClick = onChangePhoto),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Change your picture",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onEditName)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    state.name.ifBlank { "Your name" },
                    style = MaterialTheme.typography.titleLarge,
                )
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Change your name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp).size(16.dp),
                )
            }

            Text(
                state.phone,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            StandingRow(state.stats)
        }
    }
}

/**
 * Net balance, conversations, settled. The net reads as what it means rather
 * than as a signed number: money coming in and money going out are different
 * facts, not one figure with a sign.
 */
@Composable
private fun StandingRow(stats: ProfileStats) {
    val ledger = PayChatTheme.ledger
    val netLabel = when {
        stats.net.isZero -> "All settled"
        stats.net.isPositive -> "You will get"
        else -> "You will give"
    }
    val netColour = when {
        stats.net.isZero -> MaterialTheme.colorScheme.onSurface
        stats.net.isPositive -> ledger.credit
        else -> ledger.debit
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Standing(netLabel, stats.net.abs().format(), netColour)
        Standing("Conversations", stats.conversations.toString(), MaterialTheme.colorScheme.onSurface)
        Standing("Settled up", stats.settled.toString(), MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Standing(label: String, value: String, colour: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = AmountStyle, color = colour)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
