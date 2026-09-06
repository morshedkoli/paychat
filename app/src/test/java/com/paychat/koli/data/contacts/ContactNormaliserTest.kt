package com.paychat.koli.data.contacts

import com.paychat.koli.core.phone.PhoneNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactNormaliserTest {

    private val phoneNumbers = PhoneNumbers()

    @Test
    fun `the same number written four ways becomes one contact`() {
        val raw = listOf(
            "Karim" to "01712345678",
            "Karim Uddin" to "+8801712345678",
            "Karim" to "0171 234 5678",
            "Karim" to "8801712345678",
        )
        val result = ContactNormaliser.normalise(raw, phoneNumbers, ownUserPhone = null)
        assertEquals(1, result.size)
        assertEquals("+8801712345678", result.first().phoneE164)
    }

    @Test
    fun `the first name seen for a number wins`() {
        val raw = listOf(
            "Karim" to "01712345678",
            "Karim Uddin" to "01712345678",
        )
        val result = ContactNormaliser.normalise(raw, phoneNumbers, ownUserPhone = null)
        assertEquals("Karim", result.single().displayName)
    }

    @Test
    fun `entries that are not phone numbers are dropped`() {
        val raw = listOf(
            "Karim" to "01712345678",
            "Reception" to "1234",
            "Nobody" to "not a number",
        )
        val result = ContactNormaliser.normalise(raw, phoneNumbers, ownUserPhone = null)
        assertEquals(1, result.size)
    }

    @Test
    fun `your own number is never offered as a contact`() {
        val raw = listOf(
            "Me" to "01712345678",
            "Karim" to "01712345679",
        )
        val result = ContactNormaliser.normalise(
            raw,
            phoneNumbers,
            ownUserPhone = "+8801712345678",
        )
        assertEquals(listOf("+8801712345679"), result.map { it.phoneE164 })
    }

    @Test
    fun `a nameless entry falls back to the formatted number`() {
        val raw = listOf("  " to "01712345678")
        val result = ContactNormaliser.normalise(raw, phoneNumbers, ownUserPhone = null)
        assertTrue(result.single().displayName.contains("1712"))
    }

    @Test
    fun `lookups are split into batches Firestore will accept`() {
        val phones = (1..75).map { "+88017123456$it" }
        val batches = ContactNormaliser.batched(phones)
        assertEquals(3, batches.size)
        assertTrue(batches.all { it.size <= ContactNormaliser.LOOKUP_BATCH_SIZE })
        assertEquals(phones, batches.flatten())
    }

    @Test
    fun `an empty address book produces no batches`() {
        assertTrue(ContactNormaliser.batched(emptyList<String>()).isEmpty())
    }
}
