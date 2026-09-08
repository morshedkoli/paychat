package com.paychat.paychat.feature.auth.phone

import com.paychat.paychat.data.auth.AuthError
import com.paychat.paychat.data.auth.AuthException
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneLookupOutcomeTest {

    @Test
    fun `a registered number goes to the password step`() {
        assertEquals(
            PhoneLookupOutcome.Registered,
            phoneLookupOutcome(Result.success(true)),
        )
    }

    @Test
    fun `an unregistered number offers registration`() {
        assertEquals(
            PhoneLookupOutcome.Unregistered,
            phoneLookupOutcome(Result.success(false)),
        )
    }

    @Test
    fun `a rate limited lookup reports too many attempts`() {
        val outcome = phoneLookupOutcome(
            Result.failure(AuthException(AuthError.TooManyRequests))
        )
        assertEquals(
            PhoneLookupOutcome.Failed("Too many attempts. Try again later."),
            outcome,
        )
    }

    @Test
    fun `a lookup with no connection says so`() {
        val outcome = phoneLookupOutcome(Result.failure(AuthException(AuthError.Network)))
        assertEquals(
            PhoneLookupOutcome.Failed("No connection. Check your internet and try again."),
            outcome,
        )
    }

    @Test
    fun `a failure that is not an AuthException still produces a message`() {
        val outcome = phoneLookupOutcome(Result.failure(IllegalStateException("boom")))
        assertEquals(PhoneLookupOutcome.Failed("boom"), outcome)
    }
}
