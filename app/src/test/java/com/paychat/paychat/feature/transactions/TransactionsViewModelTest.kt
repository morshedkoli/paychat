package com.paychat.paychat.feature.transactions

import app.cash.turbine.test
import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.data.chat.ThreadsRepository
import com.paychat.paychat.data.local.entity.ThreadBalanceEntity
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.data.transactions.TransactionQuery
import com.paychat.paychat.data.transactions.TransactionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TransactionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val query: TransactionQuery = mockk()
    private val transactions: TransactionRepository = mockk()
    private val threads: ThreadsRepository = mockk()
    private val preferences: com.paychat.paychat.data.settings.AppPreferences = mockk(relaxed = true)

    private val me = "uid-me"

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { transactions.syncPendingTransactions() } returns flowOf()
    }
    @After fun tearDown() = Dispatchers.resetMain()

    private fun txn(id: String, direction: TxnDirection, amountMinor: Long = 1000) =
        TransactionEntity(
            txnId = id,
            threadId = "thread-1",
            createdBy = me,
            direction = direction,
            amountMinor = amountMinor,
            status = TxnStatus.ACCEPTED,
            createdAt = 1_700_000_000_000,
        )

    private fun viewModel(): TransactionsViewModel {
        every { transactions.viewerUid() } returns me
        every { query.observeRecent(any()) } returns flowOf(
            listOf(
                txn("gave", TxnDirection.SENT, 240000),
                txn("got", TxnDirection.RECEIVED, 85000),
            )
        )
        every { threads.observeThreads() } returns flowOf(
            listOf(
                ThreadEntity(
                    threadId = "thread-1",
                    peerUid = "uid-them",
                    peerName = "Rakib",
                    peerPhone = "+8801712345678",
                    isLocal = false,
                    lastMessageAt = 0,
                )
            )
        )
        every { transactions.observeBalances() } returns flowOf(
            listOf(ThreadBalanceEntity(threadId = "thread-1", amountMinor = 155000, updatedAt = 0))
        )
        return TransactionsViewModel(query, transactions, threads, preferences)
    }

    @Test
    fun `the feed starts with every row grouped by day`() = runTest {
        viewModel().state.test {
            val loaded = awaitItem().let { if (it.loading) awaitItem() else it }
            assertEquals(1, loaded.days.size)
            assertEquals(2, loaded.days.single().rows.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the hero counts one person per non-zero balance`() = runTest {
        viewModel().state.test {
            val loaded = awaitItem().let { if (it.loading) awaitItem() else it }
            assertEquals(1, loaded.people)
            assertEquals(155000, loaded.summary.net.minor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching the filter narrows the feed`() = runTest {
        val model = viewModel()
        model.state.test {
            awaitItem().let { if (it.loading) awaitItem() else it }
            model.setFilter(FeedFilter.YOU_GAVE)
            val filtered = awaitItem()
            assertEquals(FeedFilter.YOU_GAVE, filtered.filter)
            assertEquals(listOf("gave"), filtered.days.flatMap { day -> day.rows.map { it.txnId } })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loading more asks for a bigger page while the table is still full`() = runTest {
        // A full first page means there may well be more behind it.
        val fullPage = List(100) { txn("t$it", TxnDirection.SENT) }
        every { transactions.viewerUid() } returns me
        every { query.observeRecent(any()) } returns flowOf(fullPage)
        every { threads.observeThreads() } returns flowOf(emptyList())
        every { transactions.observeBalances() } returns flowOf(emptyList())
        val model = TransactionsViewModel(query, transactions, threads, preferences)

        model.state.test {
            awaitItem().let { if (it.loading) awaitItem() else it }
            model.loadMore()
            // StandardTestDispatcher queues the re-subscription rather than
            // running it eagerly, so drain it before asserting on the query
            // it caused. The resulting state is equal to the prior one (same
            // rows), and StateFlow only emits distinct values, so there is
            // no further item to await here.
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verify { query.observeRecent(200) }
    }

    @Test
    fun `loading more stops once the table returns fewer rows than asked for`() = runTest {
        // The stub hands back two rows against a page of a hundred, so the
        // table is exhausted and no wider read is justified however often the
        // trailing item asks — which a narrow filter makes it do.
        val model = viewModel()
        model.state.test {
            awaitItem().let { if (it.loading) awaitItem() else it }
            model.loadMore()
            model.loadMore()
            model.loadMore()
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        verify(exactly = 1) { query.observeRecent(100) }
        verify(exactly = 0) { query.observeRecent(200) }
    }

    @Test
    fun `a balance whose thread has not arrived is left out of the hero`() = runTest {
        every { transactions.viewerUid() } returns me
        every { query.observeRecent(any()) } returns flowOf(emptyList())
        every { threads.observeThreads() } returns flowOf(emptyList())
        every { transactions.observeBalances() } returns flowOf(
            listOf(ThreadBalanceEntity(threadId = "thread-1", amountMinor = 155000, updatedAt = 0))
        )

        TransactionsViewModel(query, transactions, threads, preferences).state.test {
            val loaded = awaitItem().let { if (it.loading) awaitItem() else it }
            assertEquals(0, loaded.people)
            assertEquals(0, loaded.summary.net.minor)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unconfirmed row shows in the list without moving the hero`() = runTest {
        every { transactions.viewerUid() } returns me
        every { query.observeRecent(any()) } returns flowOf(
            listOf(
                txn("gave", TxnDirection.SENT, 240000),
                txn("inherited", TxnDirection.SENT, 500000)
                    .copy(createdBy = "uid-them", unconfirmed = true),
            )
        )
        every { threads.observeThreads() } returns flowOf(
            listOf(
                ThreadEntity(
                    threadId = "thread-1",
                    peerUid = "uid-them",
                    peerName = "Rakib",
                    peerPhone = "+8801712345678",
                    isLocal = false,
                    lastMessageAt = 0,
                )
            )
        )
        every { transactions.observeBalances() } returns flowOf(
            listOf(ThreadBalanceEntity(threadId = "thread-1", amountMinor = 240000, updatedAt = 0))
        )

        TransactionsViewModel(query, transactions, threads, preferences).state.test {
            val loaded = awaitItem().let { if (it.loading) awaitItem() else it }
            val rows = loaded.days.flatMap { it.rows }
            assertEquals(listOf("gave", "inherited"), rows.map { it.txnId }.sorted())
            assertEquals(true, rows.single { it.txnId == "inherited" }.unconfirmed)
            // The hero reads the balance cache, which does not count the
            // unreviewed row, so it stays where it was.
            assertEquals(240000, loaded.summary.net.minor)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
