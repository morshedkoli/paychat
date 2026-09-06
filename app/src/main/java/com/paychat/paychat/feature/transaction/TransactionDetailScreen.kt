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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.ui.components.Timestamps
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
    val ledger = PayChatTheme.ledger
    var showReversal by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    if (showReversal) {
        ReversalDialog(
            onDismiss = { showReversal = false },
            onConfirm = { note ->
                showReversal = false
                viewModel.reverse(note)
            },
        )
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (state.viewerIsPayer) "You gave" else "You received",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

            HorizontalDivider()

            DetailRow("Status", statusText(transaction.status, transaction.unconfirmed))
            DetailRow("Recorded", Timestamps.daySeparator(transaction.createdAt))
            transaction.resolvedAt?.let { DetailRow("Settled", Timestamps.daySeparator(it)) }
            transaction.dueDate?.let { DetailRow("Due", Timestamps.daySeparator(it)) }
            transaction.note?.let { DetailRow("Note", it) }

            if (transaction.reversesId != null) {
                DetailRow("Type", "Correction of an earlier transaction")
            }
            if (transaction.reversedBy != null) {
                DetailRow("Type", "Corrected by a later transaction")
            }

            val photo = transaction.photoUrl ?: transaction.localPhotoPath?.let { File(it) }
            if (photo != null) {
                AsyncImage(
                    model = photo,
                    contentDescription = "Receipt",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            HorizontalDivider()

            if (state.canAccept) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::accept,
                        enabled = !state.working,
                        modifier = Modifier.weight(1f),
                    ) { Text("Accept") }
                    OutlinedButton(
                        onClick = viewModel::reject,
                        enabled = !state.working,
                        modifier = Modifier.weight(1f),
                    ) { Text("Reject") }
                }
            }

            if (state.canCancel) {
                OutlinedButton(
                    onClick = viewModel::cancel,
                    enabled = !state.working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Cancel this request") }
            }

            if (state.canReverse) {
                OutlinedButton(
                    onClick = { showReversal = true },
                    enabled = !state.working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Correct this") }
                Text(
                    "An accepted transaction is never edited or deleted. Correcting it adds " +
                        "an opposite entry, so both of you can see what changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
private fun ReversalDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Correct this transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This adds an opposite entry for the same amount. The original stays in " +
                        "the history, marked as corrected.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Why? (optional)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.ifBlank { null }) }) { Text("Correct") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
