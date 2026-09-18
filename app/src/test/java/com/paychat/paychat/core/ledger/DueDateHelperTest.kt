package com.paychat.paychat.core.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DueDateHelperTest {

    @Test
    fun `overdue dates are correctly detected`() {
        val now = System.currentTimeMillis()
        val twoDaysAgo = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -2)
        }.timeInMillis

        val info = DueDateHelper.evaluate(twoDaysAgo, now)
        assertEquals(DueStatus.OVERDUE, info.status)
        assertTrue(info.isAlert)
        assertTrue(info.label.contains("Overdue"))
    }

    @Test
    fun `due today is correctly detected`() {
        val now = System.currentTimeMillis()
        val info = DueDateHelper.evaluate(now, now)
        assertEquals(DueStatus.DUE_TODAY, info.status)
        assertTrue(info.isAlert)
        assertEquals("Due today", info.label)
    }

    @Test
    fun `due tomorrow is correctly detected`() {
        val now = System.currentTimeMillis()
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val info = DueDateHelper.evaluate(tomorrow, now)
        assertEquals(DueStatus.DUE_TOMORROW, info.status)
        assertFalse(info.isAlert)
        assertEquals("Due tomorrow", info.label)
    }

    @Test
    fun `upcoming dates are marked normal`() {
        val now = System.currentTimeMillis()
        val fiveDaysLater = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 5)
        }.timeInMillis

        val info = DueDateHelper.evaluate(fiveDaysLater, now)
        assertEquals(DueStatus.UPCOMING, info.status)
        assertFalse(info.isAlert)
        assertTrue(info.label.startsWith("Due "))
    }
}
