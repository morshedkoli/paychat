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
     */
    fun canReverse(status: TxnStatus, alreadyReversed: Boolean): Boolean =
        status == TxnStatus.ACCEPTED && !alreadyReversed

    /**
     * Inherited history: only the person who did not write it may confirm it,
     * and only while it is still unconfirmed.
     */
    fun canReviewInherited(unconfirmed: Boolean, createdBy: String, viewerUid: String): Boolean =
        unconfirmed && createdBy != viewerUid

    /** The direction a reversal must carry to undo [original]. */
    fun reversalDirection(original: TxnDirection): TxnDirection = original.opposite()
}
