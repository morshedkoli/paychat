package com.paychat.paychat.core.phone

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
    fun `the same digits mean different numbers in different countries`() {
        // Why the country has to be chosen rather than assumed: these digits
        // are a real number in both places, and guessing picks the wrong one.
        assertEquals("+442071838750", phoneNumbers.toE164("020 7183 8750", "GB"))
        assertEquals("+12027183875", phoneNumbers.toE164("202 718 3875", "US"))
    }

    @Test
    fun `a national number is rejected under the wrong country`() {
        assertNull(phoneNumbers.toE164("01712345678", "US"))
    }

    @Test
    fun `rejects a number that is not valid`() {
        assertNull(phoneNumbers.toE164("12345"))
        assertNull(phoneNumbers.toE164(""))
        assertNull(phoneNumbers.toE164("not a number"))
    }
}
