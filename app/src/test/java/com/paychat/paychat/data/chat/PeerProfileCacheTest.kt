package com.paychat.paychat.data.chat

import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.paychat.paychat.data.auth.AuthRepository
import com.paychat.paychat.data.local.dao.MessageDao
import com.paychat.paychat.data.local.dao.ThreadDao
import com.paychat.paychat.data.local.dao.UserDao
import com.paychat.paychat.data.local.entity.ThreadEntity
import com.paychat.paychat.data.local.entity.UserEntity
import com.paychat.paychat.data.remote.Collections
import com.paychat.paychat.data.remote.UserFields
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PeerProfileCacheTest {

    private val firestore: FirebaseFirestore = mockk()
    private val threadDao: ThreadDao = mockk(relaxed = true)
    private val messageDao: MessageDao = mockk(relaxed = true)
    private val userDao: UserDao = mockk(relaxed = true)
    private val auth: AuthRepository = mockk(relaxed = true)

    private val usersCollection: CollectionReference = mockk()

    private lateinit var repository: ThreadsRepository

    @Before
    fun setUp() {
        every { firestore.collection(Collections.USERS) } returns usersCollection
        repository = ThreadsRepository(
            firestore = firestore,
            threadDao = threadDao,
            messageDao = messageDao,
            userDao = userDao,
            auth = auth,
        )
    }

    @Test
    fun `refreshPeer uses fresh Room cache and skips Firestore read`() = runTest {
        val threadId = "thread-123"
        val peerUid = "peer-456"
        val now = System.currentTimeMillis()

        val cachedUser = UserEntity(
            uid = peerUid,
            phone = "+8801700000000",
            name = "Existing Peer",
            photoUrl = "https://example.com/photo.jpg",
            updatedAt = now - 60_000L, // 1 minute ago (well within 5-min threshold)
        )

        val thread = ThreadEntity(
            threadId = threadId,
            peerUid = peerUid,
            peerPhone = "+8801700000000",
            peerName = "Existing Peer",
            peerPhotoUrl = "https://example.com/photo.jpg",
            isLocal = false,
            updatedAt = now,
        )

        coEvery { threadDao.byId(threadId) } returns thread
        coEvery { userDao.byUid(peerUid) } returns cachedUser

        val result = repository.refreshPeer(threadId, force = false)

        assertTrue(result.isSuccess)
        // Firestore users collection should NOT be accessed at all
        coVerify(exactly = 0) { usersCollection.document(any()) }
    }

    @Test
    fun `refreshPeer fetches from Firestore when cache is stale`() = runTest {
        val threadId = "thread-123"
        val peerUid = "peer-456"
        val now = System.currentTimeMillis()

        val staleUser = UserEntity(
            uid = peerUid,
            phone = "+8801700000000",
            name = "Old Peer Name",
            photoUrl = null,
            updatedAt = now - (10 * 60 * 1000L), // 10 minutes ago (stale > 5 min)
        )

        val thread = ThreadEntity(
            threadId = threadId,
            peerUid = peerUid,
            peerPhone = "+8801700000000",
            peerName = "Old Peer Name",
            isLocal = false,
            updatedAt = now,
        )

        val docRef: DocumentReference = mockk()
        val docSnap: DocumentSnapshot = mockk()

        every { usersCollection.document(peerUid) } returns docRef
        every { docRef.get() } returns Tasks.forResult(docSnap)
        every { docSnap.exists() } returns true
        every { docSnap.getString(UserFields.PHONE) } returns "+8801700000000"
        every { docSnap.getString(UserFields.NAME) } returns "New Peer Name"
        every { docSnap.getString(UserFields.PHOTO_URL) } returns "https://example.com/new.jpg"

        coEvery { threadDao.byId(threadId) } returns thread
        coEvery { userDao.byUid(peerUid) } returns staleUser

        val result = repository.refreshPeer(threadId, force = false)

        assertTrue(result.isSuccess)
        // Firestore was contacted to update the profile
        coVerify(exactly = 1) { usersCollection.document(peerUid) }
        coVerify(exactly = 1) {
            userDao.upsert(match {
                it.uid == peerUid && it.name == "New Peer Name" && it.photoUrl == "https://example.com/new.jpg"
            })
        }
    }

    @Test
    fun `persist uses batch userDao byUids to fetch profiles in a single query`() = runTest {
        val peer1 = "peer-1"
        val peer2 = "peer-2"
        val now = System.currentTimeMillis()

        val user1 = UserEntity(uid = peer1, phone = "111", name = "User One", updatedAt = now)
        val user2 = UserEntity(uid = peer2, phone = "222", name = "User Two", updatedAt = now)

        coEvery { userDao.byUids(listOf(peer1, peer2)) } returns listOf(user1, user2)
        coEvery { threadDao.all() } returns emptyList()

        val threads = listOf(
            ThreadEntity(threadId = "t1", peerUid = peer1, peerPhone = "111", peerName = "", isLocal = false),
            ThreadEntity(threadId = "t2", peerUid = peer2, peerPhone = "222", peerName = "", isLocal = false),
        )

        repository.persist(threads)

        // byUids was invoked with both UIDs in a single call
        coVerify(exactly = 1) { userDao.byUids(listOf(peer1, peer2)) }
        // No Firestore remote fetch needed since both are fresh in Room
        coVerify(exactly = 0) { usersCollection.document(any()) }
        // threadDao should be updated with peer names
        coVerify(exactly = 1) {
            threadDao.upsertAll(match { list ->
                list.size == 2 &&
                    list.any { it.peerName == "User One" } &&
                    list.any { it.peerName == "User Two" }
            })
        }
    }
}
