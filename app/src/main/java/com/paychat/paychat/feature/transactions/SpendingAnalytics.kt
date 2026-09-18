package com.paychat.paychat.feature.transactions

import com.paychat.paychat.core.model.TransactionNote
import com.paychat.paychat.core.model.TxnCategory
import com.paychat.paychat.core.money.Money

data class CategorySpending(
    val category: TxnCategory,
    val totalAmount: Money,
    val count: Int,
    val percentage: Float,
)

data class SpendingAnalytics(
    val totalGiven: Money = Money.ZERO,
    val totalReceived: Money = Money.ZERO,
    val categories: List<CategorySpending> = emptyList(),
) {
    val isEmpty: Boolean get() = categories.isEmpty() && totalGiven.isZero && totalReceived.isZero
    val topCategory: TxnCategory? get() = categories.firstOrNull()?.category

    companion object {
        val EMPTY = SpendingAnalytics()

        fun compute(rows: List<FeedRow>): SpendingAnalytics {
            if (rows.isEmpty()) return EMPTY

            var givenMinor = 0L
            var receivedMinor = 0L
            val categoryAmounts = mutableMapOf<TxnCategory, Long>()
            val categoryCounts = mutableMapOf<TxnCategory, Int>()

            for (row in rows) {
                val parsedNote = TransactionNote.parse(row.note)
                val cat = parsedNote.category
                val absMinor = row.amount.minor

                if (row.viewerIsPayer) {
                    givenMinor += absMinor
                } else {
                    receivedMinor += absMinor
                }

                categoryAmounts[cat] = (categoryAmounts[cat] ?: 0L) + absMinor
                categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1
            }

            val totalVolumeMinor = givenMinor + receivedMinor
            val categories = categoryAmounts.map { (cat, minor) ->
                val pct = if (totalVolumeMinor > 0) (minor.toFloat() / totalVolumeMinor) else 0f
                CategorySpending(
                    category = cat,
                    totalAmount = Money(minor),
                    count = categoryCounts[cat] ?: 1,
                    percentage = pct,
                )
            }.sortedByDescending { it.totalAmount.minor }

            return SpendingAnalytics(
                totalGiven = Money(givenMinor),
                totalReceived = Money(receivedMinor),
                categories = categories,
            )
        }
    }
}
