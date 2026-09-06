package com.paychat.koli.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadIdsTest {

    @Test
    fun `both people compute the same id for the same conversation`() {
        assertEquals(
            ThreadIds.direct("uid_alice", "uid_bob"),
            ThreadIds.direct("uid_bob", "uid_alice"),
        )
    }

    @Test
    fun `a thread with yourself is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            ThreadIds.direct("uid_alice", "uid_alice")
        }
    }

    @Test
    fun `local threads are recognisable and direct ones are not`() {
        val local = ThreadIds.local("uid_alice", "contact_1")
        assertTrue(ThreadIds.isLocal(local))
        assertFalse(ThreadIds.isLocal(ThreadIds.direct("uid_alice", "uid_bob")))
    }

    @Test
    fun `local threads for different contacts differ`() {
        val first = ThreadIds.local("uid_alice", "contact_1")
        val second = ThreadIds.local("uid_alice", "contact_2")
        assertTrue(first != second)
    }
}
