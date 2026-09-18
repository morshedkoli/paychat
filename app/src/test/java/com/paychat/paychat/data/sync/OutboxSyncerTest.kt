package com.paychat.paychat.data.sync

import com.paychat.paychat.core.model.MessageType
import com.paychat.paychat.core.model.SyncState
import com.paychat.paychat.core.model.TxnDirection
import com.paychat.paychat.core.model.TxnStatus
import com.paychat.paychat.data.chat.ChatRepository
import com.paychat.paychat.data.local.dao.MessageDao
import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.entity.MessageEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.data.media.MediaFiles
import com.paychat.paychat.data.media.MediaUploader
import com.paychat.paychat.data.transactions.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.inject.Provider

class OutboxSyncerTest {

    private val messageDao: MessageDao = mockk(relaxed = true)
    private val transactionDao: TransactionDao = mockk(relaxed = true)
    private val chatRepository: ChatRepository = mockk(relaxed = true)
    private val transactionRepository: TransactionRepository = mockk(relaxed = true)
    private val mediaUploader: MediaUploader = mockk(relaxed = true)
    private val mediaFiles: MediaFiles = mockk(relaxed = true)

    private lateinit var syncer: OutboxSyncer

    @Before
    fun setUp() {
        syncer = OutboxSyncer(
            messageDao = messageDao,
            transactionDao = transactionDao,
            chatRepositoryProvider = Provider { chatRepository },
            transactionRepositoryProvider = Provider { transactionRepository },
            mediaUploader = mediaUploader,
            mediaFiles = mediaFiles,
        )
    }

    @Test
    fun `syncAll drains pending messages and marks them synced`() = runTest {
        val message = MessageEntity(
            messageId = "m1",
            threadId = "t1",
            senderId = "u1",
            type = MessageType.TEXT,
            text = "Hello offline",
            createdAt = 1000L,
            syncState = SyncState.PENDING,
        )

        coEvery { transactionDao.awaitingSync(any()) } returns emptyList()
        coEvery { messageDao.awaitingSync(any()) } returns listOf(message)
        coEvery { chatRepository.upload(message) } returns Unit

        val result = syncer.syncAll()

        assertTrue(result)
        coVerify(exactly = 1) { messageDao.setSyncState("m1", SyncState.UPLOADING) }
        coVerify(exactly = 1) { chatRepository.upload(message) }
        coVerify(exactly = 1) { messageDao.setSyncState("m1", SyncState.SYNCED) }
    }

    @Test
    fun `syncAll drains pending transactions and marks them synced`() = runTest {
        val transaction = TransactionEntity(
            txnId = "tx1",
            threadId = "t1",
            createdBy = "u1",
            direction = TxnDirection.SENT,
            amountMinor = 5000L,
            status = TxnStatus.ACCEPTED,
            createdAt = 1000L,
            syncState = SyncState.PENDING,
        )

        coEvery { transactionDao.awaitingSync(any()) } returns listOf(transaction)
        coEvery { messageDao.awaitingSync(any()) } returns emptyList()
        coEvery { transactionRepository.upload(transaction) } returns Unit

        val result = syncer.syncAll()

        assertTrue(result)
        coVerify(exactly = 1) { transactionDao.setSyncState("tx1", SyncState.UPLOADING) }
        coVerify(exactly = 1) { transactionRepository.upload(transaction) }
        coVerify(exactly = 1) { transactionDao.setSyncState("tx1", SyncState.SYNCED) }
    }

    @Test
    fun `syncAll marks message as failed when upload throws`() = runTest {
        val message = MessageEntity(
            messageId = "m2",
            threadId = "t1",
            senderId = "u1",
            type = MessageType.TEXT,
            text = "Failing message",
            createdAt = 1000L,
            syncState = SyncState.PENDING,
        )

        coEvery { transactionDao.awaitingSync(any()) } returns emptyList()
        coEvery { messageDao.awaitingSync(any()) } returns listOf(message)
        coEvery { chatRepository.upload(message) } throws RuntimeException("Network dropped")

        val result = syncer.syncAll()

        assertFalse(result)
        coVerify(exactly = 1) { messageDao.setSyncState("m2", SyncState.UPLOADING) }
        coVerify(exactly = 1) { messageDao.setSyncState("m2", SyncState.FAILED) }
    }

    @Test
    fun `syncAll processes both transactions and messages in order`() = runTest {
        val txn = TransactionEntity(
            txnId = "tx_ordered",
            threadId = "t1",
            createdBy = "u1",
            direction = TxnDirection.RECEIVED,
            amountMinor = 2000L,
            status = TxnStatus.ACCEPTED,
            createdAt = 1000L,
            syncState = SyncState.PENDING,
        )
        val msg = MessageEntity(
            messageId = "msg_ordered",
            threadId = "t1",
            senderId = "u1",
            type = MessageType.TXN,
            txnId = "tx_ordered",
            createdAt = 1000L,
            syncState = SyncState.PENDING,
        )

        coEvery { transactionDao.awaitingSync(any()) } returns listOf(txn)
        coEvery { messageDao.awaitingSync(any()) } returns listOf(msg)
        coEvery { transactionRepository.upload(txn) } returns Unit
        coEvery { chatRepository.upload(msg) } returns Unit

        val result = syncer.syncAll()

        assertTrue(result)
        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            transactionDao.setSyncState("tx_ordered", SyncState.UPLOADING)
            transactionRepository.upload(txn)
            transactionDao.setSyncState("tx_ordered", SyncState.SYNCED)
            messageDao.setSyncState("msg_ordered", SyncState.UPLOADING)
            chatRepository.upload(msg)
            messageDao.setSyncState("msg_ordered", SyncState.SYNCED)
        }
    }
}
