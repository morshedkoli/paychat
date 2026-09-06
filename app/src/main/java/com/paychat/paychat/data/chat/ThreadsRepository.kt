package com.paychat.paychat.data.chat

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.dao.MessageDao
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.local.dao.UnreadCount
import com.paychat.paychat.data.local.dao.UserDao
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.local.entity.UserEntity
import com.paychat.paychat.data.remote.Collections
import com.paychat.paychat.data.remote.LastMessageFields
import com.paychat.paychat.data.remote.LocalContactFields
import com.paychat.paychat.data.remote.ThreadFields
import com.paychat.paychat.data.remote.UserFields
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

    fun observeUnreadCounts(): Flow<List<UnreadCount>> {
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
     */
    suspend fun persist(threads: List<ThreadEntity>) {
        if (threads.isEmpty()) return

        threadDao.upsertAll(
            threads.map { thread ->
                val existing = threadDao.byId(thread.threadId)
                val profile = thread.peerUid?.let { profileFor(it) }

                thread.copy(
                    peerName = profile?.name?.ifEmpty { null }
                        ?: thread.peerName.ifEmpty { existing?.peerName.orEmpty() },
                    peerPhone = profile?.phone?.ifEmpty { null }
                        ?: thread.peerPhone.ifEmpty { existing?.peerPhone.orEmpty() },
                    peerPhotoUrl = profile?.photoUrl ?: existing?.peerPhotoUrl,
                )
            }
        )
    }

    /**
     * The cached profile, fetched once and stored if this is a person the app
     * has not seen before. Without it a conversation the other person started
     * would show an empty name.
     */
    private suspend fun profileFor(uid: String): UserEntity? {
        userDao.byUid(uid)?.let { return it }

        val document = runCatching {
            firestore.collection(Collections.USERS).document(uid).get().await()
        }.getOrNull() ?: return null
        if (!document.exists()) return null

        val user = UserEntity(
            uid = uid,
            phone = document.getString(UserFields.PHONE).orEmpty(),
            name = document.getString(UserFields.NAME).orEmpty(),
            photoUrl = document.getString(UserFields.PHOTO_URL),
            updatedAt = document.getLong(UserFields.UPDATED_AT) ?: 0L,
        )
        userDao.upsert(user)
        return user
    }
}

private fun DocumentSnapshot.toThreadEntity(viewerUid: String): ThreadEntity? {
    val members = (get(ThreadFields.MEMBERS) as? List<*>)?.filterIsInstance<String>().orEmpty()
    if (viewerUid !in members) return null

    val isLocal = getBoolean(ThreadFields.IS_LOCAL) ?: false
    val localContact = get(ThreadFields.LOCAL_CONTACT) as? Map<*, *>
    val lastMessage = get(ThreadFields.LAST_MESSAGE) as? Map<*, *>

    return ThreadEntity(
        threadId = id,
        peerUid = if (isLocal) null else members.firstOrNull { it != viewerUid },
        peerPhone = localContact?.get(LocalContactFields.PHONE) as? String ?: "",
        peerName = localContact?.get(LocalContactFields.NAME) as? String ?: "",
        isLocal = isLocal,
        lastMessageText = lastMessage?.get(LastMessageFields.TEXT) as? String,
        lastMessageAt = (lastMessage?.get(LastMessageFields.AT) as? Number)?.toLong() ?: 0L,
        updatedAt = (get(ThreadFields.UPDATED_AT) as? Number)?.toLong() ?: 0L,
    )
}
