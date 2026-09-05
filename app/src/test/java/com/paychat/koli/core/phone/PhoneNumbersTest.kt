package com.paychat.koli.core.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumbersTest {

    private val phoneNumbers = PhoneNumbers()

    @Test
    fun `normalises a local Bangladeshi number`() {
        assertEquals("+8801712345678", phoneNumbers.toE164("01712345678"))
    }

    @Test
    fun `normalises a number that already has a country code`() {
        assertEquals("+8801712345678", phoneNumbers.toE164("+8801712345678"))
    }

    @Test
    fun `ignores spaces and dashes`() {
        assertEquals("+8801712345678", phoneNumbers.toE164("0171 234-5678"))
    }

    @Test
    fun `every accepted form maps to one identity`() {
        val forms = listOf("01712345678", "+8801712345678", "0171 234 5678", "8801712345678")
        val normalised = forms.mapNotNull { phoneNumbers.toE164(it) }.toSet()
        assertEquals(setOf("+8801712345678"), normalised)
    }

    @Test
    fun `rejects a number that is not valid`() {
        assertNull(phoneNumbers.toE164("12345"))
        assertNull(phoneNumbers.toE164(""))
        assertNull(phoneNumbers.toE164("not a number"))
    }
}
