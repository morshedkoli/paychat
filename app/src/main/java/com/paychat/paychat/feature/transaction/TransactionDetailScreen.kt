package com.paychat.paychat.feature.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.paychat.paychat.core.model.TransactionNote
import com.paychat.paychat.core.model.TxnCategory
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.ui.components.Timestamps
import com.paychat.paychat.ui.components.ZoomableImageViewer
import com.paychat.paychat.ui.theme.AmountLargeStyle
import com.paychat.paychat.ui.theme.PayChatTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val ledger = PayChatTheme.ledger
    var showPhotoViewer by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        val transaction = state.transaction
        if (transaction == null) {
            Column(Modifier.fillMaxSize().padding(inner).padding(24.dp)) {
                Text("That transaction is not on this device.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // WhatsApp Pay Digital Receipt Card
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Status Badge Pill
                    val isCompleted = transaction.status == TxnStatus.ACCEPTED
                    val isPending = transaction.status == TxnStatus.PENDING
                    val statusBg = when {
                        isCompleted -> ledger.credit.copy(alpha = 0.15f)
                        isPending -> ledger.pending.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    }
                    val statusTextColor = when {
                        isCompleted -> ledger.credit
                        isPending -> ledger.pending
                        else -> MaterialTheme.colorScheme.error
                    }

                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = statusBg,
                    ) {
                        Text(
                            text = statusText(transaction.status, transaction.unconfirmed),
                            style = MaterialTheme.typography.labelMedium,
                            color = statusTextColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }

                    androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))

                    // Amount
                    Text(
                        text = Money(transaction.amountMinor).format(),
                        style = AmountLargeStyle,
                        color = when (transaction.status) {
                            TxnStatus.PENDING -> ledger.pending
                            TxnStatus.ACCEPTED ->
                                if (state.viewerIsPayer) ledger.credit else ledger.debit
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )

                    Text(
                        text = if (state.viewerIsPayer) "You sent / gave" else "You received",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))

                    // Receipt details
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        DetailRow("Date", Timestamps.daySeparator(transaction.createdAt))
                        transaction.resolvedAt?.let { DetailRow("Settled", Timestamps.daySeparator(it)) }
                        transaction.dueDate?.let { dueMillis ->
                            val info = com.paychat.paychat.core.ledger.DueDateHelper.evaluate(dueMillis)
                            DetailRow("Due Date", "${Timestamps.daySeparator(dueMillis)} (${info.label})")
                        }

                        val parsedNote = remember(transaction.note) { TransactionNote.parse(transaction.note) }
                        if (parsedNote.category != TxnCategory.GENERAL) {
                            DetailRow("Category", parsedNote.category.displayName)
                        }
                        if (parsedNote.text.isNotBlank()) {
                            DetailRow("Note", parsedNote.text)
                        }
                        if (parsedNote.trxId != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "TrxID",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        parsedNote.trxId,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                    )
                                    androidx.compose.material3.Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        modifier = Modifier.clickable {
                                            clipboardManager.setText(AnnotatedString(parsedNote.trxId))
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("TrxID copied to clipboard")
                                            }
                                        },
                                    ) {
                                        Text(
                                            "Copy",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (transaction.reversesId != null) {
                            DetailRow("Type", "Correction of an earlier transaction")
                        }
                        if (transaction.reversedBy != null) {
                            DetailRow("Type", "Corrected by a later transaction")
                        }
                    }
                }
            }

            val photo = transaction.photoUrl ?: transaction.localPhotoPath?.let { File(it) }
            if (photo != null) {
                if (showPhotoViewer) {
                    ZoomableImageViewer(
                        model = photo,
                        onDismiss = { showPhotoViewer = false },
                        contentDescription = "Receipt photo",
                    )
                }

                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "Receipt Image",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        AsyncImage(
                            model = photo,
                            contentDescription = "Receipt (tap to zoom)",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showPhotoViewer = true },
                        )
                    }
                }
            }

            // Actions in WhatsApp button style
            if (state.showDecisionActions) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.Button(
                        onClick = viewModel::accept,
                        enabled = !state.working,
                        shape = RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) { Text("Accept", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                    androidx.compose.material3.OutlinedButton(
                        onClick = viewModel::reject,
                        enabled = !state.working,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f).height(46.dp),
                    ) { Text("Reject", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }
                }
            }

            if (state.canCancel) {
                androidx.compose.material3.OutlinedButton(
                    onClick = viewModel::cancel,
                    enabled = !state.working,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) { Text("Cancel this request") }
            }

            if (state.canSendReminder) {
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        viewModel.sendReminder { msg ->
                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                ) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Send Friendly Reminder (তাগাদা)")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

private fun statusText(status: TxnStatus, unconfirmed: Boolean): String = when (status) {
    TxnStatus.PENDING -> "Waiting to be accepted"
    TxnStatus.ACCEPTED ->
        if (unconfirmed) "Counted, not confirmed by them yet" else "Counted in the balance"
    TxnStatus.REJECTED -> "Rejected"
    TxnStatus.CANCELLED -> "Cancelled"
}
