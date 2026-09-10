package com.paychat.paychat.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun `parses whole taka`() {
        assertEquals(Money(120000), Money.parse("1200"))
    }

    @Test
    fun `parses decimals and grouping separators`() {
        assertEquals(Money(120050), Money.parse("1,200.50"))
        assertEquals(Money(120050), Money.parse(" 1200.5 "))
    }

    @Test
    fun `rejects malformed input rather than guessing`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1.2.3"))
    }

    @Test
    fun `rejects more than two decimal places instead of rounding`() {
        assertNull(Money.parse("10.005"))
    }

    @Test
    fun `formats with grouping and two decimals`() {
        assertEquals("1,200.50", Money(120050).formatPlain())
        assertEquals("0.05", Money(5).formatPlain())
    }

    @Test
    fun `signed formatting marks direction`() {
        assertEquals("+" + Money.SYMBOL + "10.00", Money(1000).formatSigned())
        assertEquals("-" + Money.SYMBOL + "10.00", Money(-1000).formatSigned())
        assertEquals(Money.SYMBOL + "0.00", Money.ZERO.formatSigned())
    }

    @Test
    fun `arithmetic stays exact`() {
        val total = Money(10) + Money(20) + Money(1)
        assertEquals(Money(31), total)
        assertEquals(Money(-31), -total)
        assertEquals(Money(9), Money(31) - Money(22))
    }
}
