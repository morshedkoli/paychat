package com.paychat.paychat.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.core.ledger.BalanceSummary
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.ui.components.EmptyState
import com.paychat.paychat.ui.theme.AmountLargeStyle
import com.paychat.paychat.ui.theme.AmountStyle
import com.paychat.paychat.ui.theme.PayChatTheme

@Composable
fun TransactionsScreen(
    onOpenTransaction: (String) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner)) {
            item { BalanceHero(state.summary, state.people) }
            item { FilterChips(state.filter, viewModel::setFilter) }

            if (state.days.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        icon = Icons.Default.SwapVert,
                        title = "No transactions yet",
                        body = "Money you record in a chat shows up here.",
                    )
                }
            }

            state.days.forEach { day ->
                item(key = "day-${day.label}") { DayHeading(day.label) }
                items(day.rows, key = { it.txnId }) { row ->
                    FeedRowItem(row, onClick = { onOpenTransaction(row.txnId) })
                }
            }

            // Reaching the end of the list is the request for another page.
            // Keying on the total row count (not the day count) means this
            // only re-fires when the list has actually grown, and it is only
            // emitted at all once there is something to be at the end of.
            if (state.days.isNotEmpty()) {
                val totalRows = state.days.sumOf { it.rows.size }
                item(key = "load-more") {
                    LaunchedEffect(totalRows) { viewModel.loadMore() }
                }
            }
        }
    }
}

@Composable
private fun BalanceHero(summary: BalanceSummary, people: Int) {
    val ledger = PayChatTheme.ledger

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(
                brush = Brush.linearGradient(listOf(ledger.heroStart, ledger.heroEnd)),
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        Box(Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = when {
                        summary.net.isZero -> "All settled"
                        summary.net.isPositive -> "You will get, net"
                        else -> "You will give, net"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = summary.net.abs().format(),
                    style = AmountLargeStyle,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    HeroStat(label = "Get", value = summary.willGet.format())
                    HeroStat(label = "Give", value = summary.willGive.format())
                    HeroStat(label = "People", value = people.toString())
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String) {
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FilterChips(current: FeedFilter, onChoose: (FeedFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = current == FeedFilter.ALL,
            onClick = { onChoose(FeedFilter.ALL) },
            label = { Text("All") },
        )
        FilterChip(
            selected = current == FeedFilter.YOU_GAVE,
            onClick = { onChoose(FeedFilter.YOU_GAVE) },
            label = { Text("You gave") },
        )
        FilterChip(
            selected = current == FeedFilter.YOU_GOT,
            onClick = { onChoose(FeedFilter.YOU_GOT) },
            label = { Text("You got") },
        )
    }
}

@Composable
private fun DayHeading(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FeedRowItem(row: FeedRow, onClick: () -> Unit) {
    val ledger = PayChatTheme.ledger
    val dimmed = row.unconfirmed || row.pending

    val supporting = buildList {
        row.note?.let { add(it) }
        if (row.unconfirmed) add("Awaiting review")
        if (row.pending) add("Waiting for them")
    }.joinToString(" · ")

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(row.peerName) },
        supportingContent = if (supporting.isNotBlank()) {
            { Text(supporting) }
        } else null,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (row.viewerIsPayer) ledger.creditContainer else ledger.debitContainer,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (row.viewerIsPayer) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = if (row.viewerIsPayer) ledger.credit else ledger.debit,
                )
            }
        },
        trailingContent = {
            val amountColor = if (row.viewerIsPayer) ledger.credit else ledger.debit
            val sign = if (row.viewerIsPayer) "+" else "−"
            CompositionLocalProvider(
                LocalContentColor provides if (dimmed) amountColor.copy(alpha = 0.5f) else amountColor,
            ) {
                Text(
                    text = sign + Money.SYMBOL + row.amount.abs().formatPlain(),
                    style = AmountStyle,
                    color = LocalContentColor.current,
                )
            }
        },
    )
}
