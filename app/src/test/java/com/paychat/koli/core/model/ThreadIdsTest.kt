package com.paychat.koli.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// Shaped like real Firebase uids: 28 alphanumeric characters.
private const val ALICE = "AaBbCcDdEeFfGgHhIiJjKkLlMmNn"
private const val BOB = "ZzYyXxWwVvUuTtSsRrQqPpOoNnMm"

class ThreadIdsTest {

    @Test
    fun `both people compute the same id for the same conversation`() {
        assertEquals(ThreadIds.direct(ALICE, BOB), ThreadIds.direct(BOB, ALICE))
    }

    @Test
    fun `a thread with yourself is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            ThreadIds.direct(ALICE, ALICE)
        }
    }

    @Test
    fun `local threads are recognisable and direct ones are not`() {
        val local = ThreadIds.local(ALICE, "8f14e45f-ceea-467a-9575-1c1f5d6a0b21")
        assertTrue(ThreadIds.isLocal(local))
        assertFalse(ThreadIds.isLocal(ThreadIds.direct(ALICE, BOB)))
    }

    @Test
    fun `local threads for different contacts differ`() {
        val first = ThreadIds.local(ALICE, "contact-1")
        val second = ThreadIds.local(ALICE, "contact-2")
        assertTrue(first != second)
    }

    @Test
    fun `a part containing the separator is rejected rather than silently mangled`() {
        // "uid_alice" plus "local" would otherwise parse back as a direct
        // thread, or worse, as a local one belonging to someone else.
        assertThrows(IllegalArgumentException::class.java) {
            ThreadIds.direct("uid_alice", BOB)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ThreadIds.local(ALICE, "contact_1")
        }
    }

    @Test
    fun `an empty part is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ThreadIds.local(ALICE, "")
        }
    }
}
