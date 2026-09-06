package com.paychat.koli.feature.chat

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.paychat.koli.ui.components.Avatar
import com.paychat.koli.ui.theme.AmountStyle
import com.paychat.koli.ui.theme.PayChatTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onAddTransaction: (String) -> Unit,
    onOpenLedger: (String) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

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
                items(state.messages, key = { it.messageId }) { message ->
                    MessageBubble(message = message, viewerUid = state.viewerUid)
                }
            }

            HorizontalDivider()

            Composer(
                draft = state.draft,
                canSend = state.canSend,
                onDraftChange = viewModel::onDraftChange,
                onSend = viewModel::send,
                onAddTransaction = { onAddTransaction(state.threadId) },
            )
        }
    }
}

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
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAddTransaction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onAddTransaction) {
            Icon(Icons.Default.Add, contentDescription = "Record a transaction")
        }
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message") },
            maxLines = 5,
        )
        FilledIconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
        }
    }
}
