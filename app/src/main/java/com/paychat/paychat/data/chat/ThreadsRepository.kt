package com.paychat.paychat.data.chat

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.dao.MessageDao
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.local.dao.ThreadCount
import com.paychat.paychat.data.local.dao.UserDao
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.local.entity.UserEntity
import com.paychat.paychat.data.remote.Collections
import com.paychat.paychat.data.remote.LastMessageFields
import com.paychat.paychat.data.remote.LocalContactFields
import com.paychat.paychat.data.remote.ThreadFields
import com.paychat.paychat.data.remote.UserFields
import com.paychat.paychat.data.remote.getLongOrTimestamp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The conversation list.
 */
@Singleton
class ThreadsRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val threadDao: ThreadDao,
    private val messageDao: MessageDao,
    private val userDao: UserDao,
    private val auth: AuthRepository,
) {

    fun observeThreads(): Flow<List<ThreadEntity>> = threadDao.observeAll()

    fun observeUnreadCounts(): Flow<List<ThreadCount>> {
        val uid = auth.currentUid ?: return flowOf(emptyList())
        return messageDao.observeUnreadCounts(uid)
    }

    /**
     * Watches every conversation the user belongs to.
     *
     * The listener only reports the thread documents; the peer's name and
     * picture come from the cached profile, and are fetched once when a thread
     * appears for someone not seen before.
     */
    fun syncThreads(): Flow<List<ThreadEntity>> = callbackFlow {
        val viewerUid = auth.currentUid
        if (viewerUid == null) {
            close()
            return@callbackFlow
        }

        val registration = firestore.collection(Collections.THREADS)
            .whereArrayContains(ThreadFields.MEMBERS, viewerUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                trySend(snapshot.documents.mapNotNull { it.toThreadEntity(viewerUid) })
            }

        awaitClose { registration.remove() }
    }

    /**
     * Stores the threads, filling in any peer names that are not cached yet.
     *
     * The existing row's balance is kept: it is derived from transactions and
     * a thread document knows nothing about it.
     *
     * The snapshot carries every conversation, so the rows already stored are
     * read once for the whole batch and each unknown peer is looked up once,
     * rather than a database read and a possible network round trip per
     * conversation on every snapshot the listener delivers.
     */
    suspend fun persist(threads: List<ThreadEntity>) {
        if (threads.isEmpty()) return

        val stored = threadDao.all().associateBy { it.threadId }
        val profiles = profilesFor(threads.mapNotNull { it.peerUid }.distinct())

        // Only what actually moved. The listener re-delivers a conversation
        // whenever any field on it changes — a typing claim renewed every few
        // seconds, most of all — and writing a row back unchanged would still
        // invalidate the query behind the conversation list and redraw it.
        val changed = threads.mapNotNull { thread ->
            val existing = stored[thread.threadId]
            val profile = thread.peerUid?.let { profiles[it] }

            val row = thread.copy(
                peerName = profile?.name?.ifEmpty { null }
                    ?: thread.peerName.ifEmpty { existing?.peerName.orEmpty() },
                peerPhone = profile?.phone?.ifEmpty { null }
                    ?: thread.peerPhone.ifEmpty { existing?.peerPhone.orEmpty() },
                peerPhotoUrl = profile?.photoUrl ?: existing?.peerPhotoUrl,
            )
            row.takeIf { it != existing }
        }
        if (changed.isNotEmpty()) threadDao.upsertAll(changed)
    }

    /**
     * The cached profiles, fetching and storing any person the app has not
     * seen before. Without this a conversation the other person started would
     * show an empty name.
     */
    private suspend fun profilesFor(uids: List<String>): Map<String, UserEntity> {
        if (uids.isEmpty()) return emptyMap()

        val cached = uids.mapNotNull { uid -> userDao.byUid(uid) }.associateBy { it.uid }
        val missing = uids.filterNot { it in cached }
        if (missing.isEmpty()) return cached

        val fetched = missing.mapNotNull { uid ->
            val document = runCatching {
                firestore.collection(Collections.USERS).document(uid).get().await()
            }.getOrNull() ?: return@mapNotNull null
            if (!document.exists()) return@mapNotNull null

            UserEntity(
                uid = uid,
                phone = document.getString(UserFields.PHONE).orEmpty(),
                name = document.getString(UserFields.NAME).orEmpty(),
                photoUrl = document.getString(UserFields.PHOTO_URL),
                updatedAt = document.getLongOrTimestamp(UserFields.UPDATED_AT) ?: 0L,
            )
        }
        if (fetched.isNotEmpty()) userDao.upsertAll(fetched)

        return cached + fetched.associateBy { it.uid }
    }
}

private fun DocumentSnapshot.toThreadEntity(viewerUid: String): ThreadEntity? {
    val members = (get(ThreadFields.MEMBERS) as? List<*>)?.filterIsInstance<String>().orEmpty()
    if (viewerUid !in members) return null

    val isLocal = getBoolean(ThreadFields.IS_LOCAL) ?: false
    val localContact = get(ThreadFields.LOCAL_CONTACT) as? Map<*, *>
    val lastMessage = get(ThreadFields.LAST_MESSAGE) as? Map<*, *>
    val blockedBy = (get(ThreadFields.BLOCKED_BY) as? List<*>)?.filterIsInstance<String>().orEmpty()
    val departed = (get(ThreadFields.DEPARTED) as? List<*>)?.filterIsInstance<String>().orEmpty()

    return ThreadEntity(
        threadId = id,
        peerUid = if (isLocal) null else members.firstOrNull { it != viewerUid },
        peerPhone = localContact?.get(LocalContactFields.PHONE) as? String ?: "",
        peerName = localContact?.get(LocalContactFields.NAME) as? String ?: "",
        isLocal = isLocal,
        blockedByMe = viewerUid in blockedBy,
        blockedByPeer = blockedBy.any { it != viewerUid },
        peerDeparted = departed.any { it != viewerUid },
        lastMessageText = lastMessage?.get(LastMessageFields.TEXT) as? String,
        lastMessageAt = (lastMessage?.get(LastMessageFields.AT) as? Number)?.toLong() ?: 0L,
        updatedAt = (get(ThreadFields.UPDATED_AT) as? Number)?.toLong() ?: 0L,
    )
}
