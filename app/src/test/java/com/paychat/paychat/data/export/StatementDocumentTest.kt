package com.paychat.paychat.data.export

import com.paychat.paychat.core.ledger.StatementLine
import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.local.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementDocumentTest {

    @Test
    fun `the total is the sum of every section, sign included`() {
        val document = document(
            section("Rana", Money(5_000)),
            section("Sathi", Money(-2_000)),
        )

        assertEquals(Money(3_000), document.total)
    }

    @Test
    fun `a document with no lines anywhere is empty`() {
        assertTrue(document(section("Rana", Money.ZERO)).isEmpty)
    }

    @Test
    fun `a section with lines makes the document non-empty`() {
        val withLines = section("Rana", Money(100)).copy(lines = listOf(line()))
        assertFalse(document(withLines).isEmpty)
    }

    private fun line() = StatementLine(
        entry = TransactionEntity(
            txnId = "t1",
            threadId = "thread",
            createdBy = "me",
            direction = TxnDirection.SENT,
            amountMinor = 100,
            status = TxnStatus.ACCEPTED,
            createdAt = 0L,
        ),
        viewerIsPayer = true,
        effect = Money(100),
        runningBalance = Money(100),
    )

    private fun document(vararg sections: StatementSection) = StatementDocument(
        title = "Statement",
        generatedAt = 0L,
        sections = sections.toList(),
    )

    private fun section(name: String, closing: Money) = StatementSection(
        counterparty = name,
        phone = "+8801700000000",
        lines = emptyList(),
        closing = closing,
    )
}
