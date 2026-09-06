package com.paychat.paychat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.ledger.TransactionRules
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.ui.components.Timestamps
import com.paychat.paychat.ui.theme.AmountStyle
import com.paychat.paychat.ui.theme.PayChatTheme

/**
 * A transaction as it appears in the conversation.
 *
 * The wording is written from the reader's point of view rather than the
 * author's, because "I gave money" on the other person's screen has to read as
 * "they gave you money" or the ledger is impossible to follow.
 */
@Composable
fun TransactionBubble(
    transaction: TransactionEntity,
    viewerUid: String,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ledger = PayChatTheme.ledger
    val effect = BalanceCalculator.effectOn(transaction, viewerUid)
    val mine = transaction.createdBy == viewerUid

    val accent = when {
        transaction.status == TxnStatus.PENDING -> ledger.pending
        !transaction.status.affectsBalance -> MaterialTheme.colorScheme.onSurfaceVariant
        effect.isPositive -> ledger.credit
        effect.isNegative -> ledger.debit
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = headline(transaction, viewerUid),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Money(transaction.amountMinor).format(),
                style = AmountStyle,
                color = accent,
            )

            transaction.note?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            transaction.dueDate?.let {
                Text(
                    "Due ${Timestamps.daySeparator(it)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            StatusLine(transaction = transaction, mine = mine, accent = accent)

            if (TransactionRules.canAccept(transaction.status, transaction.createdBy, viewerUid)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAccept, modifier = Modifier.weight(1f)) { Text("Accept") }
                    OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f)) {
                        Text("Reject")
                    }
                }
            } else if (
                TransactionRules.canCancel(transaction.status, transaction.createdBy, viewerUid)
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancel request")
                }
            }
        }
    }
}

@Composable
private fun StatusLine(transaction: TransactionEntity, mine: Boolean, accent: Color) {
    val text = when (transaction.status) {
        TxnStatus.PENDING ->
            if (mine) "Waiting for them to accept" else "Waiting for you to accept"
        TxnStatus.ACCEPTED -> when {
            transaction.reversedBy != null -> "Corrected later"
            transaction.unconfirmed -> "Applied, not confirmed by them yet"
            else -> "Counted in the balance"
        }
        TxnStatus.REJECTED -> "Rejected"
        TxnStatus.CANCELLED -> "Cancelled"
    }

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (transaction.status == TxnStatus.PENDING) {
            accent
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/**
 * Says who paid whom, in the reader's own terms. Holds whatever the status is,
 * because a rejected claim still said who it claimed had paid.
 */
private fun headline(transaction: TransactionEntity, viewerUid: String): String =
    if (BalanceCalculator.viewerIsPayer(transaction, viewerUid)) "You gave" else "You received"
