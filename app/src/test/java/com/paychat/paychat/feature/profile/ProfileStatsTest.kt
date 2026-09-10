package com.paychat.paychat.feature.profile

import com.paychat.paychat.data.local.entity.ThreadBalanceEntity
import com.paychat.paychat.data.local.entity.ThreadEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileStatsTest {

    private fun thread(threadId: String) = ThreadEntity(
        threadId = threadId,
        peerUid = "uid-$threadId",
        peerPhone = "+88017000000",
        peerName = "Peer $threadId",
        isLocal = false,
    )

    private fun balance(threadId: String, amountMinor: Long) =
        ThreadBalanceEntity(threadId = threadId, amountMinor = amountMinor, updatedAt = 0)

    @Test
    fun `a thread with no balance row is not settled, only empty`() {
        val stats = ProfileStats.from(
            threads = listOf(thread("a")),
            balances = emptyList(),
        )

        assertEquals(1, stats.conversations)
        assertEquals(0, stats.settled)
        assertEquals(0L, stats.net.minor)
    }

    @Test
    fun `a balance row of zero counts as settled`() {
        val stats = ProfileStats.from(
            threads = listOf(thread("a")),
            balances = listOf(balance("a", 0)),
        )

        assertEquals(1, stats.conversations)
        assertEquals(1, stats.settled)
    }

    @Test
    fun `an outstanding balance is not settled`() {
        val stats = ProfileStats.from(
            threads = listOf(thread("a")),
            balances = listOf(balance("a", 240000)),
        )

        assertEquals(0, stats.settled)
        assertEquals(240000L, stats.net.minor)
    }

    @Test
    fun `a balance whose thread is gone is left out of the net`() {
        val stats = ProfileStats.from(
            threads = listOf(thread("a")),
            balances = listOf(balance("a", 100000), balance("ghost", 999999)),
        )

        assertEquals(100000L, stats.net.minor)
        assertEquals(1, stats.conversations)
    }

    @Test
    fun `a balance whose thread is gone cannot be counted as settled either`() {
        val stats = ProfileStats.from(
            threads = listOf(thread("a")),
            balances = listOf(balance("a", 5000), balance("ghost", 0)),
        )

        assertEquals(0, stats.settled)
    }

    @Test
    fun `what is owed nets off against what is owing`() {
        val stats = ProfileStats.from(
            threads = listOf(thread("a"), thread("b")),
            balances = listOf(balance("a", 300000), balance("b", -100000)),
        )

        assertEquals(200000L, stats.net.minor)
        assertEquals(2, stats.conversations)
        assertEquals(0, stats.settled)
    }

    @Test
    fun `an account with nothing in it reports zeroes`() {
        val stats = ProfileStats.from(threads = emptyList(), balances = emptyList())

        assertEquals(ProfileStats.EMPTY, stats)
    }
}
