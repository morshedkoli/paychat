package com.paychat.paychat.data.transactions

import app.cash.turbine.test
import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.entity.TransactionEntity
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionQueryTest {

    private val dao: TransactionDao = mockk(relaxed = true)

    private fun entity(id: String) = TransactionEntity(
        txnId = id,
        threadId = "thread-1",
        createdBy = "uid-me",
        direction = TxnDirection.SENT,
        amountMinor = 1000,
        status = TxnStatus.ACCEPTED,
        createdAt = 1_700_000_000_000,
    )

    @Test
    fun `the recent feed asks the dao for the page it was given`() = runTest {
        every { dao.observeRecent(100) } returns flowOf(listOf(entity("t1")))

        TransactionQuery(dao).observeRecent(100).test {
            assertEquals(listOf("t1"), awaitItem().map { it.txnId })
            awaitComplete()
        }

        verify { dao.observeRecent(100) }
    }
}
