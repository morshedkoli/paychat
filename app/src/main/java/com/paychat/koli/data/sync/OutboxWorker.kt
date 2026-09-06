package com.paychat.koli.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.paychat.koli.core.model.SyncState
import com.paychat.koli.data.chat.ChatRepository
import com.paychat.koli.data.local.dao.MessageDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Uploads everything the user created while the app could not reach the
 * server.
 *
 * Each message carries a client-generated id and is written with `set`, so
 * retrying an upload that actually succeeded overwrites the same document
 * instead of producing a duplicate.
 */
@HiltWorker
class OutboxWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val messageDao: MessageDao,
    private val chatRepository: ChatRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = messageDao.awaitingSync(listOf(SyncState.PENDING, SyncState.FAILED))
        if (pending.isEmpty()) return Result.success()

        var anyFailed = false
        for (message in pending) {
            messageDao.setSyncState(message.messageId, SyncState.UPLOADING)
            runCatching { chatRepository.upload(message) }.fold(
                onSuccess = { messageDao.setSyncState(message.messageId, SyncState.SYNCED) },
                onFailure = {
                    messageDao.setSyncState(message.messageId, SyncState.FAILED)
                    anyFailed = true
                },
            )
        }

        // Retrying is WorkManager's job; it applies the backoff configured by
        // the scheduler rather than us looping here.
        return if (anyFailed) Result.retry() else Result.success()
    }
}
