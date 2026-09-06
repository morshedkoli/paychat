package com.paychat.paychat.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SyntheticEmailTest {

    @Test
    fun `derives a stable address from the number`() {
        assertEquals(
            "p8801712345678@${SyntheticEmail.DOMAIN}",
            SyntheticEmail.forPhone("+8801712345678"),
        )
    }

    @Test
    fun `the same number always gives the same address`() {
        val a = SyntheticEmail.forPhone("+8801712345678")
        val b = SyntheticEmail.forPhone("+8801712345678")
        assertEquals(a, b)
    }

    @Test
    fun `different numbers never collide`() {
        val a = SyntheticEmail.forPhone("+8801712345678")
        val b = SyntheticEmail.forPhone("+8801712345679")
        assert(a != b)
    }

    @Test
    fun `rejects anything that is not E164`() {
        listOf("01712345678", "+", "+88017abc", "").forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) {
                SyntheticEmail.forPhone(bad)
            }
        }
    }
}
