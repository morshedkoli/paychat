package com.paychat.paychat.feature.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.core.ledger.StatementLine
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.ui.components.Timestamps
import com.paychat.paychat.ui.components.shareStatement
import com.paychat.paychat.ui.theme.AmountLargeStyle
import com.paychat.paychat.ui.theme.AmountStyle
import com.paychat.paychat.ui.theme.PayChatTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    onBack: () -> Unit,
    onOpenTransaction: (String) -> Unit,
    viewModel: LedgerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ledger = PayChatTheme.ledger
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statement) {
        state.statement?.let { uri ->
            shareStatement(context, uri, "Statement — " + state.peerName)
            viewModel.statementShared()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ledger", style = MaterialTheme.typography.titleMedium)
                        if (state.peerName.isNotBlank()) {
                            Text(
                                state.peerName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    IconButton(
                        onClick = viewModel::exportStatement,
                        enabled = !state.exporting && state.lines.isNotEmpty(),
                    ) {
                        if (state.exporting) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Share, contentDescription = "Export statement")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {

            ClosingBalance(state.closingBalance, state.peerName)

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.hiddenCount > 0) {
                        "${state.hiddenCount} entry that came to nothing is hidden"
                            .let { if (state.hiddenCount == 1) it else it.replace("entry that came", "entries that came") }
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
                TextButton(onClick = viewModel::toggleSettled) {
                    Text(if (state.showSettled) "Hide rejected" else "Show everything")
                }
            }

            HorizontalDivider()

            if (state.lines.isEmpty() && !state.loading) {
                Text(
                    "No money recorded with ${state.peerName} yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.lines, key = { it.entry.txnId }) { line ->
                    StatementRow(line = line, onClick = { onOpenTransaction(line.entry.txnId) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ClosingBalance(balance: Money, peerName: String) {
    val ledger = PayChatTheme.ledger

    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = when {
                    balance.isZero -> "Settled up"
                    balance.isPositive -> "$peerName owes you"
                    else -> "You owe $peerName"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = balance.abs().format(),
                style = AmountLargeStyle,
                color = when {
                    balance.isZero -> MaterialTheme.colorScheme.onPrimaryContainer
                    balance.isPositive -> ledger.credit
                    else -> ledger.debit
                },
            )
        }
    }
}

@Composable
private fun StatementRow(
    line: StatementLine<TransactionEntity>,
    onClick: () -> Unit,
) {
    val ledger = PayChatTheme.ledger
    val transaction = line.entry
    val counts = transaction.status.affectsBalance

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (line.viewerIsPayer) "You gave" else "You received",
                style = MaterialTheme.typography.bodyLarge,
            )
            transaction.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = statusLabel(transaction.status, transaction.reversedBy != null),
                style = MaterialTheme.typography.labelSmall,
                color = if (transaction.status == TxnStatus.PENDING) {
                    ledger.pending
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money(transaction.amountMinor).format(),
                style = AmountStyle,
                color = when {
                    !counts -> MaterialTheme.colorScheme.onSurfaceVariant
                    line.viewerIsPayer -> ledger.credit
                    else -> ledger.debit
                },
                textDecoration = if (counts) null else TextDecoration.LineThrough,
            )
            // The balance as it stood after this entry, which is what makes a
            // statement readable rather than a list of amounts.
            Text(
                text = if (counts) line.runningBalance.formatSigned() else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Timestamps.daySeparator(transaction.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun statusLabel(status: TxnStatus, reversed: Boolean): String = when (status) {
    TxnStatus.PENDING -> "Waiting to be accepted"
    TxnStatus.ACCEPTED -> if (reversed) "Corrected later" else "Counted"
    TxnStatus.REJECTED -> "Rejected"
    TxnStatus.CANCELLED -> "Cancelled"
}
