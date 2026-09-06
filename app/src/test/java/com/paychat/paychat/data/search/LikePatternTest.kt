package com.paychat.paychat.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

class LikePatternTest {

    @Test
    fun `ordinary text is wrapped in wildcards`() {
        assertEquals("%rent%", LikePattern.containing("rent"))
    }

    @Test
    fun `a percent sign the user typed is matched literally`() {
        assertEquals("%10\\% fee%", LikePattern.containing("10% fee"))
    }

    @Test
    fun `an underscore matches only an underscore`() {
        assertEquals("%bill\\_2%", LikePattern.containing("bill_2"))
    }

    @Test
    fun `the escape character is escaped before the wildcards are`() {
        // A backslash the user typed must not turn the escaping that follows
        // it into part of the pattern.
        assertEquals("%a\\\\\\%b%", LikePattern.containing("a\\%b"))
    }
}
