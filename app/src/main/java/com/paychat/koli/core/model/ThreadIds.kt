package com.paychat.koli.core.model

/**
 * Thread identifiers are derived, never random, so that both parties compute
 * the same id for the same conversation without having to agree on one first.
 *
 * The parts are joined with an underscore, which means no part may contain
 * one. Firebase uids are 28 alphanumeric characters and contact ids are UUIDs,
 * so neither does. That assumption is checked rather than trusted: a malformed
 * part would otherwise produce an id that parses back as a different kind of
 * thread.
 */
object ThreadIds {

    private const val SEPARATOR = "_"
    private const val LOCAL_MARKER = "local"

    /**
     * A conversation between two registered users. The two uids are sorted so
     * that the id does not depend on who opens the chat.
     */
    fun direct(uidA: String, uidB: String): String {
        requirePart(uidA)
        requirePart(uidB)
        require(uidA != uidB) { "a thread needs two different people" }
        val (first, second) = if (uidA < uidB) uidA to uidB else uidB to uidA
        return first + SEPARATOR + second
    }

    /**
     * A one-sided conversation with someone who has not registered. It belongs
     * to [ownerUid] alone until that phone number joins.
     */
    fun local(ownerUid: String, contactId: String): String {
        requirePart(ownerUid)
        requirePart(contactId)
        return ownerUid + SEPARATOR + LOCAL_MARKER + SEPARATOR + contactId
    }

    /**
     * @return true when [threadId] was produced by [local]
     */
    fun isLocal(threadId: String): Boolean {
        val parts = threadId.split(SEPARATOR)
        return parts.size == 3 && parts[1] == LOCAL_MARKER
    }

    private fun requirePart(part: String) {
        require(part.isNotEmpty()) { "a thread id part cannot be empty" }
        require(!part.contains(SEPARATOR)) {
            "a thread id part cannot contain '$SEPARATOR': $part"
        }
    }
}
