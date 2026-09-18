package com.paychat.paychat.feature.chat

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.paychat.paychat.ui.theme.LocalHideBalances
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.core.model.TxnCategory
import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.data.moderation.ReportReason
import com.paychat.paychat.ui.components.Avatar
import com.paychat.paychat.ui.components.EmptyState
import com.paychat.paychat.ui.theme.AmountStyle
import com.paychat.paychat.ui.theme.PayChatTheme
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onAddTransaction: (threadId: String, direction: String?, amount: String?, note: String?, category: String?) -> Unit,
    onOpenLedger: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val playback = rememberVoicePlayback()
    val context = LocalContext.current
    val ledger = PayChatTheme.ledger

    LifecycleResumeEffect(Unit) {
        viewModel.screenResumed()
        onPauseOrDispose { viewModel.screenPaused() }
    }

    var showAttachments by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var pendingCapture by remember { mutableStateOf<Pair<String, File>?>(null) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let(viewModel::sendPickedImage) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val capture = pendingCapture
        pendingCapture = null
        if (saved && capture != null) viewModel.sendCapturedImage(capture.first, capture.second)
    }

    val requestAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startRecording() }

    LaunchedEffect(state.messages.firstOrNull()?.messageId) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }

    DisposeRecording(recording = state.recordingMessageId != null, onCancel = viewModel::cancelRecording)

    if (showAttachments) {
        AttachmentSheet(
            onDismiss = { showAttachments = false },
            onGallery = {
                showAttachments = false
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onCamera = {
                showAttachments = false
                val messageId = viewModel.newAttachmentId()
                val file = viewModel.cameraFileFor(messageId)
                file.parentFile?.mkdirs()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                pendingCapture = messageId to file
                takePicture.launch(uri)
            },
            onTransaction = {
                showAttachments = false
                onAddTransaction(state.threadId, null, null, null, null)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onOpenLedger(state.threadId) },
                    ) {
                        Avatar(
                            name = state.peerName,
                            key = state.peerPhone,
                            photoUrl = state.peerPhotoUrl,
                            size = 38.dp,
                        )
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(
                                text = state.peerName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                ),
                                maxLines = 1,
                            )
                            val subtitle = when {
                                state.peerDeparted -> "Account deleted"
                                state.peerTyping -> "typing…"
                                state.isLocal -> "Not on PayChat yet"
                                else -> "tap for ledger"
                            }
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = if (state.peerTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Quick Payment / Transaction shortcut
                    IconButton(onClick = { onAddTransaction(state.threadId, null, null, null, null) }) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = "Transfer",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }

                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        val settleAction: (() -> Unit)? = if (!state.balance.isZero) {
                            {
                                val dir = if (state.balance.isNegative) TxnDirection.SENT.name else TxnDirection.RECEIVED.name
                                val amountStr = String.format(Locale.US, "%.2f", Math.abs(state.balance.minor) / 100.0)
                                onAddTransaction(state.threadId, dir, amountStr, "Settlement", TxnCategory.SETTLEMENT.name)
                            }
                        } else null

                        if (settleAction != null) {
                            DropdownMenuItem(
                                text = { Text("Settle balance") },
                                onClick = {
                                    showMenu = false
                                    settleAction()
                                },
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("View ledger") },
                            onClick = {
                                showMenu = false
                                onOpenLedger(state.threadId)
                            },
                        )

                        DropdownMenuItem(
                            text = { Text(if (state.blockedByMe) "Unblock" else "Block") },
                            enabled = !state.blockedByPeer,
                            onClick = {
                                showMenu = false
                                if (state.blockedByMe) viewModel.setBlocked(false)
                                else confirmBlock = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Report") },
                            onClick = {
                                showMenu = false
                                reporting = true
                            },
                        )
                    }
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
                .background(ledger.chatBackground)
                .imePadding()
        ) {
            val settleAction: (() -> Unit)? = if (!state.balance.isZero) {
                {
                    val dir = if (state.balance.isNegative) TxnDirection.SENT.name else TxnDirection.RECEIVED.name
                    val amountStr = String.format(Locale.US, "%.2f", Math.abs(state.balance.minor) / 100.0)
                    onAddTransaction(state.threadId, dir, amountStr, "Settlement", TxnCategory.SETTLEMENT.name)
                }
            } else null

            val hideBalances = LocalHideBalances.current
            BalanceHeader(
                balanceText = state.balance.formatSigned(hideBalances),
                positive = state.balance.isPositive,
                zero = state.balance.isZero,
                onClick = { onOpenLedger(state.threadId) },
                onSettle = settleAction,
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Payments,
                            title = "No messages yet",
                            body = if (state.isLocal) {
                                state.peerName + " is not on PayChat yet. You can still record transactions."
                            } else {
                                "Say hello or record money with the payment button."
                            },
                        )
                    }
                }

                items(state.messages, key = { it.messageId }) { message ->
                    val transaction = message.txnId?.let(state.transactions::get)
                    if (transaction != null) {
                        TransactionBubble(
                            transaction = transaction,
                            viewerUid = state.viewerUid,
                            onOpen = { onOpenTransaction(transaction.txnId) },
                            onAccept = { viewModel.acceptTransaction(transaction.txnId) },
                            onReject = { viewModel.rejectTransaction(transaction.txnId) },
                            onCancel = { viewModel.cancelTransaction(transaction.txnId) },
                        )
                    } else {
                        MessageBubble(
                            message = message,
                            viewerUid = state.viewerUid,
                            playback = playback,
                            onReply = viewModel::startReply,
                        )
                    }
                }
            }

            if (state.peerDeparted) {
                ClosedNotice(
                    state.peerName + " has deleted their account. The conversation and its balance are kept."
                )
            } else if (state.blocked) {
                BlockedNotice(
                    blockedByMe = state.blockedByMe,
                    peerName = state.peerName,
                    onUnblock = { viewModel.setBlocked(false) },
                )
            } else {
                Composer(
                    draft = state.draft,
                    replyingTo = state.replyingTo,
                    onCancelReply = viewModel::cancelReply,
                    canSend = state.canSend,
                    recording = state.recordingMessageId != null,
                    onDraftChange = viewModel::onDraftChange,
                    onSend = viewModel::send,
                    onAttach = { showAttachments = true },
                    onStartRecording = { requestAudio.launch(Manifest.permission.RECORD_AUDIO) },
                    onStopRecording = viewModel::stopRecording,
                    onCancelRecording = viewModel::cancelRecording,
                    onQuickTransaction = { onAddTransaction(state.threadId, null, null, null, null) },
                )
            }
        }
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = { confirmBlock = false },
            title = { Text("Block " + state.peerName + "?") },
            text = {
                Text(
                    "Neither of you can send messages or record money in this conversation. What is already recorded stays exactly as it is."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBlock = false
                        viewModel.setBlocked(true)
                    }
                ) { Text("Block") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBlock = false }) { Text("Cancel") }
            },
        )
    }

    if (reporting) {
        ReportDialog(
            peerName = state.peerName,
            onDismiss = { reporting = false },
            onReport = { reason ->
                reporting = false
                viewModel.report(reason, detail = null)
            },
        )
    }
}

