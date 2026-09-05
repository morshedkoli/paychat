package com.paychat.koli.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun `accepts an ordinary name`() {
        assertNull(Validators.validateName("Murshed Koli"))
    }

    @Test
    fun `rejects a blank name`() {
        assertEquals(NameError.EMPTY, Validators.validateName("   "))
    }

    @Test
    fun `rejects an over long name`() {
        val long = "a".repeat(Validators.MAX_NAME_LENGTH + 1)
        assertEquals(NameError.TOO_LONG, Validators.validateName(long))
    }

    @Test
    fun `accepts a reasonable password`() {
        assertNull(Validators.validatePassword("taka9taka"))
    }

    @Test
    fun `rejects a short password`() {
        assertEquals(PasswordError.TOO_SHORT, Validators.validatePassword("abc123"))
    }

    @Test
    fun `rejects a digits only password because the phone number is guessable`() {
        assertEquals(PasswordError.DIGITS_ONLY, Validators.validatePassword("017123456"))
    }

    @Test
    fun `rejects the most common passwords`() {
        assertEquals(PasswordError.TOO_COMMON, Validators.validatePassword("password"))
    }

    @Test
    fun `otp must be six digits`() {
        assertTrue(Validators.isValidOtp("123456"))
        assertFalse(Validators.isValidOtp("12345"))
        assertFalse(Validators.isValidOtp("1234567"))
        assertFalse(Validators.isValidOtp("12345a"))
    }
}
