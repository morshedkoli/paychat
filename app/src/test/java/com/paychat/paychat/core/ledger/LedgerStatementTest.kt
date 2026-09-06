package com.paychat.paychat.core.ledger

import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ALICE = "uidStatementAlice"
private const val BOB = "uidStatementBob"

private data class StatementEntry(
    val id: String,
    override val createdBy: String,
    override val direction: TxnDirection,
    override val amountMinor: Long,
    override val status: TxnStatus = TxnStatus.ACCEPTED,
    override val unconfirmed: Boolean = false,
) : LedgerEntry

class LedgerStatementTest {

    private val entries = listOf(
        StatementEntry("1", ALICE, TxnDirection.SENT, 100_000),
        StatementEntry("2", ALICE, TxnDirection.SENT, 50_000, status = TxnStatus.REJECTED),
        StatementEntry("3", ALICE, TxnDirection.RECEIVED, 30_000),
    )

    @Test
    fun `each line shows the balance as it stood at that point`() {
        val lines = LedgerStatement.build(entries, ALICE)
        assertEquals(
            listOf(Money(100_000), Money(100_000), Money(70_000)),
            lines.map { it.runningBalance },
        )
    }

    @Test
    fun `an entry that came to nothing leaves the running balance alone`() {
        val lines = LedgerStatement.build(entries, ALICE)
        val rejected = lines.single { it.entry.id == "2" }
        assertEquals(Money.ZERO, rejected.effect)
    }

    @Test
    fun `display order is reversed but the arithmetic is not`() {
        val oldestFirst = LedgerStatement.build(entries, ALICE)
        val newestFirst = LedgerStatement.build(entries, ALICE, newestFirst = true)

        assertEquals(oldestFirst.map { it.entry.id }.reversed(), newestFirst.map { it.entry.id })

        // The balance on a given entry must be the same whichever way the
        // statement is read, or the two orders would tell different stories.
        oldestFirst.forEach { line ->
            val same = newestFirst.single { it.entry.id == line.entry.id }
            assertEquals(line.runningBalance, same.runningBalance)
        }
    }

    @Test
    fun `the closing balance does not depend on the display order`() {
        assertEquals(
            LedgerStatement.closingBalance(entries, ALICE),
            LedgerStatement.closingBalance(entries.reversed(), ALICE),
        )
        assertEquals(Money(70_000), LedgerStatement.closingBalance(entries, ALICE))
    }

    @Test
    fun `both sides read the same statement mirrored`() {
        val alice = LedgerStatement.build(entries, ALICE)
        val bob = LedgerStatement.build(entries, BOB)

        alice.zip(bob).forEach { (a, b) ->
            assertEquals(-a.effect, b.effect)
            assertEquals(-a.runningBalance, b.runningBalance)
            assertTrue(a.viewerIsPayer != b.viewerIsPayer)
        }
    }

    @Test
    fun `an empty ledger has no lines and a zero balance`() {
        val lines = LedgerStatement.build(emptyList<StatementEntry>(), ALICE)
        assertTrue(lines.isEmpty())
        assertEquals(Money.ZERO, LedgerStatement.closingBalance(emptyList<StatementEntry>(), ALICE))
    }
}
