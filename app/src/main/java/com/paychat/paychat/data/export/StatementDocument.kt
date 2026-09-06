package com.paychat.paychat.data.export

import com.paychat.paychat.core.ledger.StatementLine
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.local.entity.TransactionEntity

/**
 * A statement, ready to be drawn.
 *
 * Separated from both the database and the renderer so that what a statement
 * says is decided in one place and drawn in another, and so the export can be
 * checked without a PDF.
 */
data class StatementDocument(
    val title: String,
    val generatedAt: Long,
    val sections: List<StatementSection>,
) {
    /** The sum of every section, which is what the account comes to overall. */
    val total: Money get() = sections.fold(Money.ZERO) { sum, section -> sum + section.closing }

    val isEmpty: Boolean get() = sections.all { it.lines.isEmpty() }
}

/**
 * One conversation's worth of a statement. An export of a single chat has one
 * of these; an export of everything has one per conversation.
 */
data class StatementSection(
    val counterparty: String,
    val phone: String,
    val lines: List<StatementLine<TransactionEntity>>,
    val closing: Money,
)
