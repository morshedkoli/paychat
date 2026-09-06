package com.paychat.koli.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.paychat.koli.core.model.MessageType
import com.paychat.koli.core.model.SyncState
import com.paychat.koli.data.chat.ChatRepository
import com.paychat.koli.data.local.dao.MessageDao
import com.paychat.koli.data.local.entity.MessageEntity
import com.paychat.koli.data.media.MediaFiles
import com.paychat.koli.data.media.MediaUploader
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

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
    private val mediaUploader: MediaUploader,
    private val mediaFiles: MediaFiles,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending = messageDao.awaitingSync(listOf(SyncState.PENDING, SyncState.FAILED))
        if (pending.isEmpty()) return Result.success()

        var anyFailed = false
        for (message in pending) {
            messageDao.setSyncState(message.messageId, SyncState.UPLOADING)
            runCatching { send(message) }.fold(
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

    /**
     * An attachment is uploaded before the message document, so a message is
     * never visible to the other person pointing at a file that is not there
     * yet. The uploaded URL is stored first, so a retry after the document
     * write failed does not upload the file a second time.
     */
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

        chatRepository.upload(toSend)
    }
}

private fun MessageEntity.needsMediaUpload(): Boolean =
    (type == MessageType.IMAGE || type == MessageType.VOICE) &&
        mediaUrl == null &&
        !localMediaPath.isNullOrEmpty()
