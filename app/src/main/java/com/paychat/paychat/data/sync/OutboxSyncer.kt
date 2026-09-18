package com.paychat.paychat.data.sync

import com.paychat.paychat.core.model.MessageType
import com.paychat.paychat.core.model.SyncState
import com.paychat.paychat.data.chat.ChatRepository
import com.paychat.paychat.data.local.dao.MessageDao
import com.paychat.paychat.data.local.dao.TransactionDao
import com.paychat.paychat.data.local.entity.MessageEntity
import com.paychat.paychat.data.local.entity.TransactionEntity
import com.paychat.paychat.data.media.MediaFiles
import com.paychat.paychat.data.media.MediaUploader
import com.paychat.paychat.data.transactions.TransactionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class OutboxSyncer @Inject constructor(
    private val messageDao: MessageDao,
    private val transactionDao: TransactionDao,
    private val chatRepositoryProvider: Provider<ChatRepository>,
    private val transactionRepositoryProvider: Provider<TransactionRepository>,
    private val mediaUploader: MediaUploader,
    private val mediaFiles: MediaFiles,
) {
    private val syncMutex = Mutex()

    /**
     * Drains the outbox: uploads all pending transactions and messages.
     * Protected by a mutex so concurrent flushes from NetworkCallback and WorkManager
     * don't duplicate work.
     *
     * @return true if all pending items succeeded, false if any failed
     */
    suspend fun syncAll(): Boolean = syncMutex.withLock {
        val waiting = listOf(SyncState.PENDING, SyncState.UPLOADING, SyncState.FAILED)

        var anyFailed = uploadTransactions(transactionDao.awaitingSync(waiting))
        anyFailed = uploadMessages(messageDao.awaitingSync(waiting)) || anyFailed

        !anyFailed
    }

    private suspend fun uploadTransactions(pending: List<TransactionEntity>): Boolean {
        var anyFailed = false
        for (transaction in pending) {
            transactionDao.setSyncState(transaction.txnId, SyncState.UPLOADING)
            val result = runCatching {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    send(transaction)
                }
            }
            result.fold(
                onSuccess = { transactionDao.setSyncState(transaction.txnId, SyncState.SYNCED) },
                onFailure = {
                    transactionDao.setSyncState(transaction.txnId, SyncState.FAILED)
                    anyFailed = true
                },
            )
        }
        return anyFailed
    }

    private suspend fun uploadMessages(pending: List<MessageEntity>): Boolean {
        var anyFailed = false
        for (message in pending) {
            messageDao.setSyncState(message.messageId, SyncState.UPLOADING)
            val result = runCatching {
                withTimeout(NETWORK_TIMEOUT_MS) {
                    send(message)
                }
            }
            result.fold(
                onSuccess = { messageDao.setSyncState(message.messageId, SyncState.SYNCED) },
                onFailure = {
                    messageDao.setSyncState(message.messageId, SyncState.FAILED)
                    anyFailed = true
                },
            )
        }
        return anyFailed
    }

    private suspend fun send(transaction: TransactionEntity) {
        var toSend = transaction

        if (transaction.photoUrl == null && !transaction.localPhotoPath.isNullOrEmpty()) {
            val localPath = transaction.localPhotoPath
            val uploaded = mediaUploader.upload(
                file = File(localPath),
                messageId = transaction.txnId,
                isVoice = false,
            ).getOrThrow()

            transactionDao.setPhoto(transaction.txnId, uploaded.secureUrl, uploaded.publicId)
            toSend = transaction.copy(
                photoUrl = uploaded.secureUrl,
                photoPublicId = uploaded.publicId,
                localPhotoPath = null,
            )
            mediaFiles.discard(localPath)
        }

        transactionRepositoryProvider.get().upload(toSend)
    }

    private suspend fun send(message: MessageEntity) {
        var toSend = message

        if (message.needsMediaUpload()) {
            val localPath = message.localMediaPath ?: error("attachment has no local file")
            val uploaded = mediaUploader.upload(
                file = File(localPath),
                messageId = message.messageId,
                isVoice = message.type == MessageType.VOICE,
            ).getOrThrow()

            messageDao.setMedia(message.messageId, uploaded.secureUrl, uploaded.publicId)
            toSend = message.copy(
                mediaUrl = uploaded.secureUrl,
                mediaPublicId = uploaded.publicId,
                localMediaPath = null,
            )
            mediaFiles.discard(localPath)
        }

        chatRepositoryProvider.get().upload(toSend)
    }

    private companion object {
        const val NETWORK_TIMEOUT_MS = 25_000L
    }
}

private fun MessageEntity.needsMediaUpload(): Boolean =
    (type == MessageType.IMAGE || type == MessageType.VOICE) &&
        mediaUrl == null &&
        !localMediaPath.isNullOrEmpty()