@Composable
private fun BlockedNotice(blockedByMe: Boolean, peerName: String, onUnblock: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (blockedByMe) {
                "You blocked " + peerName + ". The ledger is unchanged."
            } else {
                peerName + " has blocked this conversation. The ledger is unchanged."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (blockedByMe) {
            TextButton(onClick = onUnblock) { Text("Unblock") }
        }
    }
}

@Composable
private fun ClosedNotice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
    )
}

@Composable
private fun ReportDialog(
    peerName: String,
    onDismiss: () -> Unit,
    onReport: (ReportReason) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report " + peerName) },
        text = {
            Column {
                Text(
                    "A moderator sees this conversation and why you reported it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                ReportReason.entries.forEach { reason ->
                    TextButton(onClick = { onReport(reason) }) { Text(reason.label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DisposeRecording(recording: Boolean, onCancel: () -> Unit) {
    androidx.compose.runtime.DisposableEffect(recording) {
        onDispose { if (recording) onCancel() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onTransaction: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(bottom = 24.dp)) {
            ListItem(
                headlineContent = { Text("Photo Gallery") },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF9C27B0)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Color.White)
                    }
                },
                modifier = Modifier.clickable(onClick = onGallery),
            )
            ListItem(
                headlineContent = { Text("Camera") },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE91E63)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                    }
                },
                modifier = Modifier.clickable(onClick = onCamera),
            )
            ListItem(
                headlineContent = { Text("Send / Request Money") },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Payments, contentDescription = null, tint = Color.White)
                    }
                },
                modifier = Modifier.clickable(onClick = onTransaction),
            )
        }
    }
}

/**
 * WhatsApp-style slim interactive balance bar.
 */
@Composable
private fun BalanceHeader(
    balanceText: String,
    positive: Boolean,
    zero: Boolean,
    onClick: () -> Unit,
    onSettle: (() -> Unit)? = null,
) {
    val ledger = PayChatTheme.ledger

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = when {
                        zero -> "Settled up"
                        positive -> "They owe you:"
                        else -> "You owe them:"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = balanceText,
                    style = AmountStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = when {
                        zero -> MaterialTheme.colorScheme.onSurfaceVariant
                        positive -> ledger.credit
                        else -> ledger.debit
                    },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!zero && onSettle != null) {
                    FilledTonalButton(
                        onClick = onSettle,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    ) {
                        Text("Settle", style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        "Ledger",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

/**
 * WhatsApp signature floating composer layout:
 * - Rounded pill container on the left with emoji, attachment, text input, and quick pay.
 * - Independent circular action button on the right for Send / Voice.
 */
@Composable
private fun Composer(
    draft: String,
    replyingTo: QuotedMessage? = null,
    onCancelReply: () -> Unit = {},
    canSend: Boolean,
    recording: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onQuickTransaction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 2.dp),
    ) {
        if (replyingTo != null) {
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to ${replyingTo.authorName}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = replyingTo.textSnippet,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = onCancelReply, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel reply",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        if (recording) {
            Surface(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCancelRecording) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Discard recording",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "Recording voice note…",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            FilledIconButton(
                onClick = onStopRecording,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Send recording")
            }
            return@Row
        }

        // Left Pill Input Box
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onAttach, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Attach",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Message",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    },
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )

                if (draft.isBlank()) {
                    IconButton(onClick = onQuickTransaction, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Default.Payments,
                            contentDescription = "Pay",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // Right Circular Floating Action Button
        FilledIconButton(
            onClick = if (canSend) onSend else onStartRecording,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = if (canSend) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                contentDescription = if (canSend) "Send" else "Record a voice message",
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
}



