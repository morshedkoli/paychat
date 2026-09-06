package com.paychat.koli.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.koli.core.money.Money
import com.paychat.koli.ui.components.Avatar
import com.paychat.koli.ui.components.Timestamps
import com.paychat.koli.ui.theme.AmountLargeStyle
import com.paychat.koli.ui.theme.AmountStyle
import com.paychat.koli.ui.theme.PayChatTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenThread: (String) -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PayChat") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewChat) {
                Icon(Icons.Default.Chat, contentDescription = "Start a chat")
            }
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {

            BalanceSummaryCard(
                net = state.summary.net,
                willGet = state.summary.willGet,
                willGive = state.summary.willGive,
            )

            HorizontalDivider()

            if (state.threads.isEmpty() && !state.loading) {
                Text(
                    "No conversations yet. Tap the button to start one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.threads, key = { it.threadId }) { thread ->
                    ThreadListItem(thread = thread, onClick = { onOpenThread(thread.threadId) })
                }
            }
        }
    }
}

/**
 * Net alone would hide symmetric debt, so what the user is owed and what they
 * owe are always shown beside it.
 */
@Composable
private fun BalanceSummaryCard(net: Money, willGet: Money, willGive: Money) {
    val ledger = PayChatTheme.ledger

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Overall balance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = net.formatSigned(),
                style = AmountLargeStyle,
                color = when {
                    net.isPositive -> ledger.credit
                    net.isNegative -> ledger.debit
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SummaryFigure("You will get", willGet, ledger.credit)
                SummaryFigure("You will give", willGive, ledger.debit)
            }
        }
    }
}

@Composable
private fun SummaryFigure(label: String, amount: Money, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(amount.format(), style = AmountStyle, color = color)
    }
}

@Composable
private fun ThreadListItem(thread: ThreadRow, onClick: () -> Unit) {
    val ledger = PayChatTheme.ledger

    ListItem(
        headlineContent = {
            Text(
                thread.name,
                fontWeight = if (thread.unreadCount > 0) FontWeight.SemiBold else null,
            )
        },
        supportingContent = {
            Text(
                text = thread.lastMessage.ifBlank {
                    if (thread.isLocal) "Not on PayChat yet" else "No messages yet"
                },
                maxLines = 1,
            )
        },
        leadingContent = {
            Avatar(name = thread.name, key = thread.phone, photoUrl = thread.photoUrl)
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Timestamps.forThreadList(thread.lastMessageAt),
                    style = MaterialTheme.typography.labelSmall,
                )
                if (!thread.balance.isZero) {
                    Text(
                        text = thread.balance.formatSigned(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (thread.balance.isPositive) ledger.credit else ledger.debit,
                    )
                }
                if (thread.unreadCount > 0) {
                    Badge { Text(thread.unreadCount.toString()) }
                }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
