package com.paychat.paychat.core.ledger

import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus

/**
 * Who may do what to a transaction, and what state a new one starts in.
 *
 * These are the same rules the Firestore security rules enforce. They live
 * here as well so the UI can grey out an action rather than letting the user
 * tap it and get a permission error, and so they can be tested without a
 * server. The server remains the authority: this copy is for the interface,
 * never for trust.
 */
object TransactionRules {

    /**
     * What a newly created transaction starts as.
     *
     * A `SENT` claim raises what the other person owes, so they have to accept
     * it. A `RECEIVED` claim only lowers it, so there is nothing to protect
     * against and it applies at once.
     *
     * On a one-sided thread there is nobody to accept, so both directions
     * apply immediately and are marked unconfirmed until that person joins and
     * reviews them.
     */
    fun initialStatus(direction: TxnDirection, isLocalThread: Boolean): TxnStatus = when {
        isLocalThread -> TxnStatus.ACCEPTED
        direction.requiresAcceptance -> TxnStatus.PENDING
        else -> TxnStatus.ACCEPTED
    }

    fun initialUnconfirmed(isLocalThread: Boolean): Boolean = isLocalThread

    /** The counterparty, and only the counterparty, decides on a pending item. */
    fun canAccept(status: TxnStatus, createdBy: String, viewerUid: String): Boolean =
        status == TxnStatus.PENDING && createdBy != viewerUid

    fun canReject(status: TxnStatus, createdBy: String, viewerUid: String): Boolean =
        canAccept(status, createdBy, viewerUid)

    /** The author may withdraw their own request until it is acted on. */
    fun canCancel(status: TxnStatus, createdBy: String, viewerUid: String): Boolean =
        status == TxnStatus.PENDING && createdBy == viewerUid

    /**
     * An accepted transaction is never edited or deleted. Either party may
     * correct it with a reversing entry, which follows the normal acceptance
     * rules for its own direction.
     *
     * Inherited history is excluded even though it is accepted. It is waiting
     * to be accepted or rejected, which is the decision that belongs to it;
     * correcting it instead would mean one write carrying both a review and a
     * correction, which the security rules refuse as a single change.
     */
    fun canReverse(
        status: TxnStatus,
        alreadyReversed: Boolean,
        unconfirmed: Boolean = false,
    ): Boolean = status == TxnStatus.ACCEPTED && !alreadyReversed && !unconfirmed

    /**
     * Inherited history: only the person who did not write it may confirm it,
     * and only while it is still unconfirmed.
     */
    fun canReviewInherited(unconfirmed: Boolean, createdBy: String, viewerUid: String): Boolean =
        unconfirmed && createdBy != viewerUid

    /** The direction a reversal must carry to undo [original]. */
    fun reversalDirection(original: TxnDirection): TxnDirection = original.opposite()

    /**
     * Which correction, if any, is holding each corrected entry.
     *
     * The link is written on the correction, as `reversesId`, because that is
     * the row the author creates. The pointer back the other way is derived
     * from it here rather than remembered, so that a correction the
     * counterparty refuses releases the entry it was correcting: an entry
     * still carrying a stale pointer could never be corrected a second time.
     *
     * @return the corrected entry's id, to the id of the correction claiming it
     */
    fun correctionsHeld(entries: List<CorrectionLink>): Map<String, String> = entries
        .filter { it.correctsId != null && it.status.holdsCorrection }
        .associate { it.correctsId!! to it.id }
}

/**
 * One entry reduced to what the correction rule needs from it. Narrow on
 * purpose, so the rule can be exercised without a database row.
 */
data class CorrectionLink(
    val id: String,
    val correctsId: String?,
    val status: TxnStatus,
)
