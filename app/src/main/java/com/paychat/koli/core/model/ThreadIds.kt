package com.paychat.koli.core.model

/**
 * Thread identifiers are derived, never random, so that both parties compute
 * the same id for the same conversation without having to agree on one first.
 */
object ThreadIds {

    private const val SEPARATOR = "_"
    private const val LOCAL_MARKER = "local"

    /**
     * A conversation between two registered users. The two uids are sorted so
     * that the id does not depend on who opens the chat.
     */
    fun direct(uidA: String, uidB: String): String {
        require(uidA != uidB) { "a thread needs two different people" }
        val (first, second) = if (uidA < uidB) uidA to uidB else uidB to uidA
        return first + SEPARATOR + second
    }

    /**
     * A one-sided conversation with someone who has not registered. It belongs
     * to [ownerUid] alone until that phone number joins.
     */
    fun local(ownerUid: String, contactId: String): String =
        ownerUid + SEPARATOR + LOCAL_MARKER + SEPARATOR + contactId

    fun isLocal(threadId: String): Boolean =
        threadId.split(SEPARATOR).getOrNull(1) == LOCAL_MARKER
}
