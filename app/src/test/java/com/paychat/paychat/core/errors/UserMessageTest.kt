package com.paychat.paychat.core.errors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The Firebase exception branches are not covered here: their status enums
 * cannot be initialised off a device, so exercising them would need an
 * instrumented test rather than this one.
 */
class UserMessageTest {

    @Test
    fun `a dropped connection reads as being offline`() {
        val message = IOException("socket closed").userMessage("fallback")
        assertTrue(message.startsWith("You are offline"))
    }

    @Test
    fun `the app's own wording is kept`() {
        val error = IllegalStateException("That picture could not be read.")
        assertEquals("That picture could not be read.", error.userMessage("fallback"))
    }

    @Test
    fun `a developer-facing message falls back to the caller's wording`() {
        val error = IllegalStateException("java.lang.NullPointerException at line 12")
        assertEquals("fallback", error.userMessage("fallback"))
    }

    @Test
    fun `an exception with no message at all falls back`() {
        assertEquals("fallback", RuntimeException().userMessage("fallback"))
    }
}
