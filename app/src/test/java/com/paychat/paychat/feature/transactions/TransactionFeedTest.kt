package com.paychat.paychat.feature.transactions

import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.core.money.Money
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TransactionFeedTest {

    private val me = "uid-me"
    private val them = "uid-them"

    private fun txn(
        id: String,
        createdBy: String,
        direction: TxnDirection,
        amountMinor: Long = 1000,
        status: TxnStatus = TxnStatus.ACCEPTED,
        unconfirmed: Boolean = false,
        createdAt: Long = 1_700_000_000_000,
        threadId: String = "thread-1",
        note: String? = null,
    ) = TransactionEntity(
        txnId = id,
        threadId = threadId,
        createdBy = createdBy,
        direction = direction,
        amountMinor = amountMinor,
        note = note,
        status = status,
        unconfirmed = unconfirmed,
        createdAt = createdAt,
    )

    private fun thread(
        threadId: String = "thread-1",
        peerName: String = "Rakib",
        peerPhone: String = "+8801712345678",
        isLocal: Boolean = false,
    ) = ThreadEntity(
        threadId = threadId,
        peerUid = them,
        peerName = peerName,
        peerPhone = peerPhone,
        isLocal = isLocal,
        lastMessageAt = 0,
    )

    @Test
    fun `the author of a sent transaction is the payer`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(txn("t1", createdBy = me, direction = TxnDirection.SENT)),
            threads = listOf(thread()),
            viewerUid = me,
        )

        assertEquals(1, rows.size)
        assertTrue(rows.single().viewerIsPayer)
    }

    @Test
    fun `the same transaction reads the other way for the counterparty`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(txn("t1", createdBy = them, direction = TxnDirection.SENT)),
            threads = listOf(thread()),
            viewerUid = me,
        )

        assertEquals(false, rows.single().viewerIsPayer)
    }

    @Test
    fun `a thread with no name falls back to the phone number`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(txn("t1", createdBy = me, direction = TxnDirection.SENT)),
            threads = listOf(thread(peerName = "", peerPhone = "+8801712345678")),
            viewerUid = me,
        )

        assertEquals("+8801712345678", rows.single().peerName)
    }

    @Test
    fun `a transaction whose thread is missing is listed under a placeholder`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(txn("t1", createdBy = me, direction = TxnDirection.SENT)),
            threads = emptyList(),
            viewerUid = me,
        )

        assertEquals(1, rows.size)
        assertEquals(TransactionFeed.UNKNOWN_PEER, rows.single().peerName)
    }

    @Test
    fun `you gave keeps only the rows the viewer paid`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(
                txn("gave", createdBy = me, direction = TxnDirection.SENT),
                txn("got", createdBy = me, direction = TxnDirection.RECEIVED),
            ),
            threads = listOf(thread()),
            viewerUid = me,
        )

        assertEquals(listOf("gave"), TransactionFeed.filter(rows, FeedFilter.YOU_GAVE).map { it.txnId })
        assertEquals(listOf("got"), TransactionFeed.filter(rows, FeedFilter.YOU_GOT).map { it.txnId })
        assertEquals(2, TransactionFeed.filter(rows, FeedFilter.ALL).size)
    }

    @Test
    fun `an unconfirmed row written by someone else is flagged`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(
                txn("t1", createdBy = them, direction = TxnDirection.SENT, unconfirmed = true)
            ),
            threads = listOf(thread()),
            viewerUid = me,
        )

        assertTrue(rows.single().unconfirmed)
    }

    @Test
    fun `a pending transaction is flagged so it can be muted`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(
                txn("t1", createdBy = me, direction = TxnDirection.SENT, status = TxnStatus.PENDING)
            ),
            threads = listOf(thread()),
            viewerUid = me,
        )

        assertTrue(rows.single().pending)
    }

    @Test
    fun `rows either side of midnight land in different days`() {
        val lateYesterday = Calendar.getInstance().apply {
            set(2024, Calendar.MARCH, 10, 23, 50, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val earlyToday = Calendar.getInstance().apply {
            set(2024, Calendar.MARCH, 11, 0, 10, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = Calendar.getInstance().apply {
            set(2024, Calendar.MARCH, 11, 12, 0, 0)
        }.timeInMillis

        val rows = TransactionFeed.rows(
            transactions = listOf(
                txn("older", createdBy = me, direction = TxnDirection.SENT, createdAt = lateYesterday),
                txn("newer", createdBy = me, direction = TxnDirection.SENT, createdAt = earlyToday),
            ),
            threads = listOf(thread()),
            viewerUid = me,
        )

        val days = TransactionFeed.group(rows, now = now)

        assertEquals(2, days.size)
        assertEquals(listOf("newer"), days.first().rows.map { it.txnId })
        assertEquals(listOf("older"), days.last().rows.map { it.txnId })
    }

    @Test
    fun `rows are newest first within a day`() {
        val rows = TransactionFeed.rows(
            transactions = listOf(
                txn("older", createdBy = me, direction = TxnDirection.SENT, createdAt = 1_700_000_000_000),
                txn("newer", createdBy = me, direction = TxnDirection.SENT, createdAt = 1_700_000_060_000),
            ),
            threads = listOf(thread()),
            viewerUid = me,
        )

        val days = TransactionFeed.group(rows, now = 1_700_000_100_000)

        assertEquals(listOf("newer", "older"), days.single().rows.map { it.txnId })
    }
}
