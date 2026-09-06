package com.paychat.paychat.data.contacts

import com.paychat.paychat.core.phone.PhoneNumbers

/**
 * One entry read from the device address book, after normalisation.
 */
data class ContactCandidate(
    val phoneE164: String,
    val displayName: String,
)

/**
 * Turns raw address book rows into the set of numbers worth looking up.
 *
 * Kept separate from the ContentResolver so the rules can be tested without a
 * device: address books are full of duplicates, the same number written four
 * ways, entries that are not phone numbers at all, and the user's own number.
 */
object ContactNormaliser {

    /**
     * @param raw pairs of (display name, phone number as stored on the device)
     * @param ownUserPhone the signed in user's own number, which is dropped
     * @return one entry per distinct number, keeping the first name seen for it
     */
    fun normalise(
        raw: List<Pair<String, String>>,
        phoneNumbers: PhoneNumbers,
        ownUserPhone: String?,
    ): List<ContactCandidate> {
        val seen = LinkedHashMap<String, ContactCandidate>()
        for ((name, number) in raw) {
            val e164 = phoneNumbers.toE164(number) ?: continue
            if (e164 == ownUserPhone) continue
            val displayName = name.trim().ifEmpty { phoneNumbers.formatForDisplay(e164) }
            seen.putIfAbsent(e164, ContactCandidate(e164, displayName))
        }
        return seen.values.toList()
    }

    /**
     * Firestore accepts at most 30 values in a single `whereIn`, so lookups go
     * out in batches of that size.
     */
    const val LOOKUP_BATCH_SIZE = 30

    fun <T> batched(items: List<T>): List<List<T>> = items.chunked(LOOKUP_BATCH_SIZE)
}
