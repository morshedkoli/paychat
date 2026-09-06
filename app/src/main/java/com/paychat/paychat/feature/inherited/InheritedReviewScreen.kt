package com.paychat.paychat.feature.inherited

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.ui.components.Timestamps
import com.paychat.paychat.ui.theme.AmountStyle
import com.paychat.paychat.ui.theme.PayChatTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InheritedReviewScreen(
    onBack: () -> Unit,
    viewModel: InheritedReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val ledger = PayChatTheme.ledger

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Money recorded before you joined") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {

            if (state.done) {
                Text(
                    "Nothing left to review.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                return@Column
            }

            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${state.peerName} recorded these while your number was not on " +
                            "PayChat. None of them count towards your balance until you " +
                            "accept them.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "If you accept everything",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = state.netIfAccepted.formatSigned(),
                            style = AmountStyle,
                            color = if (state.netIfAccepted.isNegative) {
                                ledger.debit
                            } else {
                                ledger.credit
                            },
                        )
                    }
                    Button(
                        onClick = viewModel::acceptAll,
                        enabled = !state.working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Accept all ${state.items.size}") }
                }
            }

            HorizontalDivider()

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.items, key = { it.transaction.txnId }) { item ->
                    InheritedRow(
                        item = item,
                        working = state.working,
                        onAccept = { viewModel.accept(item.transaction.txnId) },
                        onReject = { viewModel.reject(item.transaction.txnId) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun InheritedRow(
    item: InheritedItem,
    working: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    val ledger = PayChatTheme.ledger

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (item.viewerIsPayer) "You gave" else "You received",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = item.amount.format(),
                style = AmountStyle,
                color = if (item.viewerIsPayer) ledger.credit else ledger.debit,
            )
        }

        item.transaction.note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "Recorded ${Timestamps.daySeparator(item.transaction.createdAt)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAccept,
                enabled = !working,
                modifier = Modifier.weight(1f),
            ) { Text("Accept") }
            OutlinedButton(
                onClick = onReject,
                enabled = !working,
                modifier = Modifier.weight(1f),
            ) { Text("Reject") }
        }
    }
}
