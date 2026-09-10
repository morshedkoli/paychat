package com.paychat.paychat.feature.transactions

import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.ui.components.Timestamps

/** Which side of the ledger the feed is showing. */
enum class FeedFilter { ALL, YOU_GAVE, YOU_GOT }

/**
 * One transaction as the feed shows it. Everything that depends on who is
 * reading has already been resolved here, so the screen never has to know the
 * sign convention.
 */
data class FeedRow(
    val txnId: String,
    val threadId: String,
    val peerName: String,
    val amount: Money,
    /** True when the viewer handed the money over, whoever wrote the row. */
    val viewerIsPayer: Boolean,
    val note: String?,
    val createdAt: Long,
    /** Inherited history this viewer has not reviewed yet. */
    val unconfirmed: Boolean,
    /** Waiting on the other person, so it does not count yet. */
    val pending: Boolean,
)

data class FeedDay(val label: String, val rows: List<FeedRow>)

/**
 * Turns stored transactions into the feed. Pure on purpose: the sign, the
 * wording and the day boundaries are the parts worth testing, and none of them
 * need Android or a database.
 */
object TransactionFeed {

    fun rows(
        transactions: List<TransactionEntity>,
        threads: List<ThreadEntity>,
        viewerUid: String,
    ): List<FeedRow> {
        val nameByThread = threads.associate {
            it.threadId to it.peerName.ifBlank { it.peerPhone }
        }
        return transactions.map { txn ->
            FeedRow(
                txnId = txn.txnId,
                threadId = txn.threadId,
                // A transaction can arrive before its thread row does, so a
                // missing name is normal and must not drop the row.
                peerName = nameByThread[txn.threadId].orEmpty(),
                amount = Money(txn.amountMinor),
                viewerIsPayer = BalanceCalculator.viewerIsPayer(txn, viewerUid),
                note = txn.note,
                createdAt = txn.createdAt,
                unconfirmed = txn.unconfirmed && txn.createdBy != viewerUid,
                pending = !txn.status.isTerminal,
            )
        }
    }

    fun filter(rows: List<FeedRow>, filter: FeedFilter): List<FeedRow> = when (filter) {
        FeedFilter.ALL -> rows
        FeedFilter.YOU_GAVE -> rows.filter { it.viewerIsPayer }
        FeedFilter.YOU_GOT -> rows.filter { !it.viewerIsPayer }
    }

    /** Newest day first, and newest row first inside each day. */
    fun group(rows: List<FeedRow>, now: Long = System.currentTimeMillis()): List<FeedDay> =
        rows.sortedByDescending { it.createdAt }
            .groupBy { Timestamps.daySeparator(it.createdAt, now) }
            .map { (label, dayRows) -> FeedDay(label, dayRows) }
}
