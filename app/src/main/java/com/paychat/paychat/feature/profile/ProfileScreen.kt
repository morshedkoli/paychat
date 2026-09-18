package com.paychat.paychat.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.ui.components.Avatar
import com.paychat.paychat.ui.components.PayChatSpinner
import com.paychat.paychat.ui.components.shareFile
import com.paychat.paychat.ui.components.shareStatement
import com.paychat.paychat.ui.theme.LocalHideBalances
import com.paychat.paychat.ui.theme.PayChatTheme
import kotlinx.coroutines.launch

/**
 * The profile tab: who you are on top, then everything about the account and the
 * app that you can change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var editingName by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var confirmClearCache by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
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
            ProfileIdentity(
                state = state,
                onChangePhoto = {
                    pickPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onEditName = { editingName = true },
                onCopyPhone = {
                    clipboardManager.setText(AnnotatedString(state.phone))
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Phone number copied to clipboard")
                    }
                },
                onOpenQr = { showQrDialog = true },
            )

            Spacer(Modifier.height(4.dp))

            AppearanceSection(state, onChooseTheme = viewModel::setTheme)

            AccountSection(
                state = state,
                onToggleAppLock = viewModel::setAppLockEnabled,
                onSelectLockTimeout = viewModel::setLockTimeout,
                onToggleAlwaysHideBalances = viewModel::setAlwaysHideBalancesOnLaunch,
            )

            DataSection(
                state,
                onExportStatements = viewModel::exportEverything,
                onExportData = viewModel::exportData,
                onClearCache = { confirmClearCache = true },
            )

            DangerSection(
                state,
                onSignOut = { confirmSignOut = true },
                onDeleteAccount = { confirmDelete = true },
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showQrDialog) {
        PersonalQrDialog(
            name = state.name,
            phone = state.phone,
            photoUrl = state.photoUrl,
            onDismiss = { showQrDialog = false },
            onShare = {
                val link = QrCodeHelper.createPayChatUri(state.phone, state.name)
                val intent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, "Connect or pay with me on PayChat: $link")
                    type = "text/plain"
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Share PayChat QR"))
            },
            onCopyLink = {
                val link = QrCodeHelper.createPayChatUri(state.phone, state.name)
                clipboardManager.setText(AnnotatedString(link))
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Payment link copied to clipboard")
                }
            },
        )
    }

    if (confirmClearCache) {
        ClearCacheDialog(
            cacheSize = state.cacheSizeFormatted,
            onDismiss = { confirmClearCache = false },
            onConfirm = {
                confirmClearCache = false
                viewModel.clearCache()
            },
        )
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
 * The interactive profile identity card:
 * large avatar with camera badge, user name with edit button,
 * phone number with 1-tap clipboard copy, and net balance pill badge.
 */
@Composable
private fun ProfileIdentity(
    state: ProfileUiState,
    onChangePhoto: () -> Unit,
    onEditName: () -> Unit,
    onCopyPhone: () -> Unit,
    onOpenQr: () -> Unit,
) {
    val ledger = PayChatTheme.ledger
    val hideBalances = LocalHideBalances.current

    val netLabel = if (hideBalances) {
        Money.MASKED
    } else when {
        state.stats.net.isZero -> "All settled"
        state.stats.net.isPositive -> "You will get ${state.stats.net.abs().format()}"
        else -> "You will give ${state.stats.net.abs().format()}"
    }

    val netColour = when {
        hideBalances || state.stats.net.isZero -> MaterialTheme.colorScheme.onSurfaceVariant
        state.stats.net.isPositive -> ledger.credit
        else -> ledger.debit
    }

    val netBadgeBg = when {
        hideBalances || state.stats.net.isZero -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        state.stats.net.isPositive -> ledger.credit.copy(alpha = 0.12f)
        else -> ledger.debit.copy(alpha = 0.12f)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Large Avatar with photo edit badge
                Box(modifier = Modifier.size(68.dp)) {
                    Avatar(
                        name = state.name,
                        key = state.phone,
                        photoUrl = state.photoUrl,
                        size = 68.dp,
                        modifier = Modifier.clickable(
                            enabled = !state.uploadingPhoto,
                            onClick = onChangePhoto,
                        ),
                    )

                    if (state.uploadingPhoto) {
                        PayChatSpinner(
                            modifier = Modifier.align(Alignment.Center),
                            size = 68.dp,
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onChangePhoto),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = "Change your picture",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                // Name & phone
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onEditName)
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            text = state.name.ifBlank { "Your name" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit name",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    // Phone number with 1-tap copy
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onCopyPhone)
                            .padding(vertical = 2.dp, horizontal = 2.dp),
                    ) {
                        Text(
                            text = state.phone,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy phone number",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                // QR Code Button
                IconButton(
                    onClick = onOpenQr,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "My QR Code",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Net balance pill badge strip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = netBadgeBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = netColour,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Net Balance",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = netLabel,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = netColour,
                    )
                }
            }
        }
    }
}

