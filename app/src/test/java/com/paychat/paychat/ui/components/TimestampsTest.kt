package com.paychat.paychat.ui.components

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private val now = at(2026, Calendar.MARCH, 15, hour = 18)

    @Test
    fun `today shows a clock time`() {
        val label = Timestamps.forThreadList(at(2026, Calendar.MARCH, 15, hour = 9), now)
        assertTrue(label, label.contains("9"))
    }

    @Test
    fun `yesterday is named rather than dated`() {
        assertEquals(
            "Yesterday",
            Timestamps.forThreadList(at(2026, Calendar.MARCH, 14), now),
        )
    }

    @Test
    fun `earlier this year shows day and month without the year`() {
        val label = Timestamps.forThreadList(at(2026, Calendar.JANUARY, 3), now)
        assertTrue(label, label.contains("3"))
        assertFalse(label, label.contains("2026"))
    }

    @Test
    fun `a previous year includes the year`() {
        val label = Timestamps.forThreadList(at(2025, Calendar.DECEMBER, 31), now)
        assertTrue(label, label.contains("2025"))
    }

    @Test
    fun `a thread with no messages has no timestamp`() {
        assertEquals("", Timestamps.forThreadList(0L, now))
    }

    @Test
    fun `two times on the same day are the same day, one second apart across midnight is not`() {
        val lateNight = at(2026, Calendar.MARCH, 15, hour = 23, minute = 59)
        val earlyMorning = at(2026, Calendar.MARCH, 16, hour = 0, minute = 0)
        assertTrue(Timestamps.isSameDay(lateNight, at(2026, Calendar.MARCH, 15, hour = 1)))
        assertFalse(Timestamps.isSameDay(lateNight, earlyMorning))
    }

    @Test
    fun `the day separator names today`() {
        assertEquals("Today", Timestamps.daySeparator(now, now))
    }
}
