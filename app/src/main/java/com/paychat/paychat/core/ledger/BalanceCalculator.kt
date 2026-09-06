package com.paychat.paychat.core.ledger

import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money

/**
 * A single row the balance can be computed from. Deliberately a narrow
 * interface so both the Room entity and the Firestore DTO can satisfy it and
 * the arithmetic lives in exactly one place.
 */
interface LedgerEntry {
    val createdBy: String
    val direction: TxnDirection
    val amountMinor: Long
    val status: TxnStatus

    /** Inherited history that the receiving user has not reviewed yet. */
    val unconfirmed: Boolean
}

/**
 * Balance arithmetic. Pure, no Android or Firebase types, fully unit testable.
 *
 * Sign convention, from [viewerUid]'s point of view:
 *   positive - the other person owes the viewer
 *   negative - the viewer owes the other person
 */
object BalanceCalculator {

    /**
     * @return the signed effect of [entry] on [viewerUid]'s balance, or
     *   [Money.ZERO] when the entry does not count.
     */
    fun effectOn(entry: LedgerEntry, viewerUid: String): Money {
        if (!entry.status.affectsBalance) return Money.ZERO

        // Unconfirmed rows were written by someone else against this user before
        // they registered. They stay out of the viewer's balance until reviewed,
        // but still count for the person who recorded them.
        if (entry.unconfirmed && entry.createdBy != viewerUid) return Money.ZERO

        val viewerIsAuthor = entry.createdBy == viewerUid
        val viewerIsPayer = when (entry.direction) {
            TxnDirection.SENT -> viewerIsAuthor
            TxnDirection.RECEIVED -> !viewerIsAuthor
        }

        return if (viewerIsPayer) Money(entry.amountMinor) else Money(-entry.amountMinor)
    }

    /** Net balance of a whole conversation, from [viewerUid]'s point of view. */
    fun balanceOf(entries: List<LedgerEntry>, viewerUid: String): Money =
        entries.fold(Money.ZERO) { acc, e -> acc + effectOn(e, viewerUid) }

    /**
     * Running balance after each entry, in the order given. Used by the ledger
     * screen and the PDF statement, where every row shows the balance as of
     * that row.
     */
    fun runningBalance(entries: List<LedgerEntry>, viewerUid: String): List<Money> {
        var acc = Money.ZERO
        return entries.map { acc += effectOn(it, viewerUid); acc }
    }

    /**
     * The three figures shown on the home screen. Net alone hides symmetric
     * debt, so the two gross figures are always shown beside it.
     */
    fun summarise(threadBalances: Collection<Money>): BalanceSummary {
        var net = Money.ZERO
        var willGet = Money.ZERO
        var willGive = Money.ZERO
        threadBalances.forEach { b ->
            net += b
            if (b.isPositive) willGet += b
            if (b.isNegative) willGive += b.abs()
        }
        return BalanceSummary(net = net, willGet = willGet, willGive = willGive)
    }
}

data class BalanceSummary(
    val net: Money,
    val willGet: Money,
    val willGive: Money,
) {
    companion object {
        val EMPTY = BalanceSummary(Money.ZERO, Money.ZERO, Money.ZERO)
    }
}
