package com.paychat.paychat.feature.transactions

import com.paychat.paychat.core.model.TxnCategory
import com.paychat.paychat.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpendingAnalyticsTest {

    private fun sampleRow(
        id: String,
        amountMinor: Long,
        viewerIsPayer: Boolean,
        note: String?,
    ) = FeedRow(
        txnId = id,
        peerName = "Friend",
        amount = Money(amountMinor),
        viewerIsPayer = viewerIsPayer,
        note = note,
        pending = false,
        unconfirmed = false,
        createdAt = 1000L,
    )

    @Test
    fun `empty rows produce empty analytics`() {
        val result = SpendingAnalytics.compute(emptyList())
        assertTrue(result.isEmpty)
        assertEquals(Money.ZERO, result.totalGiven)
        assertEquals(Money.ZERO, result.totalReceived)
    }

    @Test
    fun `aggregates category spending and cashflow correctly`() {
        val rows = listOf(
            sampleRow("1", 50000, true, "[Food & Dining] Burger"),
            sampleRow("2", 30000, true, "[Food & Dining] Pizza"),
            sampleRow("3", 20000, false, "[Transport] Bus fare"),
            sampleRow("4", 100000, true, "[Rent] Monthly flat rent"),
        )

        val result = SpendingAnalytics.compute(rows)
        assertFalse(result.isEmpty)
        // Given: 500 + 300 + 1000 = 1800 BDT (180000 minor)
        assertEquals(180000L, result.totalGiven.minor)
        // Received: 200 BDT (20000 minor)
        assertEquals(20000L, result.totalReceived.minor)

        assertEquals(TxnCategory.RENT, result.topCategory)
        assertEquals(3, result.categories.size)

        val rentCat = result.categories.first { it.category == TxnCategory.RENT }
        assertEquals(100000L, rentCat.totalAmount.minor)
        assertEquals(1, rentCat.count)

        val foodCat = result.categories.first { it.category == TxnCategory.FOOD }
        assertEquals(80000L, foodCat.totalAmount.minor)
        assertEquals(2, foodCat.count)
    }
}
