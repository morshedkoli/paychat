package com.paychat.paychat.feature.chat

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.data.moderation.ReportReason
import com.paychat.paychat.ui.components.Avatar
import com.paychat.paychat.ui.components.EmptyState
import com.paychat.paychat.ui.theme.AmountStyle
import com.paychat.paychat.ui.theme.PayChatTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onAddTransaction: (String) -> Unit,
    onOpenLedger: (String) -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val playback = rememberVoicePlayback()
    val context = LocalContext.current

    // A push for the chat being read is suppressed, so the screen has to say
    // when it is the one on top.
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

    // The list is reversed, so index 0 is the newest message.
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

    // Leaving mid-recording throws the partial file away rather than sending
    // whatever happened to be captured.
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
                onAddTransaction(state.threadId)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            name = state.peerName,
                            key = state.peerPhone,
                            photoUrl = state.peerPhotoUrl,
                            size = 36.dp,
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(state.peerName, style = MaterialTheme.typography.titleMedium)
                            if (state.isLocal) {
                                Text(
                                    "Not on PayChat yet",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (state.blockedByMe) "Unblock" else "Block") },
                            // Being blocked by the other person is not
                            // something this side can undo.
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
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).imePadding()) {

            BalanceHeader(
                balanceText = state.balance.formatSigned(),
                positive = state.balance.isPositive,
                zero = state.balance.isZero,
                onClick = { onOpenLedger(state.threadId) },
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                reverseLayout = true,
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Default.Payments,
                            title = "No messages yet",
                            body = if (state.isLocal) {
                                state.peerName + " is not on PayChat yet. You can " +
                                    "still record money, and it follows them here " +
                                    "when they join."
                            } else {
                                "Say hello, or record money with the plus button."
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
                        )
                    }
                }
            }

            HorizontalDivider()

            if (state.blocked) {
                BlockedNotice(
                    blockedByMe = state.blockedByMe,
                    peerName = state.peerName,
                    onUnblock = { viewModel.setBlocked(false) },
                )
            } else {
                Composer(
                    draft = state.draft,
                    canSend = state.canSend,
                    recording = state.recordingMessageId != null,
                    onDraftChange = viewModel::onDraftChange,
                    onSend = viewModel::send,
                    onAttach = { showAttachments = true },
                    onStartRecording = { requestAudio.launch(Manifest.permission.RECORD_AUDIO) },
                    onStopRecording = viewModel::stopRecording,
                    onCancelRecording = viewModel::cancelRecording,
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
                    "Neither of you can send messages or record money in this " +
                        "conversation. What is already recorded stays exactly as it is."
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

/**
 * Replaces the composer while the conversation is closed.
 *
 * The history above it stays exactly where it was: blocking someone settles
 * nothing, and hiding what is owed would be the one thing this app must not
 * do.
 */
@Composable
private fun BlockedNotice(blockedByMe: Boolean, peerName: String, onUnblock: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding(),
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ListItem(
            headlineContent = { Text("Photo from gallery") },
            leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
            modifier = Modifier.clickableRow(onGallery),
        )
        ListItem(
            headlineContent = { Text("Take a photo") },
            leadingContent = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
            modifier = Modifier.clickableRow(onCamera),
        )
        ListItem(
            headlineContent = { Text("Record a transaction") },
            leadingContent = { Icon(Icons.Default.Payments, contentDescription = null) },
            modifier = Modifier.clickableRow(onTransaction),
        )
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit) = this.clickable(onClick = onClick)


@Composable
private fun BalanceHeader(
    balanceText: String,
    positive: Boolean,
    zero: Boolean,
    onClick: () -> Unit,
) {
    val ledger = PayChatTheme.ledger

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    zero -> "Settled up"
                    positive -> "They owe you"
                    else -> "You owe them"
                },
                style = MaterialTheme.typography.labelLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = balanceText,
                    style = AmountStyle,
                    color = when {
                        zero -> MaterialTheme.colorScheme.onSurfaceVariant
                        positive -> ledger.credit
                        else -> ledger.debit
                    },
                )
                IconButton(onClick = onClick) {
                    Text("Ledger", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    canSend: Boolean,
    recording: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (recording) {
            IconButton(onClick = onCancelRecording) {
                Icon(Icons.Default.Close, contentDescription = "Discard recording")
            }
            Text(
                "Recording…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f).padding(bottom = 14.dp),
            )
            FilledIconButton(onClick = onStopRecording, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Stop, contentDescription = "Send recording")
            }
            return@Row
        }

        IconButton(onClick = onAttach) {
            Icon(Icons.Default.Add, contentDescription = "Attach")
        }
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message") },
            maxLines = 5,
        )
        FilledIconButton(
            onClick = if (canSend) onSend else onStartRecording,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = if (canSend) Icons.AutoMirrored.Filled.Send else Icons.Default.Mic,
                contentDescription = if (canSend) "Send" else "Record a voice message",
            )
        }
    }
}
