package com.paychat.paychat.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.ledger.TransactionRules
import com.paychat.paychat.core.model.TransactionNote
import com.paychat.paychat.core.model.TxnCategory
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.ui.components.Timestamps
import com.paychat.paychat.ui.theme.AmountStyle
import com.paychat.paychat.ui.theme.PayChatTheme
import com.paychat.paychat.ui.theme.WhatsAppForestGreen
import com.paychat.paychat.ui.theme.WhatsAppTealGreen

/**
 * WhatsApp Pay-style digital transfer card inside chat.
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
    val isPayer = BalanceCalculator.viewerIsPayer(transaction, viewerUid)

    val accent = when {
        transaction.status == TxnStatus.PENDING -> ledger.pending
        !transaction.status.affectsBalance -> MaterialTheme.colorScheme.onSurfaceVariant
        effect.isPositive -> ledger.credit
        effect.isNegative -> ledger.debit
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val cardShape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .width(310.dp)
                .shadow(1.dp, cardShape)
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = cardShape,
                )
                .clickable(onClick = onOpen)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val parsedNote = remember(transaction.note) { TransactionNote.parse(transaction.note) }

            // Header row with payment icon and direction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPayer) ledger.debitContainer else ledger.creditContainer
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPayer) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = if (isPayer) ledger.debit else ledger.credit,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = if (isPayer) "You sent" else "You received",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (parsedNote.category != TxnCategory.GENERAL) {
                    Text(
                        text = parsedNote.category.displayName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            // Big Bold Amount
            Text(
                text = Money(transaction.amountMinor).format(),
                style = AmountStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                color = accent,
            )

            if (parsedNote.text.isNotBlank()) {
                Text(
                    text = parsedNote.text,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (parsedNote.trxId != null) {
                Text(
                    text = "TrxID: ${parsedNote.trxId}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f),
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            transaction.dueDate?.let { dueMillis ->
                val info = com.paychat.paychat.core.ledger.DueDateHelper.evaluate(dueMillis)
                val badgeColor = when (info.status) {
                    com.paychat.paychat.core.ledger.DueStatus.OVERDUE -> MaterialTheme.colorScheme.error
                    com.paychat.paychat.core.ledger.DueStatus.DUE_TODAY -> Color(0xFFE65100)
                    com.paychat.paychat.core.ledger.DueStatus.DUE_TOMORROW -> Color(0xFFF57C00)
                    com.paychat.paychat.core.ledger.DueStatus.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val badgeBg = when (info.status) {
                    com.paychat.paychat.core.ledger.DueStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    com.paychat.paychat.core.ledger.DueStatus.DUE_TODAY -> Color(0xFFFFE0B2)
                    com.paychat.paychat.core.ledger.DueStatus.DUE_TOMORROW -> Color(0xFFFFF3E0)
                    com.paychat.paychat.core.ledger.DueStatus.UPCOMING -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = info.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (info.isAlert) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                            fontSize = 11.sp,
                        ),
                        color = badgeColor,
                    )
                }
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            // Status and actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusBadge(transaction = transaction, mine = mine, accent = accent)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "Details",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (TransactionRules.canAccept(transaction.status, transaction.createdBy, viewerUid)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Button(
                        onClick = onAccept,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.weight(1f).height(36.dp),
                    ) {
                        Text("Accept", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onReject,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f).height(36.dp),
                    ) {
                        Text("Reject", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (
                TransactionRules.canCancel(transaction.status, transaction.createdBy, viewerUid)
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(
                        "Cancel request",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(transaction: TransactionEntity, mine: Boolean, accent: Color) {
    val (text, icon) = when (transaction.status) {
        TxnStatus.PENDING ->
            Pair(if (mine) "Pending" else "Action needed", Icons.Default.Pending)
        TxnStatus.ACCEPTED -> when {
            transaction.reversedBy != null -> Pair("Corrected", Icons.Default.CheckCircle)
            transaction.unconfirmed -> Pair("Applied", Icons.Default.CheckCircle)
            else -> Pair("Completed", Icons.Default.CheckCircle)
        }
        TxnStatus.REJECTED -> Pair("Rejected", Icons.Default.CheckCircle)
        TxnStatus.CANCELLED -> Pair("Cancelled", Icons.Default.CheckCircle)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            ),
            color = accent,
        )
    }
}
