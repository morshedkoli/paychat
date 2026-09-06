package com.paychat.koli.data.chat

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.paychat.koli.core.model.MessageType
import com.paychat.koli.core.model.SyncState
import com.paychat.koli.data.auth.AuthRepository
import com.paychat.koli.data.local.dao.MessageDao
import com.paychat.koli.data.local.dao.ThreadDao
import com.paychat.koli.data.local.entity.MessageEntity
import com.paychat.koli.data.local.entity.ThreadEntity
import com.paychat.koli.data.remote.Collections
import com.paychat.koli.data.remote.LastMessageFields
import com.paychat.koli.data.remote.MessageFields
import com.paychat.koli.data.remote.ThreadFields
import com.paychat.koli.data.sync.OutboxScheduler
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sending and receiving messages.
 *
 * Room is the source of truth for what the screen shows. A sent message is
 * written locally first and uploaded by the outbox worker, so the chat behaves
 * the same with or without a connection.
 */
@Singleton
class ChatRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val messageDao: MessageDao,
    private val threadDao: ThreadDao,
    private val auth: AuthRepository,
    private val outbox: OutboxScheduler,
) {

    fun observeMessages(threadId: String): Flow<List<MessageEntity>> =
        messageDao.observeThread(threadId)

    fun observeThread(threadId: String): Flow<ThreadEntity?> = threadDao.observe(threadId)

    /**
     * Queues a text message.
     *
     * The id is generated here rather than by Firestore, so that retrying an
     * upload writes the same document instead of a duplicate.
     */
    suspend fun sendText(threadId: String, text: String): Result<Unit> = runCatching {
        val body = text.trim()
        require(body.isNotEmpty()) { "cannot send an empty message" }
        val senderId = auth.currentUid ?: error("not signed in")
        val now = System.currentTimeMillis()

        val message = MessageEntity(
            messageId = UUID.randomUUID().toString(),
            threadId = threadId,
            senderId = senderId,
            type = MessageType.TEXT,
            text = body,
            createdAt = now,
            syncState = SyncState.PENDING,
        )
        messageDao.insert(message)
        threadDao.setLastMessage(threadId, MessageMapper.preview(MessageType.TEXT, body), now)
        outbox.schedule()
    }

    /**
     * Queues an attachment.
     *
     * [localPath] must already point inside the app's own storage. A picked
     * photo is copied there first, because the picker's permission on the
     * original lasts only as long as the activity result, and an outbox entry
     * may outlive that by days.
     */
    suspend fun sendMedia(
        threadId: String,
        messageId: String,
        type: MessageType,
        localPath: String,
        durationMs: Long? = null,
    ): Result<Unit> = runCatching {
        require(type == MessageType.IMAGE || type == MessageType.VOICE) {
            "sendMedia only handles attachments"
        }
        val senderId = auth.currentUid ?: error("not signed in")
        val now = System.currentTimeMillis()

        val message = MessageEntity(
            messageId = messageId,
            threadId = threadId,
            senderId = senderId,
            type = type,
            localMediaPath = localPath,
            durationMs = durationMs,
            createdAt = now,
            syncState = SyncState.PENDING,
        )
        messageDao.insert(message)
        threadDao.setLastMessage(threadId, MessageMapper.preview(type, null), now)
        outbox.schedule()
    }

    /** Uploads one queued message. Called by the outbox worker. */
    suspend fun upload(message: MessageEntity) {
        val document = firestore.collection(Collections.THREADS)
            .document(message.threadId)
            .collection(Collections.MESSAGES)
            .document(message.messageId)

        document.set(
            mapOf(
                MessageFields.SENDER_ID to message.senderId,
                MessageFields.TYPE to message.type.name,
                MessageFields.TEXT to message.text,
                MessageFields.MEDIA_URL to message.mediaUrl,
                MessageFields.MEDIA_PUBLIC_ID to message.mediaPublicId,
                MessageFields.DURATION_MS to message.durationMs,
                MessageFields.TXN_ID to message.txnId,
                MessageFields.CREATED_AT to message.createdAt,
                MessageFields.DELIVERED_TO to listOf(message.senderId),
                MessageFields.READ_BY to listOf(message.senderId),
            )
        ).await()

        firestore.collection(Collections.THREADS).document(message.threadId).set(
            mapOf(
                ThreadFields.LAST_MESSAGE to mapOf(
                    LastMessageFields.TEXT to MessageMapper.preview(message.type, message.text),
                    LastMessageFields.TYPE to message.type.name,
                    LastMessageFields.AT to message.createdAt,
                    LastMessageFields.SENDER_ID to message.senderId,
                ),
                ThreadFields.UPDATED_AT to message.createdAt,
            ),
            SetOptions.merge(),
        ).await()
    }

    /**
     * Watches one conversation and writes what arrives into Room, for as long
     * as it is collected.
     *
     * A snapshot listener cannot suspend, so it emits the mapped rows and the
     * collector, which is a coroutine, performs the database write.
     */
    fun syncMessages(threadId: String): Flow<List<MessageEntity>> = callbackFlow {
        val viewerUid = auth.currentUid
        if (viewerUid == null) {
            close()
            return@callbackFlow
        }

        val registration = firestore.collection(Collections.THREADS)
            .document(threadId)
            .collection(Collections.MESSAGES)
            .orderBy(MessageFields.CREATED_AT, Query.Direction.DESCENDING)
            .limit(MESSAGE_PAGE_SIZE)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(
                    snapshot.documents.map {
                        MessageMapper.toEntity(it.toRemote(threadId), viewerUid)
                    }
                )
            }

        awaitClose { registration.remove() }
    }

    suspend fun persist(messages: List<MessageEntity>) {
        if (messages.isNotEmpty()) messageDao.upsertAll(messages)
    }

    /**
     * Marks every message in the thread that the user has not read yet.
     *
     * Read receipts are the only field a non-sender may change on a message,
     * which the security rules enforce.
     */
    suspend fun markRead(threadId: String) {
        val viewerUid = auth.currentUid ?: return
        val unread = messageDao.unreadFrom(threadId, viewerUid)
        if (unread.isEmpty()) return

        messageDao.markRead(unread.map { it.messageId }, System.currentTimeMillis())

        val collection = firestore.collection(Collections.THREADS)
            .document(threadId)
            .collection(Collections.MESSAGES)
        firestore.runBatch { batch ->
            unread.forEach { message ->
                batch.update(
                    collection.document(message.messageId),
                    mapOf(
                        MessageFields.READ_BY to FieldValue.arrayUnion(viewerUid),
                        MessageFields.DELIVERED_TO to FieldValue.arrayUnion(viewerUid),
                    ),
                )
            }
        }.await()
    }

    private companion object {
        const val MESSAGE_PAGE_SIZE = 200L
    }
}

private fun DocumentSnapshot.toRemote(threadId: String) = MessageMapper.Remote(
    messageId = id,
    threadId = threadId,
    senderId = getString(MessageFields.SENDER_ID).orEmpty(),
    type = getString(MessageFields.TYPE),
    text = getString(MessageFields.TEXT),
    mediaUrl = getString(MessageFields.MEDIA_URL),
    mediaPublicId = getString(MessageFields.MEDIA_PUBLIC_ID),
    durationMs = getLong(MessageFields.DURATION_MS),
    txnId = getString(MessageFields.TXN_ID),
    createdAt = getLong(MessageFields.CREATED_AT) ?: 0L,
    deliveredTo = (get(MessageFields.DELIVERED_TO) as? List<*>)?.filterIsInstance<String>().orEmpty(),
    readBy = (get(MessageFields.READ_BY) as? List<*>)?.filterIsInstance<String>().orEmpty(),
)
