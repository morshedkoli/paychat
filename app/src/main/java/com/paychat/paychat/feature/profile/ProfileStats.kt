package com.paychat.paychat.feature.profile

import com.paychat.paychat.core.ledger.BalanceCalculator
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.local.entity.ThreadBalanceEntity
import com.paychat.paychat.data.local.entity.ThreadEntity

/**
 * What the Profile card says about where this account stands.
 *
 * @param net everything owed to this user less everything they owe
 * @param conversations how many people they have a thread with
 * @param settled how many of those threads have had money in them and now
 *   stand at zero
 */
data class ProfileStats(
    val net: Money,
    val conversations: Int,
    val settled: Int,
) {
    companion object {
        val EMPTY = ProfileStats(Money.ZERO, 0, 0)

        /**
         * Counts from the two tables that already drive the rest of the app.
         *
         * Only threads that still exist are counted, in either figure: a
         * balance row can outlive its conversation, and totalling money the
         * user cannot navigate to would be a number they could never reconcile.
         *
         * A thread is settled only if it HAS a balance row that reads zero.
         * A thread with no row at all has never carried money, which is empty
         * rather than settled, and calling it settled would flatter the count.
         */
        fun from(
            threads: List<ThreadEntity>,
            balances: List<ThreadBalanceEntity>,
        ): ProfileStats {
            if (threads.isEmpty()) return EMPTY

            val balanceByThread = balances.associate { it.threadId to Money(it.amountMinor) }
            val reachable = threads.mapNotNull { balanceByThread[it.threadId] }

            return ProfileStats(
                net = BalanceCalculator.summarise(reachable).net,
                conversations = threads.size,
                settled = reachable.count { it.isZero },
            )
        }
    }
}
