package com.paychat.paychat.core.ledger

import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val OWNER = "uidOwner"
private const val JOINER = "uidJoiner"

private data class Inherited(
    override val createdBy: String,
    override val direction: TxnDirection,
    override val amountMinor: Long,
    override val status: TxnStatus = TxnStatus.ACCEPTED,
    override val unconfirmed: Boolean = true,
) : LedgerEntry

/**
 * What happens to money recorded against a phone number before its owner had
 * an account.
 */
class HandoverTest {

    private val history = listOf(
        Inherited(OWNER, TxnDirection.SENT, 100_000),
        Inherited(OWNER, TxnDirection.SENT, 50_000),
    )

    @Test
    fun `nobody can load debt onto a number before its owner joins`() {
        // This is the whole reason inherited history starts unconfirmed. The
        // person who wrote it carries it; the new account does not, until they
        // have seen it and agreed.
        assertEquals(Money(150_000), BalanceCalculator.balanceOf(history, OWNER))
        assertEquals(Money.ZERO, BalanceCalculator.balanceOf(history, JOINER))
    }

    @Test
    fun `accepting brings an entry into the new user's balance`() {
        val reviewed = history.map { it.copy(unconfirmed = false) }
        assertEquals(Money(-150_000), BalanceCalculator.balanceOf(reviewed, JOINER))
        assertEquals(Money(150_000), BalanceCalculator.balanceOf(reviewed, OWNER))
    }

    @Test
    fun `rejecting stops an entry counting for either side`() {
        // A dispute has to remove the entry from the person who wrote it too,
        // or the two of them would permanently disagree about the balance.
        val rejected = history.map {
            it.copy(unconfirmed = false, status = TxnStatus.REJECTED)
        }
        assertEquals(Money.ZERO, BalanceCalculator.balanceOf(rejected, OWNER))
        assertEquals(Money.ZERO, BalanceCalculator.balanceOf(rejected, JOINER))
    }

    @Test
    fun `reviewing one entry leaves the others alone`() {
        val partly = listOf(
            history[0].copy(unconfirmed = false),
            history[1],
        )
        assertEquals(Money(-100_000), BalanceCalculator.balanceOf(partly, JOINER))
        assertEquals(Money(150_000), BalanceCalculator.balanceOf(partly, OWNER))
    }

    @Test
    fun `only the person who did not write an entry may review it`() {
        assertTrue(TransactionRules.canReviewInherited(true, OWNER, JOINER))
        assertFalse(TransactionRules.canReviewInherited(true, OWNER, OWNER))
    }

    @Test
    fun `an entry already reviewed cannot be reviewed again`() {
        assertFalse(TransactionRules.canReviewInherited(false, OWNER, JOINER))
    }
}
