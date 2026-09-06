package com.paychat.paychat.core.ledger

import com.paychat.paychat.core.money.Money

/**
 * One line of a statement: what happened, and what the balance stood at
 * afterwards.
 */
data class StatementLine<T : LedgerEntry>(
    val entry: T,
    val viewerIsPayer: Boolean,
    /** The signed effect of this entry alone, zero when it does not count. */
    val effect: Money,
    /** The balance after this entry, counting every entry up to and including it. */
    val runningBalance: Money,
)

/**
 * Builds the statement shown on the ledger screen and, later, exported to PDF.
 *
 * Pure, so the running balance can be tested without a database, and so the
 * screen and the export can never disagree about what the ledger says.
 */
object LedgerStatement {

    /**
     * @param entries in the order they were recorded, oldest first
     * @param newestFirst true to return the lines in display order, which is
     *   newest at the top; the running balance is still accumulated oldest
     *   first, so each line shows the balance as it stood at that moment
     */
    fun <T : LedgerEntry> build(
        entries: List<T>,
        viewerUid: String,
        newestFirst: Boolean = false,
    ): List<StatementLine<T>> {
        var running = Money.ZERO
        val lines = entries.map { entry ->
            val effect = BalanceCalculator.effectOn(entry, viewerUid)
            running += effect
            StatementLine(
                entry = entry,
                viewerIsPayer = BalanceCalculator.viewerIsPayer(entry, viewerUid),
                effect = effect,
                runningBalance = running,
            )
        }
        return if (newestFirst) lines.asReversed() else lines
    }

    /**
     * The balance the statement closes at, whichever order the lines are in.
     *
     * Taken from the accumulated total rather than from the first or last
     * line, so reversing the display order cannot change the answer.
     */
    fun <T : LedgerEntry> closingBalance(entries: List<T>, viewerUid: String): Money =
        BalanceCalculator.balanceOf(entries, viewerUid)
}
