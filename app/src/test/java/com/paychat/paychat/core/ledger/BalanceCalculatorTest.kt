package com.paychat.paychat.core.ledger

import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ALICE = "uid_alice"
private const val BOB = "uid_bob"

private data class Entry(
    override val createdBy: String,
    override val direction: TxnDirection,
    override val amountMinor: Long,
    override val status: TxnStatus = TxnStatus.ACCEPTED,
    override val unconfirmed: Boolean = false,
) : LedgerEntry

class BalanceCalculatorTest {

    @Test
    fun `sent money is owed back to the sender`() {
        val e = Entry(ALICE, TxnDirection.SENT, 50_000)
        assertEquals(Money(50_000), BalanceCalculator.effectOn(e, ALICE))
        assertEquals(Money(-50_000), BalanceCalculator.effectOn(e, BOB))
    }

    @Test
    fun `received money reduces what the author is owed`() {
        // Alice records that she received 500 from Bob.
        val e = Entry(ALICE, TxnDirection.RECEIVED, 50_000)
        assertEquals(Money(-50_000), BalanceCalculator.effectOn(e, ALICE))
        assertEquals(Money(50_000), BalanceCalculator.effectOn(e, BOB))
    }

    @Test
    fun `only accepted transactions move the balance`() {
        listOf(TxnStatus.PENDING, TxnStatus.REJECTED, TxnStatus.CANCELLED).forEach { status ->
            val e = Entry(ALICE, TxnDirection.SENT, 10_000, status = status)
            assertEquals(Money.ZERO, BalanceCalculator.effectOn(e, ALICE))
            assertEquals(Money.ZERO, BalanceCalculator.effectOn(e, BOB))
        }
    }

    @Test
    fun `unconfirmed inherited history counts only for its author`() {
        val e = Entry(ALICE, TxnDirection.SENT, 30_000, unconfirmed = true)
        assertEquals(Money(30_000), BalanceCalculator.effectOn(e, ALICE))
        assertEquals(Money.ZERO, BalanceCalculator.effectOn(e, BOB))
    }

    @Test
    fun `balances of the two parties always mirror each other`() {
        val entries = listOf(
            Entry(ALICE, TxnDirection.SENT, 100_000),
            Entry(BOB, TxnDirection.SENT, 40_000),
            Entry(ALICE, TxnDirection.RECEIVED, 25_000),
        )
        val alice = BalanceCalculator.balanceOf(entries, ALICE)
        val bob = BalanceCalculator.balanceOf(entries, BOB)
        assertEquals(Money(35_000), alice)
        assertEquals(-alice, bob)
    }

    @Test
    fun `running balance reports the figure after each row`() {
        val entries = listOf(
            Entry(ALICE, TxnDirection.SENT, 10_000),
            Entry(ALICE, TxnDirection.SENT, 5_000),
            Entry(ALICE, TxnDirection.RECEIVED, 3_000),
        )
        assertEquals(
            listOf(Money(10_000), Money(15_000), Money(12_000)),
            BalanceCalculator.runningBalance(entries, ALICE)
        )
    }

    @Test
    fun `who paid is read from the direction, not from the balance`() {
        // A rejected claim still said who it claimed had paid, so the wording
        // in the interface must not depend on whether it counts.
        val rejected = Entry(ALICE, TxnDirection.SENT, 10_000, status = TxnStatus.REJECTED)
        assertTrue(BalanceCalculator.viewerIsPayer(rejected, ALICE))
        assertFalse(BalanceCalculator.viewerIsPayer(rejected, BOB))
    }

    @Test
    fun `each side reads the same entry from their own side`() {
        val received = Entry(ALICE, TxnDirection.RECEIVED, 10_000)
        assertFalse(BalanceCalculator.viewerIsPayer(received, ALICE))
        assertTrue(BalanceCalculator.viewerIsPayer(received, BOB))
    }

    @Test
    fun `summary separates gross owed from gross owing`() {
        val summary = BalanceCalculator.summarise(
            listOf(Money(50_000), Money(-50_000), Money(20_000))
        )
        assertEquals(Money(20_000), summary.net)
        assertEquals(Money(70_000), summary.willGet)
        assertEquals(Money(50_000), summary.willGive)
    }
}
