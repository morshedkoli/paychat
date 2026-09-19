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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap
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
     * Reads one conversation once, for when there is no listener running.
     *
     * The conversation list holds the listener, and it only exists while that
     * tab is on screen. A push arriving for an app in the background has to
     * bring the row itself or the list would be stale the next time it opened.
     */
    suspend fun refreshThread(threadId: String): Result<Unit> = runCatching {
        val viewerUid = auth.currentUid ?: return@runCatching
        val document = firestore.collection(Collections.THREADS).document(threadId).get().await()
        val thread = document.toThreadEntity(viewerUid) ?: return@runCatching
        persist(listOf(thread))
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

    /** Track recently failed lookups to avoid hammering Firestore on rapid snapshot updates */
    private val failedLookups = ConcurrentHashMap<String, Long>()

    /**
     * The cached profiles, fetching and storing any person the app has not
     * seen before. Without this a conversation the other person started would
     * show an empty name.
     */
    private suspend fun profilesFor(uids: List<String>): Map<String, UserEntity> {
        if (uids.isEmpty()) return emptyMap()

        // Batch query cached users from Room in a single round-trip
        val cached = userDao.byUids(uids).associateBy { it.uid }
        val now = System.currentTimeMillis()

        // A profile is not fetched once and kept forever: the other person
        // changes their picture and their name, and nothing tells this device
        // when they do. A row older than the refresh interval is read again,
        // which is what makes a new picture appear in the conversation list
        // rather than the one cached the day the thread was first seen.
        val stale = cached.values
            .filter { now - it.updatedAt > PROFILE_REFRESH_MS }
            .map { it.uid }
        val missing = uids.filterNot { it in cached }

        // Skip UIDs that failed recently to avoid hammering Firestore during rapid snapshot updates
        val wanted = (missing + stale).distinct().filter { uid ->
            val lastFailed = failedLookups[uid]
            lastFailed == null || (now - lastFailed) > FAILED_LOOKUP_COOLDOWN_MS
        }
        if (wanted.isEmpty()) return cached

        // Fetch uncached / stale profiles in parallel
        val fetched = coroutineScope {
            wanted.map { uid ->
                async {
                    val profile = fetchProfile(uid)
                    if (profile == null) {
                        failedLookups[uid] = now
                    } else {
                        failedLookups.remove(uid)
                    }
                    profile
                }
            }.awaitAll().filterNotNull()
        }
        if (fetched.isNotEmpty()) userDao.upsertAll(fetched)

        return cached + fetched.associateBy { it.uid }
    }

    /**
     * Reads one profile from the server, keeping the local row's own moment
     * rather than the document's: the refresh interval measures when this
     * device last looked, not when the other person last edited.
     */
    private suspend fun fetchProfile(uid: String): UserEntity? {
        val document = runCatching {
            firestore.collection(Collections.USERS).document(uid).get().await()
        }.getOrNull() ?: return null
        if (!document.exists()) return null

        val existing = userDao.byUid(uid)
        val phoneFromServer = document.getString(UserFields.PHONE).orEmpty()
        val phone = phoneFromServer.ifEmpty { existing?.phone.orEmpty() }

        return UserEntity(
            uid = uid,
            phone = phone,
            name = document.getString(UserFields.NAME).orEmpty(),
            photoUrl = document.getString(UserFields.PHOTO_URL),
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Reads the other person's profile, checking the local cache first.
     * Opening a conversation is the moment their picture and name are
     * most looked at, but if already fresh within [PEER_REFRESH_WINDOW_MS],
     * we avoid an unnecessary network read unless [force] is true.
     */
    suspend fun refreshPeer(threadId: String, force: Boolean = false): Result<Unit> = runCatching {
        val peerUid = threadDao.byId(threadId)?.peerUid ?: return@runCatching
        val now = System.currentTimeMillis()
        val cached = userDao.byUid(peerUid)

        if (!force && cached != null && (now - cached.updatedAt) < PEER_REFRESH_WINDOW_MS) {
            // Room cache is still fresh: sync thread fields locally if needed and return
            threadDao.byId(threadId)?.let { thread ->
                val updated = thread.copy(
                    peerName = cached.name.ifEmpty { thread.peerName },
                    peerPhone = cached.phone.ifEmpty { thread.peerPhone },
                    peerPhotoUrl = cached.photoUrl ?: thread.peerPhotoUrl,
                )
                if (updated != thread) threadDao.upsert(updated)
            }
            return@runCatching
        }

        val profile = fetchProfile(peerUid) ?: return@runCatching
        userDao.upsert(profile)

        threadDao.byId(threadId)?.let { thread ->
            val updated = thread.copy(
                peerName = profile.name.ifEmpty { thread.peerName },
                peerPhone = profile.phone.ifEmpty { thread.peerPhone },
                peerPhotoUrl = profile.photoUrl ?: thread.peerPhotoUrl,
            )
            if (updated != thread) threadDao.upsert(updated)
        }
    }

    private companion object {
        /** How long a cached profile is trusted in thread lists before it is read again. */
        const val PROFILE_REFRESH_MS = 15 * 60 * 1000L

        /** Freshness window when opening a conversation before forcing a remote read. */
        const val PEER_REFRESH_WINDOW_MS = 5 * 60 * 1000L

        /** Cooldown before re-attempting a profile lookup that returned null or failed. */
        const val FAILED_LOOKUP_COOLDOWN_MS = 5 * 60 * 1000L
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
