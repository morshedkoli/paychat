package com.paychat.paychat.data.chat

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.paychat.paychat.core.model.MessageType
import com.paychat.paychat.core.model.SyncState
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.dao.MessageDao
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.local.entity.MessageEntity
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.remote.Collections
import com.paychat.paychat.data.remote.LastMessageFields
import com.paychat.paychat.data.remote.MessageFields
import com.paychat.paychat.data.remote.ThreadFields
import com.paychat.paychat.data.remote.getLongOrTimestamp
import com.paychat.paychat.data.sync.OutboxScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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

    // ------------------------------------------------------------- typing

    /**
     * Says whether the other person is typing right now.
     *
     * The claim is a timestamp rather than a flag, so it expires on its own.
     * A flag would stay set for ever whenever the other device lost its
     * connection mid-word, and there is no reliable moment to clear it from
     * here. The flow re-emits when the claim runs out.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePeerTyping(threadId: String): Flow<Boolean> {
        val viewerUid = auth.currentUid ?: return flowOf(false)

        return callbackFlow {
            val registration = firestore.collection(Collections.THREADS)
                .document(threadId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    val claims = snapshot.get(ThreadFields.TYPING) as? Map<*, *>
                    trySend(
                        claims?.entries
                            ?.filter { it.key != viewerUid }
                            ?.mapNotNull { (it.value as? Number)?.toLong() }
                            ?.maxOrNull()
                            ?: 0L
                    )
                }
            awaitClose { registration.remove() }
        }
            // A claim that has run out is worth nothing, so the flow has to
            // report it expiring even though no snapshot arrives to say so.
            .flatMapLatest { claimedAt ->
                val remaining = TYPING_TTL_MS - (System.currentTimeMillis() - claimedAt)
                if (remaining <= 0) flowOf(false)
                else flow {
                    emit(true)
                    delay(remaining)
                    emit(false)
                }
            }
            .distinctUntilChanged()
    }

    /**
     * Renews, or withdraws, this user's claim to be typing.
     *
     * Rate limited to one write while a claim is still fresh, because the
     * caller is a text field and would otherwise write on every keystroke.
     * Withdrawing is not rate limited: it is what stops the other side seeing
     * "typing" after the message has been sent.
     */
    suspend fun setTyping(threadId: String, typing: Boolean) {
        val viewerUid = auth.currentUid ?: return
        val now = System.currentTimeMillis()

        if (typing) {
            if (now - (lastTypingWriteAt[threadId] ?: 0L) < TYPING_RENEW_MS) return
            lastTypingWriteAt[threadId] = now
        } else {
            if (lastTypingWriteAt.remove(threadId) == null) return
        }

        // Failure is ignored on purpose. A typing indicator that could raise
        // an error banner would be worse than one that quietly does not show.
        runCatching {
            firestore.collection(Collections.THREADS).document(threadId).set(
                mapOf(ThreadFields.TYPING to mapOf(viewerUid to if (typing) now else 0L)),
                SetOptions.merge(),
            ).await()
        }
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

    /** When this user last claimed to be typing, per conversation. */
    private val lastTypingWriteAt = ConcurrentHashMap<String, Long>()

    private companion object {
        const val MESSAGE_PAGE_SIZE = 200L

        /** How long a typing claim counts for before it has to be renewed. */
        const val TYPING_TTL_MS = 6_000L

        /** How often a claim is renewed while the person keeps typing. */
        const val TYPING_RENEW_MS = 3_000L
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
    durationMs = getLongOrTimestamp(MessageFields.DURATION_MS),
    txnId = getString(MessageFields.TXN_ID),
    createdAt = getLongOrTimestamp(MessageFields.CREATED_AT) ?: 0L,
    deliveredTo = (get(MessageFields.DELIVERED_TO) as? List<*>)?.filterIsInstance<String>().orEmpty(),
    readBy = (get(MessageFields.READ_BY) as? List<*>)?.filterIsInstance<String>().orEmpty(),
)
