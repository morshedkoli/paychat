package com.paychat.paychat.core.model

/**
 * The direction of a transaction, always from the point of view of the person
 * who created it.
 */
enum class TxnDirection {
    /** "I gave money to you." Increases what the counterparty owes, so it needs acceptance. */
    SENT,

    /** "I received money from you." Only reduces what the counterparty owes, so it applies at once. */
    RECEIVED;

    val requiresAcceptance: Boolean get() = this == SENT

    fun opposite(): TxnDirection = if (this == SENT) RECEIVED else SENT
}

enum class TxnStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELLED;

    val isTerminal: Boolean get() = this != PENDING
    val affectsBalance: Boolean get() = this == ACCEPTED

    /**
     * Whether a correction in this state still holds the entry it corrects.
     *
     * A correction is claimed the moment it is recorded, so the same entry
     * cannot be corrected twice while the first attempt is outstanding. One
     * that is refused or withdrawn releases the entry again.
     */
    val holdsCorrection: Boolean get() = this == PENDING || this == ACCEPTED
}

enum class MessageType {
    TEXT,
    IMAGE,
    VOICE,
    TXN,
    SYSTEM,
}

/**
 * Whether a locally created row has made it to the server yet.
 */
enum class SyncState {
    PENDING,
    UPLOADING,
    SYNCED,
    FAILED,
}
