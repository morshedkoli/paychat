package com.paychat.paychat.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.paychat.paychat.ui.theme.LocalHideBalances
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paychat.paychat.core.ledger.BalanceSummary
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.ui.components.EmptyState
import com.paychat.paychat.ui.theme.AmountLargeStyle
import com.paychat.paychat.ui.theme.AmountStyle
import com.paychat.paychat.ui.theme.PayChatTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    onOpenTransaction: (String) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Payments",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                actions = {
                    val hideBalances = LocalHideBalances.current
                    IconButton(onClick = viewModel::toggleAnalytics) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = "Analytics",
                            tint = if (state.showAnalytics) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.toggleHideBalances(hideBalances) }) {
                        Icon(
                            imageVector = if (hideBalances) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (hideBalances) "Show balances" else "Hide balances",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            item { BalanceHero(state.summary, state.people) }
            if (state.showAnalytics && !state.analytics.isEmpty) {
                item { SpendingAnalyticsSection(state.analytics) }
            }
            item { FilterChips(state.filter, viewModel::setFilter) }

            if (state.days.isEmpty() && !state.loading) {
                item {
                    EmptyState(
                        icon = Icons.Default.Payments,
                        title = "No payments yet",
                        body = "Transactions you send or receive in chats will appear here.",
                    )
                }
            }

            state.days.forEach { day ->
                item(key = "day-${day.label}") { DayHeading(day.label) }
                items(day.rows, key = { it.txnId }) { row ->
                    FeedRowItem(row, onClick = { onOpenTransaction(row.txnId) })
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }

            if (state.days.isNotEmpty()) {
                val totalRows = state.days.sumOf { it.rows.size }
                item(key = "load-more") {
                    LaunchedEffect(totalRows) { viewModel.loadMore() }
                }
            }
        }
    }
}

/**
 * WhatsApp Pay-style balance hero card.
 */
@Composable
private fun BalanceHero(summary: BalanceSummary, people: Int) {
    val ledger = PayChatTheme.ledger
    val hideBalances = LocalHideBalances.current
    val heroShape = RoundedCornerShape(16.dp)

    Card(
        shape = heroShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = heroShape,
            ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = when {
                    summary.net.isZero -> "Total Balance · Settled up"
                    summary.net.isPositive -> "Total Balance · You are owed"
                    else -> "Total Balance · You owe"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (hideBalances) Money.MASKED else summary.net.abs().format(),
                style = AmountLargeStyle.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = when {
                    summary.net.isZero -> MaterialTheme.colorScheme.onSurface
                    summary.net.isPositive -> ledger.credit
                    else -> ledger.debit
                },
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HeroStat(label = "To Receive", value = if (hideBalances) Money.MASKED else summary.willGet.format(), color = ledger.credit)
                HeroStat(label = "To Pay", value = if (hideBalances) Money.MASKED else summary.willGive.format(), color = ledger.debit)
                HeroStat(label = "Contacts", value = people.toString(), color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}


@Composable
private fun HeroStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterChips(current: FeedFilter, onChoose: (FeedFilter) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = current == FeedFilter.ALL,
            onClick = { onChoose(FeedFilter.ALL) },
            label = { Text("All") },
            shape = RoundedCornerShape(16.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
            ),
        )
        FilterChip(
            selected = current == FeedFilter.YOU_GAVE,
            onClick = { onChoose(FeedFilter.YOU_GAVE) },
            label = { Text("Sent") },
            shape = RoundedCornerShape(16.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
            ),
        )
        FilterChip(
            selected = current == FeedFilter.YOU_GOT,
            onClick = { onChoose(FeedFilter.YOU_GOT) },
            label = { Text("Received") },
            shape = RoundedCornerShape(16.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                selectedLabelColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun DayHeading(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun FeedRowItem(row: FeedRow, onClick: () -> Unit) {
    val ledger = PayChatTheme.ledger
    val dimmed = row.unconfirmed || row.pending
    val direction = if (row.viewerIsPayer) "Sent" else "Received"

    val supporting = buildList {
        add(direction)
        row.note?.let { if (it.isNotBlank()) add(it) }
        if (row.unconfirmed) add("Awaiting review")
        if (row.pending) add("Waiting for them")
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    color = if (row.viewerIsPayer) ledger.debitContainer else ledger.creditContainer
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (row.viewerIsPayer) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = direction,
                tint = if (row.viewerIsPayer) ledger.debit else ledger.credit,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.peerName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        val amountColor = if (row.viewerIsPayer) ledger.debit else ledger.credit
        val sign = if (row.viewerIsPayer) "-" else "+"
        val hideBalances = LocalHideBalances.current
        Text(
            text = if (hideBalances) Money.MASKED else (sign + Money.SYMBOL + row.amount.abs().formatPlain()),
            style = AmountStyle.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
            color = if (dimmed) amountColor.copy(alpha = 0.5f) else amountColor,
        )
    }
}

@Composable
private fun SpendingAnalyticsSection(analytics: SpendingAnalytics) {
    val ledger = PayChatTheme.ledger
    val hideBalances = LocalHideBalances.current
    val shape = RoundedCornerShape(16.dp)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = shape,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Spending & Activity Breakdown",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${analytics.categories.size} categories",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Cashflow ratio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Total Given", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (hideBalances) Money.MASKED else analytics.totalGiven.format(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = ledger.debit,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total Received", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (hideBalances) Money.MASKED else analytics.totalReceived.format(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = ledger.credit,
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

            // Category rows
            analytics.categories.take(5).forEach { catSpending ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = catSpending.category.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "(${catSpending.count})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = if (hideBalances) Money.MASKED else catSpending.totalAmount.format(),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { catSpending.percentage },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
}
