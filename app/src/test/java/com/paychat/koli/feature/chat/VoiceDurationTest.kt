package com.paychat.koli.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceDurationTest {

    @Test
    fun `seconds are padded so the times line up`() {
        assertEquals("0:07", formatDuration(7_000))
        assertEquals("1:04", formatDuration(64_000))
    }

    @Test
    fun `a part second rounds down rather than showing a fraction`() {
        assertEquals("0:01", formatDuration(1_900))
    }

    @Test
    fun `zero is shown rather than left blank`() {
        assertEquals("0:00", formatDuration(0))
    }

    @Test
    fun `long recordings keep counting in minutes`() {
        assertEquals("10:00", formatDuration(600_000))
    }
}
