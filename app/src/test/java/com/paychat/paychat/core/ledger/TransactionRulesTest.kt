package com.paychat.paychat.core.ledger

import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val AUTHOR = "uidAuthor"
private const val OTHER = "uidOther"

class TransactionRulesTest {

    @Test
    fun `a claim to have paid needs the other person to accept`() {
        assertEquals(
            TxnStatus.PENDING,
            TransactionRules.initialStatus(TxnDirection.SENT, isLocalThread = false),
        )
    }

    @Test
    fun `a claim to have been paid applies at once`() {
        // It only lowers what the other person owes, so there is nothing to
        // protect them from.
        assertEquals(
            TxnStatus.ACCEPTED,
            TransactionRules.initialStatus(TxnDirection.RECEIVED, isLocalThread = false),
        )
    }

    @Test
    fun `on a one-sided thread both directions apply and are unconfirmed`() {
        TxnDirection.entries.forEach { direction ->
            assertEquals(
                TxnStatus.ACCEPTED,
                TransactionRules.initialStatus(direction, isLocalThread = true),
            )
        }
        assertTrue(TransactionRules.initialUnconfirmed(isLocalThread = true))
        assertFalse(TransactionRules.initialUnconfirmed(isLocalThread = false))
    }

    @Test
    fun `only the counterparty may accept or reject`() {
        assertTrue(TransactionRules.canAccept(TxnStatus.PENDING, AUTHOR, OTHER))
        assertTrue(TransactionRules.canReject(TxnStatus.PENDING, AUTHOR, OTHER))

        // Accepting your own claim would let one person move the balance alone.
        assertFalse(TransactionRules.canAccept(TxnStatus.PENDING, AUTHOR, AUTHOR))
        assertFalse(TransactionRules.canReject(TxnStatus.PENDING, AUTHOR, AUTHOR))
    }

    @Test
    fun `only the author may cancel`() {
        assertTrue(TransactionRules.canCancel(TxnStatus.PENDING, AUTHOR, AUTHOR))
        assertFalse(TransactionRules.canCancel(TxnStatus.PENDING, AUTHOR, OTHER))
    }

    @Test
    fun `a settled transaction cannot be accepted, rejected or cancelled again`() {
        listOf(TxnStatus.ACCEPTED, TxnStatus.REJECTED, TxnStatus.CANCELLED).forEach { status ->
            assertFalse(TransactionRules.canAccept(status, AUTHOR, OTHER))
            assertFalse(TransactionRules.canReject(status, AUTHOR, OTHER))
            assertFalse(TransactionRules.canCancel(status, AUTHOR, AUTHOR))
        }
    }

    @Test
    fun `only an accepted transaction can be corrected, and only once`() {
        assertTrue(TransactionRules.canReverse(TxnStatus.ACCEPTED, alreadyReversed = false))
        assertFalse(TransactionRules.canReverse(TxnStatus.ACCEPTED, alreadyReversed = true))
        assertFalse(TransactionRules.canReverse(TxnStatus.PENDING, alreadyReversed = false))
        assertFalse(TransactionRules.canReverse(TxnStatus.REJECTED, alreadyReversed = false))
    }

    /**
     * Inherited history is accepted but still awaiting a decision. Correcting
     * it instead would put a review and a correction in the same write, which
     * the security rules refuse as one change.
     */
    @Test
    fun `inherited history is reviewed rather than corrected`() {
        assertFalse(
            TransactionRules.canReverse(
                TxnStatus.ACCEPTED,
                alreadyReversed = false,
                unconfirmed = true,
            )
        )
    }

    @Test
    fun `a correction carries the opposite direction`() {
        assertEquals(
            TxnDirection.RECEIVED,
            TransactionRules.reversalDirection(TxnDirection.SENT),
        )
        assertEquals(
            TxnDirection.SENT,
            TransactionRules.reversalDirection(TxnDirection.RECEIVED),
        )
    }

    @Test
    fun `inherited history is reviewed by the person who did not write it`() {
        assertTrue(TransactionRules.canReviewInherited(true, AUTHOR, OTHER))
        assertFalse(TransactionRules.canReviewInherited(true, AUTHOR, AUTHOR))
        assertFalse(TransactionRules.canReviewInherited(false, AUTHOR, OTHER))
    }

    @Test
    fun `a correction claims the entry it corrects as soon as it is recorded`() {
        val held = TransactionRules.correctionsHeld(
            listOf(
                CorrectionLink("original", null, TxnStatus.ACCEPTED),
                CorrectionLink("correction", "original", TxnStatus.PENDING),
            )
        )

        assertEquals(mapOf("original" to "correction"), held)
    }

    @Test
    fun `an accepted correction keeps holding it`() {
        val held = TransactionRules.correctionsHeld(
            listOf(
                CorrectionLink("original", null, TxnStatus.ACCEPTED),
                CorrectionLink("correction", "original", TxnStatus.ACCEPTED),
            )
        )

        assertEquals(mapOf("original" to "correction"), held)
    }

    /**
     * The defect this rule exists for: an entry whose correction was refused
     * used to keep pointing at it, which left it permanently uncorrectable
     * while still counting in full.
     */
    @Test
    fun `a refused or withdrawn correction releases it again`() {
        listOf(TxnStatus.REJECTED, TxnStatus.CANCELLED).forEach { outcome ->
            val held = TransactionRules.correctionsHeld(
                listOf(
                    CorrectionLink("original", null, TxnStatus.ACCEPTED),
                    CorrectionLink("correction", "original", outcome),
                )
            )

            assertEquals("a $outcome correction still held the entry", emptyMap<String, String>(), held)
        }
    }

    @Test
    fun `entries that correct nothing hold nothing`() {
        val held = TransactionRules.correctionsHeld(
            listOf(
                CorrectionLink("a", null, TxnStatus.ACCEPTED),
                CorrectionLink("b", null, TxnStatus.PENDING),
            )
        )

        assertTrue(held.isEmpty())
    }
}
